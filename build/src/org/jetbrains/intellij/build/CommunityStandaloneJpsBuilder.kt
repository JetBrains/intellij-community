// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.io.NioFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.impl.BaseLayout
import org.jetbrains.intellij.build.impl.JarPackager
import org.jetbrains.intellij.build.impl.LibraryPackMode
import org.jetbrains.intellij.build.impl.ModuleItem
import org.jetbrains.intellij.build.impl.ModuleOutputPatcher
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.buildJar
import org.jetbrains.intellij.build.io.zipWithCompression
import java.nio.file.Files
import java.nio.file.Path

/**
 * Creates JARs containing classes required to run the external build for IDEA project without IDE.
 */
suspend fun buildCommunityStandaloneJpsBuilder(
  targetDir: Path,
  context: BuildContext,
  dryRun: Boolean = false,
  layoutCustomizer: ((BaseLayout) -> Unit) = {},
) {
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
    "intellij.libraries.hash4j",
    "intellij.libraries.caffeine",
    "intellij.libraries.gson",
    "intellij.libraries.fastutil",
    "intellij.libraries.mvstore",
    "intellij.libraries.commons.lang3",
    "intellij.libraries.commons.logging",
    "intellij.libraries.commons.codec",
    "intellij.libraries.aalto.xml",
    "intellij.libraries.lz4",
    "intellij.libraries.http.client",
    "intellij.libraries.cli.parser",
    "intellij.libraries.asm",
    "intellij.libraries.jgoodies.forms",
    "intellij.libraries.oro.matcher",
    "intellij.libraries.plexus.utils",
    "intellij.libraries.protobuf",
    "intellij.libraries.maven.resolver.provider",
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
    "Log4J",
    "Eclipse",
    "netty-jps",
    "slf4j-api",
    "jetbrains-annotations",
    "jps-javac-extension",
    "kotlin-stdlib",
    "kotlinx-coroutines-core",
    "kotlin-metadata",
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
    JarPackager.pack(
      includedModules = layout.includedModules,
      outputDir = tempDir,
      isRootDir = false,
      isCodesignEnabled = false,
      layout = layout,
      platformLayout = null,
      moduleOutputPatcher = ModuleOutputPatcher(),
      dryRun = dryRun,
      context = context,
    )

    val targetFile = targetDir.resolve("standalone-jps-$buildNumber.zip")
    withContext(Dispatchers.IO) {
      buildJar(
        targetFile = tempDir.resolve("jps-build-test-$buildNumber.jar"),
        moduleNames = listOf(
          "intellij.platform.jps.build",
          "intellij.platform.jps.model.tests",
          "intellij.platform.jps.model.serialization.tests"
        ),
        context = context,
      )
      zipWithCompression(targetFile = targetFile, dirs = mapOf(tempDir to ""))
    }

    context.notifyArtifactBuilt(targetFile)
  }
  finally {
    withContext(Dispatchers.IO + NonCancellable) {
      NioFiles.deleteRecursively(tempDir)
    }
  }
}
