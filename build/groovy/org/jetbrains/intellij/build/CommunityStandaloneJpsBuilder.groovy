/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.impl.LayoutBuilder

/**
 * Creates JARs containing classes required to run the external build for IDEA project without IDE.
 *
 * @author nik
 */
class CommunityStandaloneJpsBuilder {
  private final BuildContext buildContext

  CommunityStandaloneJpsBuilder(BuildContext buildContext) {
    this.buildContext = buildContext
  }

  void layoutJps(String targetDir, String buildNumber, @DelegatesTo(LayoutBuilder.LayoutSpec) Closure additionalJars) {
    AntBuilder ant = buildContext.ant
    String home = buildContext.paths.communityHome
    new LayoutBuilder(ant, buildContext.project, false).layout(targetDir) {
      zip("standalone-jps-${buildNumber}.zip") {
        jar("util.jar") {
          module("annotations-common")
          module("annotations")
          module("util-rt")
          module("util")
        }

        jar("jps-launcher.jar") {
          module("jps-launcher")
        }

        jar("jps-model.jar") {
          module("jps-model-api")
          module("jps-model-impl")
          module("jps-model-serialization")
        }
        jar("jps-builders.jar") {
          module("forms_rt")
          module("forms-compiler")
          module("instrumentation-util")
          module("instrumentation-util-8")
          module("javac-ref-scanner-8")
          module("jps-builders")
          module("jps-standalone-builder")
        }
        jar("idea_rt.jar") {
          module("java-runtime")
        }
        jar("jps-builders-6.jar") {
          module("jps-builders-6")
        }
        //layout of groovy jars must be consistent with GroovyBuilder.getGroovyRtRoots method
        jar("groovy-jps-plugin.jar") {
          module("groovy-jps-plugin")
        }
        jar("groovy_rt.jar") {
          module("groovy_rt")
        }
        jar("groovy-rt-constants.jar") {
          module("groovy-rt-constants")
        }
        jar("ui-designer-jps-plugin.jar") { module("ui-designer-jps-plugin") }


        jar("maven-jps-plugin.jar") { module("maven-jps-plugin") }
        jar("aether-dependency-resolver.jar") { module("aether-dependency-resolver") }
        jar("gradle-jps-plugin.jar") { module("gradle-jps-plugin") }
        ant.fileset(dir: "$home/plugins/maven/maven30-server-impl/lib/maven3/lib") { include(name: "plexus-utils-*.jar") }

        jar("eclipse-jps-plugin.jar") {
          module("common-eclipse-util")
          module("eclipse-jps-plugin")
        }
        jar("devkit-jps-plugin.jar") { module("devkit-jps-plugin") }
        jar("intellilang-jps-plugin.jar") { module("intellilang-jps-plugin") }
        ant.fileset(dir: "$home/lib") {
          include(name: "jdom.jar")
          include(name: "jna.jar")
          include(name: "jna-platform.jar")
          include(name: "oromatcher.jar")
          include(name: "trove4j.jar")
          include(name: "asm-all.jar")
          include(name: "nanoxml-*.jar")
          include(name: "protobuf-*.jar")
          include(name: "cli-parser-*.jar")
          include(name: "log4j.jar")
          include(name: "jgoodies-forms.jar")
          include(name: "ecj*.jar")
          include(name: "netty-all-*.jar")
          include(name: "snappy-in-java-*.jar")
          include(name: "aether-*.jar")
          include(name: "maven-aether-provider-*.jar")
          include(name: "commons-codec-*.jar")
          include(name: "commons-logging-*.jar")
          include(name: "httpclient-*.jar")
          include(name: "httpcore-*.jar")
          include(name: "slf4j-api-*.jar")
        }
        ant.fileset(dir: "$home/jps/lib") {
          include(name: "optimizedFileManager.jar")
        }
        jar("ant-jps-plugin.jar") { module("ant-jps-plugin") }
        include(additionalJars)
      }
      jar("jps-build-test-${buildNumber}.jar") {
        moduleTests("jps-builders")
        moduleTests("jps-model-tests")
        moduleTests("jps-serialization-tests")
      }
    }
    buildContext.notifyArtifactBuilt(targetDir)
  }
}
