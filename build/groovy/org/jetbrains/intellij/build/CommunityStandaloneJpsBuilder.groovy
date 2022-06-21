// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.jetbrains.intellij.build.impl.LayoutBuilder
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectStructureMapping
import org.jetbrains.jps.model.library.JpsLibrary

import java.nio.file.Path

/**
 * Creates JARs containing classes required to run the external build for IDEA project without IDE.
 */
@CompileStatic
final class CommunityStandaloneJpsBuilder {
  private final BuildContext buildContext

  CommunityStandaloneJpsBuilder(BuildContext buildContext) {
    this.buildContext = buildContext
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  void processJpsLayout(String targetDir, String buildNumber, ProjectStructureMapping projectStructureMapping,
                        boolean copyFiles, @DelegatesTo(LayoutBuilder.LayoutSpec) Closure additionalJars) {
    BuildContext context = buildContext
    new LayoutBuilder(buildContext).process(targetDir, projectStructureMapping, copyFiles) {
      zip(getZipName(buildNumber)) {
        jar("util.jar") {
          module("intellij.platform.util")
          module("intellij.platform.util.classLoader")
          module("intellij.platform.util.text.matching")
          module("intellij.platform.util.base")
          module("intellij.platform.util.xmlDom")
          module("intellij.platform.tracing.rt")
          module("intellij.platform.util.diff")
          module("intellij.platform.util.rt.java8")
        }

        jar("util_rt.jar") {
          module("intellij.platform.util.rt")
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
        dir("rt") {
          jar("jps-javac-rt-rpc.jar") {
            module("intellij.platform.jps.build.javac.rt.rpc")
          }
          moduleLibrary("intellij.platform.jps.build.javac.rt.rpc", "protobuf-java6")
        }
        //layout of groovy jars must be consistent with GroovyBuilder.getGroovyRtRoots method
        jar("groovy-jps.jar") { module("intellij.groovy.jps") }
        jar("groovy-rt.jar") { module("intellij.groovy.rt") }
        jar("groovy-rt-class-loader.jar") { module("intellij.groovy.rt.classLoader") }
        jar("groovy-constants-rt.jar") { module("intellij.groovy.constants.rt") }
        jar("java-guiForms-jps.jar") { module("intellij.java.guiForms.jps") }


        jar("maven-jps.jar") { module("intellij.maven.jps") }
        jar("aether-dependency-resolver.jar") { module("intellij.java.aetherDependencyResolver") }
        jar("gradle-jps.jar") { module("intellij.gradle.jps") }

        jar("eclipse-jps.jar") { module("intellij.eclipse.jps") }
        jar("eclipse-common.jar") { module("intellij.eclipse.common") }
        jar("devkit-jps.jar") { module("intellij.devkit.jps") }
        jar("java-langInjection-jps.jar") { module("intellij.java.langInjection.jps") }

        jar("space-java-jps.jar") { module("intellij.space.java.jps") }

        for (String name in List.of(
          "JDOM", "jna", "OroMatcher", "Trove4j", "ASM", "NanoXML", "protobuf", "cli-parser", "Log4J", "jgoodies-forms", "Eclipse",
          "netty-codec-http", "lz4-java", "commons-codec", "commons-logging", "http-client", "Slf4j", "Guava", "plexus-utils",
          "jetbrains-annotations-java5", "gson", "jps-javac-extension", "fastutil-min", "kotlin-stdlib-jdk8",
          "commons-lang3", "maven-resolver-provider", "netty-buffer", "aalto-xml"
        )) {
          projectLibrary(name)
        }
        moduleLibrary("intellij.platform.jps.build", "qdox-java-parser")
        for (JpsLibrary library in context.findRequiredModule("intellij.java.aetherDependencyResolver").libraryCollection.libraries) {
          jpsLibrary(library)
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
    buildContext.notifyArtifactWasBuilt(Path.of(targetDir).normalize().toAbsolutePath())
  }

  static String getZipName(String buildNumber) {
    return "standalone-jps-${buildNumber}.zip"
  }
}
