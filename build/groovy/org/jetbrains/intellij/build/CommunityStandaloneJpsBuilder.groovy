// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.impl.LayoutBuilder
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectStructureMapping

/**
 * Creates JARs containing classes required to run the external build for IDEA project without IDE.
 */
class CommunityStandaloneJpsBuilder {
  private final BuildContext buildContext

  CommunityStandaloneJpsBuilder(BuildContext buildContext) {
    this.buildContext = buildContext
  }

  void processJpsLayout(String targetDir, String buildNumber, ProjectStructureMapping projectStructureMapping,
                        boolean copyFiles, @DelegatesTo(LayoutBuilder.LayoutSpec) Closure additionalJars) {
    def context = buildContext
    new LayoutBuilder(buildContext, false).process(targetDir, projectStructureMapping, copyFiles) {
      zip(getZipName(buildNumber)) {
        jar("util.jar") {
          module("intellij.platform.util.rt")
          module("intellij.platform.util")
          module("intellij.platform.util.classLoader")
          module("intellij.platform.util.text.matching")
          module("intellij.platform.util.collections")
          module("intellij.platform.util.strings")
          module("intellij.platform.util.diagnostic")
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
        jar("groovy-jps.jar") { module("intellij.groovy.jps") }
        jar("groovy-rt.jar") { module("intellij.groovy.rt") }
        jar("groovy-constants-rt.jar") { module("intellij.groovy.constants.rt") }
        jar("java-guiForms-jps.jar") { module("intellij.java.guiForms.jps") }


        jar("maven-jps.jar") { module("intellij.maven.jps") }
        jar("aether-dependency-resolver.jar") { module("intellij.java.aetherDependencyResolver") }
        jar("gradle-jps.jar") { module("intellij.gradle.jps") }

        jar("eclipse-jps.jar") { module("intellij.eclipse.jps") }
        jar("eclipse-common.jar") { module("intellij.eclipse.common") }
        jar("devkit-jps.jar") { module("intellij.devkit.jps") }
        jar("java-langInjection-jps.jar") { module("intellij.java.langInjection.jps") }

        [
          "JDOM", "jna", "OroMatcher", "Trove4j", "ASM", "NanoXML", "protobuf", "cli-parser", "Log4J", "jgoodies-forms", "Eclipse",
          "netty-codec-http", "lz4-java", "commons-codec", "commons-logging", "http-client", "Slf4j", "Guava", "plexus-utils",
          "jetbrains-annotations-java5", "qdox-java-parser", "gson", "jps-javac-extension", "fastutil-min"
        ].each {
          projectLibrary(it)
        }
        context.findRequiredModule("intellij.java.aetherDependencyResolver").libraryCollection.libraries.each {
          jpsLibrary(it)
        }

        jar("ant-jps.jar") { module("intellij.ant.jps") }
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

  static String getZipName(String buildNumber) {
    "standalone-jps-${buildNumber}.zip"
  }
}
