// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.impl.BaseLayout
import org.jetbrains.intellij.build.impl.JarPackager
import org.jetbrains.intellij.build.impl.LibraryPackMode
import org.jetbrains.intellij.build.io.deleteDir
import org.jetbrains.intellij.build.io.zipWithCompression
import org.jetbrains.intellij.build.tasks.DirSource
import org.jetbrains.intellij.build.tasks.buildJar
import java.nio.file.Files
import java.nio.file.Path

/**
 * Creates JARs containing classes required to run the external build for IDEA project without IDE.
 */
suspend fun buildCommunityStandaloneJpsBuilder(targetDir: Path, context: BuildContext) {
  val layout = BaseLayout()

  listOf(
    "intellij.platform.util",
    "intellij.platform.util.classLoader",
    "intellij.platform.util.text.matching",
    "intellij.platform.util.base",
    "intellij.platform.util.xmlDom",
    "intellij.platform.util.jdom",
    "intellij.platform.tracing.rt",
    "intellij.platform.util.diff",
    "intellij.platform.util.rt.java8",
  ).forEach {
    layout.withModule(it, "util.jar")
  }

  layout.withModule("intellij.platform.util.rt", "util_rt.jar")
  layout.withModule("intellij.platform.jps.build.launcher", "jps-launcher.jar")


  listOf(
    "intellij.platform.jps.model",
    "intellij.platform.jps.model.impl",
    "intellij.platform.jps.model.serialization",
  ).forEach {
    layout.withModule(it, "jps-model.jar")
  }

  listOf(
    "intellij.java.guiForms.rt",
    "intellij.java.guiForms.compiler",
    "intellij.java.compiler.instrumentationUtil",
    "intellij.java.compiler.instrumentationUtil.java8",
    "intellij.platform.jps.build",
    "intellij.tools.jps.build.standalone",
  ).forEach {
    layout.withModule(it, "jps-builders.jar")
  }

  layout.withModule("intellij.java.rt", "idea_rt.jar")
  layout.withModule("intellij.platform.jps.build.javac.rt", "jps-builders-6.jar")

  layout.withModule("intellij.platform.jps.build.javac.rt.rpc", "rt/jps-javac-rt-rpc.jar")
  layout.withModuleLibrary(libraryName = "protobuf-java6",
                           moduleName = "intellij.platform.jps.build.javac.rt.rpc",
                           relativeOutputPath = "rt/protobuf-java6.jar")

  // layout of groovy jars must be consistent with GroovyBuilder.getGroovyRtRoots method
  layout.withModule("intellij.groovy.jps", "groovy-jps.jar")
  layout.withModule("intellij.groovy.rt", "groovy-rt.jar")
  layout.withModule("intellij.groovy.rt.classLoader", "groovy-rt-class-loader.jar")
  layout.withModule("intellij.groovy.constants.rt", "groovy-constants-rt.jar")
  layout.withModule("intellij.java.guiForms.jps", "java-guiForms-jps.jar")


  layout.withModule("intellij.maven.jps", "maven-jps.jar")
  layout.withModule("intellij.java.aetherDependencyResolver", "aether-dependency-resolver.jar")
  layout.withModule("intellij.gradle.jps", "gradle-jps.jar")

  layout.withModule("intellij.eclipse.jps", "eclipse-jps.jar")
  layout.withModule("intellij.eclipse.common", "eclipse-common.jar")
  layout.withModule("intellij.devkit.jps", "devkit-jps.jar")
  layout.withModule("intellij.java.langInjection.jps", "java-langInjection-jps.jar")

  layout.withModule("intellij.space.java.jps", "space-java-jps.jar")

  for (it in listOf(
    "jna", "OroMatcher", "ASM", "NanoXML", "protobuf", "cli-parser", "Log4J", "jgoodies-forms", "Eclipse",
    "netty-codec-http", "lz4-java", "commons-codec", "commons-logging", "http-client", "Slf4j", "Guava", "plexus-utils",
    "jetbrains-annotations-java5", "gson", "jps-javac-extension", "fastutil-min", "kotlin-stdlib-jdk8",
    "commons-lang3", "maven-resolver-provider", "netty-buffer", "aalto-xml"
  )) {
    layout.withProjectLibrary(it, LibraryPackMode.STANDALONE_MERGED)
  }

  layout.withModule("intellij.ant.jps", "ant-jps.jar")

  val buildNumber = context.fullBuildNumber

  val tempDir = withContext(Dispatchers.IO) {
    Files.createDirectories(targetDir)
    Files.createTempDirectory(targetDir, "jps-standalone-community-")
  }
  try {
    JarPackager.pack(jarToModules = layout.jarToModules, outputDir = tempDir, context = context, layout = layout)

    withContext(Dispatchers.IO) {
      buildJar(tempDir.resolve("jps-build-test-$buildNumber.jar"), listOf(
        "intellij.platform.jps.build",
        "intellij.platform.jps.model.tests",
        "intellij.platform.jps.model.serialization.tests"
      ).map { moduleName ->
        DirSource(context.getModuleOutputDir(context.findRequiredModule(moduleName)))
      })


      zipWithCompression(targetDir.resolve("standalone-jps-$buildNumber.zip"), mapOf(tempDir to ""))
    }

    context.notifyArtifactWasBuilt(targetDir)
  }
  finally {
    withContext(Dispatchers.IO + NonCancellable) {
      deleteDir(tempDir)
    }
  }
}
