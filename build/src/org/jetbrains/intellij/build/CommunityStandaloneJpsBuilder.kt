// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.impl.*
import org.jetbrains.intellij.build.io.deleteDir
import org.jetbrains.intellij.build.io.zipWithCompression
import java.nio.file.Files
import java.nio.file.Path

/**
 * Creates JARs containing classes required to run the external build for IDEA project without IDE.
 */
suspend fun buildCommunityStandaloneJpsBuilder(targetDir: Path,
                                               context: BuildContext,
                                               dryRun: Boolean = false,
                                               layoutCustomizer: ((BaseLayout) -> Unit) = {}) {
  val layout = PlatformLayout()

  layout.withModules(sequenceOf(
    "intellij.platform.util",
    "intellij.platform.util.multiplatform",
    "intellij.platform.util.classLoader",
    "intellij.platform.util.base",
    "intellij.platform.util.base.multiplatform",
    "intellij.platform.util.xmlDom",
    "intellij.platform.util.jdom",
    "intellij.platform.tracing.rt",
    "intellij.platform.util.diff",
    "intellij.platform.util.rt.java8",
    "intellij.platform.util.trove",
    "intellij.platform.util.nanoxml",
  ).map { ModuleItem(moduleName = it, relativeOutputFile = "util.jar", reason = null) })

  layout.withModule("intellij.platform.util.rt", "util_rt.jar")
  layout.withModule("intellij.platform.jps.build.launcher", "jps-launcher.jar")

  layout.withModule("intellij.platform.runtime.repository", "platform-runtime-repository.jar")
  layout.withModules(sequenceOf(
    "intellij.platform.jps.model",
    "intellij.platform.jps.model.impl",
    "intellij.platform.jps.model.serialization",
  ).map { ModuleItem(moduleName = it, relativeOutputFile = "jps-model.jar", reason = null) })

  layout.withModules(sequenceOf(
    "intellij.java.guiForms.rt",
    "intellij.java.guiForms.compiler",
    "intellij.java.compiler.instrumentationUtil",
    "intellij.java.compiler.instrumentationUtil.java8",
    "intellij.platform.jps.build",
    "intellij.platform.jps.build.dependencyGraph",
    "intellij.tools.jps.build.standalone",
  ).map { ModuleItem(moduleName = it, relativeOutputFile = "jps-builders.jar", reason = null) })

  layout.withModule("intellij.java.rt", "idea_rt.jar")
  layout.withModule("intellij.platform.jps.build.javac.rt", "jps-builders-6.jar")

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
  layout.withModule("intellij.devkit.runtimeModuleRepository.jps", "devkit-runtimeModuleRepository-jps.jar")
  layout.withModule("intellij.java.langInjection.jps", "java-langInjection-jps.jar")

  layout.withModule("intellij.space.java.jps", "space-java-jps.jar")

  for (it in listOf(
    "jna",
    "OroMatcher",
    "ASM",
    "protobuf",
    "cli-parser",
    "Log4J",
    "jgoodies-forms",
    "Eclipse",
    "netty-jps",
    "lz4-java",
    "commons-codec",
    "commons-logging",
    "http-client",
    "slf4j-api",
    "plexus-utils",
    "jetbrains-annotations",
    "gson",
    "jps-javac-extension",
    "fastutil-min",
    "kotlin-stdlib",
    "commons-lang3",
    "maven-resolver-provider",
    "aalto-xml",
    "caffeine",
    "mvstore",
    "kotlin-metadata",
    "hash4j"
  )) {
    layout.withProjectLibrary(it, LibraryPackMode.STANDALONE_MERGED)
  }

  layout.withModule("intellij.ant.jps", "ant-jps.jar")

  layoutCustomizer(layout)

  val buildNumber = context.fullBuildNumber

  val tempDir = withContext(Dispatchers.IO) {
    Files.createDirectories(targetDir)
    Files.createTempDirectory(targetDir, "jps-standalone-community-")
  }
  try {
    JarPackager.pack(includedModules = layout.includedModules,
                     outputDir = tempDir,
                     context = context,
                     layout = layout,
                     platformLayout = null,
                     isRootDir = false,
                     isCodesignEnabled = false,
                     moduleOutputPatcher = ModuleOutputPatcher(),
                     dryRun = dryRun)

    val targetFile = targetDir.resolve("standalone-jps-$buildNumber.zip")
    withContext(Dispatchers.IO) {
      buildJar(targetFile = tempDir.resolve("jps-build-test-$buildNumber.jar"),
               moduleNames = listOf(
                 "intellij.platform.jps.build",
                 "intellij.platform.jps.model.tests",
                 "intellij.platform.jps.model.serialization.tests"
               ),
               context = context)

      zipWithCompression(targetFile = targetFile, dirs = mapOf(tempDir to ""))
    }

    context.notifyArtifactBuilt(targetFile)
  }
  finally {
    withContext(Dispatchers.IO + NonCancellable) {
      deleteDir(tempDir)
    }
  }
}
