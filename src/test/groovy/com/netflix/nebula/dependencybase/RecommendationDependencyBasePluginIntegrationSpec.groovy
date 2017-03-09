/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.nebula.dependencybase

import nebula.test.IntegrationTestKitSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator

class RecommendationDependencyBasePluginIntegrationSpec extends IntegrationTestKitSpec {
    def "recommend versions of dependencies"() {
        given:
        setup1Dependency()

        when:
        def results = runTasks("dependencies", "--configuration", "compileClasspath")

        then:
        results.output.contains "test.nebula:foo: -> 1.0.0"
    }

    def "recommend versions of dependencies are explained in dependencyInsightEnhanced"() {
        given:
        setup1Dependency()

        when:
        def results = runTasks("dependencyInsightEnhanced", "--configuration", "compileClasspath", "--dependency", "foo")

        then:
        results.output.contains "test.nebula:foo:1.0.0 (recommend: 1.0.0 via NebulaTest)"
    }

    def "forces respected"() {
        given:
        setup1DependencyForce()

        when:
        def results = runTasks("dependencyInsightEnhanced", "--configuration", "compileClasspath", "--dependency", "foo")

        then:
        results.output.contains "test.nebula:foo:1.0.0 (forced, ignore recommend: 2.0.0 via NebulaTest)"
    }

    def "multiproject sees recommendations"() {
        given:
        setupMultiproject()

        when:
        def onefoo = runTasks(":one:dependencyInsightEnhanced", "--configuration", "compileClasspath", "--dependency", "foo")

        then:
        onefoo.output.contains "test.nebula:foo:1.0.0 (recommend: 1.0.0 via NebulaTest)"

        when:
        def twofoo = runTasks(":two:dependencyInsightEnhanced", "--configuration", "compileClasspath", "--dependency", "foo")

        then:
        twofoo.output.contains "test.nebula:foo:1.0.0 (recommend: 1.0.0 via NebulaTest)"

        when:
        def twobar = runTasks(":two:dependencyInsightEnhanced", "--configuration", "compileClasspath", "--dependency", "bar")

        then:
        twobar.output.contains "test.nebula:bar:2.0.0 (recommend: 2.0.0 via NebulaTest)"
    }

    def setup1Dependency() {
        def graph = new DependencyGraphBuilder().addModule("test.nebula:foo:1.0.0").build()
        def generator = new GradleDependencyGenerator(graph)
        generator.generateTestMavenRepo()

        buildFile << """\
            plugins {
                id "nebula.dependency-base"
                id "java"
            }
            
            repositories {
                ${generator.mavenRepositoryBlock}
            }
            
            project.nebulaDependencyBase.addRecommendation("test.nebula:foo", "1.0.0", "NebulaTest")
            
            dependencies {
                implementation "test.nebula:foo"
            }
            """.stripIndent()
    }

    def setup1DependencyForce() {
        def graph = new DependencyGraphBuilder()
                .addModule("test.nebula:foo:1.0.0")
                .addModule("test.nebula:foo:2.0.0")
                .build()
        def generator = new GradleDependencyGenerator(graph)
        generator.generateTestMavenRepo()

        buildFile << """\
            plugins {
                id "nebula.dependency-base"
                id "java"
            }
            
            repositories {
                ${generator.mavenRepositoryBlock}
            }
            
            project.nebulaDependencyBase.addRecommendation("test.nebula:foo", "2.0.0", "NebulaTest")

            configurations.all {
                resolutionStrategy {
                    force "test.nebula:foo:1.0.0"
                }
            }
            
            dependencies {
                implementation "test.nebula:foo"
            }
            """.stripIndent()
    }

    def setupMultiproject() {
        keepFiles = true
        def graph = new DependencyGraphBuilder()
                .addModule("test.nebula:foo:1.0.0")
                .addModule("test.nebula:bar:2.0.0")
                .build()
        def generator = new GradleDependencyGenerator(graph)
        generator.generateTestMavenRepo()

        buildFile << """\
            plugins {
                id "nebula.dependency-base"
            }
            
            subprojects {
                apply plugin: "nebula.dependency-base"
                apply plugin: "java"
                
                project.nebulaDependencyBase.addRecommendation("test.nebula:foo", "1.0.0", "NebulaTest")
                project.nebulaDependencyBase.addRecommendation("test.nebula:bar", "2.0.0", "NebulaTest")
                
                repositories {
                    ${generator.mavenRepositoryBlock}
                }
            }
            """.stripIndent()

        addSubproject("one", """\
            dependencies {
                compile "test.nebula:foo"
            }
            """.stripIndent())
        addSubproject("two", """\
            dependencies {
                compile project(":one")
                compile "test.nebula:bar"
            }
            """.stripIndent())
    }
}