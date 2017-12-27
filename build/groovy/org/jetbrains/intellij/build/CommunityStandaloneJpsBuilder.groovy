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
    new LayoutBuilder(buildContext, false).layout(targetDir) {
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
        moduleLibrary("maven-jps-plugin", "plexus-utils-2.0.6.jar")

        jar("eclipse-jps-plugin.jar") {
          module("common-eclipse-util")
          module("eclipse-jps-plugin")
        }
        jar("devkit-jps-plugin.jar") { module("devkit-jps-plugin") }
        jar("intellilang-jps-plugin.jar") { module("intellilang-jps-plugin") }

        [
          "JDOM", "jna", "OroMatcher", "Trove4j", "ASM", "NanoXML", "protobuf", "cli-parser", "Log4J", "jgoodies-forms", "Eclipse",
          "Netty", "Snappy-Java", "lz4-java", "commons-codec", "commons-logging", "http-client", "Slf4j", "Guava"
        ].each {
          projectLibrary(it)
        }
        moduleLibrary("aether-dependency-resolver", "aether-1.1.0-all.jar")
        moduleLibrary("aether-dependency-resolver", "maven-aether-provider-3.3.9-all.jar")

        moduleLibrary("jps-builders-6", "optimizedFileManager.jar")
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
