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
          module("intellij.platform.annotations.common")
          module("intellij.platform.annotations.java5")
          module("intellij.platform.util.rt")
          module("intellij.platform.util")
        }

        jar("jps-launcher.jar") {
          module("intellij.platform.jps.build.launcher")
        }

        jar("jps-model.jar") {
          module("intellij.platform.jps.model")
          module("intellij.platform.jps.model.impl")
          module("intellij.platform.jps.model.serialization")
        }
        jar("jps-builders.jar") {
          module("intellij.java.guiForms.rt")
          module("intellij.java.guiForms.compiler")
          module("intellij.java.compiler.instrumentationUtil")
          module("intellij.java.compiler.instrumentationUtil.java8")
          module("intellij.java.jps.javacRefScanner8")
          module("intellij.platform.jps.build")
          module("intellij.tools.jps.build.standalone")
        }
        jar("idea_rt.jar") {
          module("intellij.java.rt")
        }
        jar("jps-builders-6.jar") {
          module("intellij.platform.jps.build.javac.rt")
        }
        //layout of groovy jars must be consistent with GroovyBuilder.getGroovyRtRoots method
        jar("groovy-jps-plugin.jar") {
          module("intellij.groovy.jps")
        }
        jar("groovy_rt.jar") {
          module("intellij.groovy.rt")
        }
        jar("groovy-rt-constants.jar") {
          module("intellij.groovy.constants.rt")
        }
        jar("ui-designer-jps-plugin.jar") { module("intellij.java.guiForms.jps") }


        jar("maven-jps-plugin.jar") { module("intellij.maven.jps") }
        jar("aether-dependency-resolver.jar") { module("intellij.java.aetherDependencyResolver") }
        jar("gradle-jps-plugin.jar") { module("intellij.gradle.jps") }
        moduleLibrary("intellij.maven.jps", "plexus-utils-2.0.6.jar")

        jar("eclipse-jps-plugin.jar") {
          module("intellij.eclipse.common")
          module("intellij.eclipse.jps")
        }
        jar("devkit-jps-plugin.jar") { module("intellij.devkit.jps") }
        jar("intellilang-jps-plugin.jar") { module("intellij.java.langInjection.jps") }

        [
          "JDOM", "jna", "OroMatcher", "Trove4j", "ASM", "NanoXML", "protobuf", "cli-parser", "Log4J", "jgoodies-forms", "Eclipse",
          "Netty", "Snappy-Java", "lz4-java", "commons-codec", "commons-logging", "http-client", "Slf4j", "Guava"
        ].each {
          projectLibrary(it)
        }
        moduleLibrary("intellij.java.aetherDependencyResolver", "aether-1.1.0-all.jar")
        moduleLibrary("intellij.java.aetherDependencyResolver", "maven-aether-provider-3.3.9-all.jar")

        moduleLibrary("intellij.platform.jps.build.javac.rt", "optimizedFileManager.jar")
        jar("ant-jps-plugin.jar") { module("intellij.ant.jps") }
        include(additionalJars)
      }
      jar("jps-build-test-${buildNumber}.jar") {
        moduleTests("intellij.platform.jps.build")
        moduleTests("intellij.platform.jps.model.tests")
        moduleTests("intellij.platform.jps.model.serialization.tests")
      }
    }
    buildContext.notifyArtifactBuilt(targetDir)
  }
}
