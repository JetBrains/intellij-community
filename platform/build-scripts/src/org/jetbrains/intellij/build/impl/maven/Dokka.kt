// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.maven

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.io.runProcess
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.listDirectoryEntries

/**
 * Both Javadoc and KDoc are processed, see https://kotlinlang.org/docs/dokka-introduction.html
 */
@OptIn(ExperimentalPathApi::class)
internal class Dokka(private val context: BuildContext) {
  private val moduleWithDokka: JpsModule by lazy {
    context.findRequiredModule("intellij.libraries.dokka")
  }

  private fun JpsModule.requireLibrary(name: String): JpsLibrary {
    return libraryCollection.findLibrary(name) ?: error("Missing library $name for module ${this.name}")
  }

  /**
   * See https://kotlinlang.org/docs/dokka-cli.html
   */
  private val cli: Path by lazy {
    val lib = moduleWithDokka.requireLibrary("jetbrains.dokka.cli")
    val jars = lib.getPaths(JpsOrderRootType.COMPILED)
    jars.singleOrNull() ?: error("Only one jar is expected in ${lib.name} but found $jars")
  }

  /**
   * See https://kotlinlang.org/docs/dokka-javadoc.html
   */
  private val pluginsClasspath: List<Path> by lazy {
    val dokkaJavadoc = moduleWithDokka.requireLibrary("jetbrains.dokka.javadoc.plugin")
    val dokkaKotlin = moduleWithDokka.requireLibrary("jetbrains.dokka.analysis.kotlin.descriptors")
    dokkaJavadoc.getPaths(JpsOrderRootType.COMPILED) +
    dokkaKotlin.getPaths(JpsOrderRootType.COMPILED)
  }

  /**
   * @return [outputDir] containing all the docs generated
   */
  suspend fun generateDocumentation(
    modules: List<JpsModule>,
    outputDir: Path = context.paths.tempDir.resolve("${modules.joinToString(separator = "-") { it.name }}-dokka"),
  ): Path {
    return spanBuilder("generate documentation").setAttribute("modules", modules.joinToString { it.name }).use {
      require(modules.any()) {
        "No modules supplied to generate Dokka documentation"
      }
      outputDir.deleteRecursively()
      outputDir.createDirectories()
      val sourceSet = modules.asSequence()
        .flatMap { it.sourceRoots }
        .joinToString(prefix = "-src ", separator = ";") {
          it.path.absolutePathString()
        }
      runProcess(
        args = listOf(
          context.stableJavaExecutable.absolutePathString(),
          "-jar", cli.absolutePathString(),
          "-sourceSet", sourceSet,
          "-outputDir", outputDir.absolutePathString(),
          "-loggingLevel", "WARN",
          "-pluginsClasspath", pluginsClasspath.joinToString(separator = ";") { it.absolutePathString() },
        )
      )
      check(outputDir.listDirectoryEntries(glob = "index.html").any()) {
        "$outputDir contains no index.html"
      }
      outputDir
    }
  }
}