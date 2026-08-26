// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.classPath

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.dev.AssembledPrepackedPluginContentJar
import org.jetbrains.intellij.build.dev.PrepackedPluginContentJar
import org.jetbrains.intellij.build.impl.ModuleItem
import org.jetbrains.intellij.build.impl.projectStructureMapping.CustomAssetEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Where a handed-off jar lands in a plugin's classpath.
 *
 * The position is what these tests are about, and it is invisible to any byte comparison of a jar.
 * `putMoreLikelyPluginJarsFirst` is stable and its last tiebreak is the file-name length, so two jars whose names are
 * equally long keep the order this function gave them. Appending the handed-off jars would therefore let the *producer*
 * of a jar decide the plugin classpath.
 */
internal class PrepackedClassPathOrderTest {
  private val libDir: Path = Path.of("/dist/plugins/example/lib")

  @Test
  fun `a handed-off jar takes the place of the asset it replaced`() {
    // The assembly created `a.jar`, then would have created `handed.jar`, then created `b.jar`.
    val files = merge(
      distribution = listOf(moduleOutput("a.jar"), moduleOutput("b.jar")),
      prepacked = listOf(handedOver("handed.jar", assetOrdinal = 1)),
    )

    assertThat(files).containsExactly(jar("a.jar"), jar("handed.jar"), jar("b.jar"))
  }

  @Test
  fun `ordinal zero comes first and an ordinal past the last asset comes last`() {
    val files = merge(
      distribution = listOf(moduleOutput("a.jar")),
      prepacked = listOf(handedOver("last.jar", assetOrdinal = 9), handedOver("first.jar", assetOrdinal = 0)),
    )

    assertThat(files).containsExactly(jar("first.jar"), jar("a.jar"), jar("last.jar"))
  }

  @Test
  fun `two jars handed over at one ordinal keep the order the assembly recorded`() {
    val files = merge(
      distribution = listOf(moduleOutput("a.jar")),
      prepacked = listOf(handedOver("second.jar", assetOrdinal = 1), handedOver("third.jar", assetOrdinal = 1)),
    )

    assertThat(files).containsExactly(jar("a.jar"), jar("second.jar"), jar("third.jar"))
  }

  @Test
  fun `several entries of one asset advance the position once`() {
    // A jar merging a module output and two libraries is three entries and one asset. An asset follows the recorded
    // position, so counting entries instead of assets drains the hand-off two places early and this case sees it.
    val files = merge(
      distribution = listOf(
        moduleOutput("a.jar"), moduleOutput("a.jar"), moduleOutput("a.jar"),
        moduleOutput("b.jar"),
        moduleOutput("c.jar"),
      ),
      prepacked = listOf(handedOver("handed.jar", assetOrdinal = 2)),
    )

    assertThat(files).containsExactly(jar("a.jar"), jar("b.jar"), jar("handed.jar"), jar("c.jar"))
  }

  @Test
  fun `an asset the classpath refuses still holds its position`() {
    // `modules/inner.jar` is not on the classpath, and a `CustomAssetEntry` that is not a jar is not either. Both
    // consumed an asset index, so a hand-off recorded after them belongs after `a.jar`.
    val files = merge(
      distribution = listOf(
        moduleOutput("a.jar"),
        moduleOutput("modules/inner.jar"),
        CustomAssetEntry(path = libDir.resolve("icons"), hash = 0, relativeOutputFile = "icons"),
        moduleOutput("b.jar"),
      ),
      prepacked = listOf(handedOver("handed.jar", assetOrdinal = 3)),
    )

    assertThat(files).containsExactly(jar("a.jar"), jar("handed.jar"), jar("b.jar"))
  }

  @Test
  fun `a jar handed over below lib is not on the classpath`() {
    val files = merge(
      distribution = listOf(moduleOutput("a.jar")),
      prepacked = listOf(handedOver("modules/handed.jar", assetOrdinal = 0)),
    )

    assertThat(files).containsExactly(jar("a.jar"))
  }

  @Test
  fun `a destination an asset already produced is listed once`() {
    val files = merge(
      distribution = listOf(moduleOutput("a.jar")),
      prepacked = listOf(handedOver("a.jar", assetOrdinal = 1)),
    )

    assertThat(files).containsExactly(jar("a.jar"))
  }

  private fun merge(
    distribution: List<DistributionFileEntry>,
    prepacked: List<AssembledPrepackedPluginContentJar>,
  ): List<Path> = mergePrepackedIntoAssetOrder(distribution = distribution, prepacked = prepacked, libDir = libDir)

  private fun jar(relativeOutputFile: String): Path = libDir.resolve(relativeOutputFile)

  private fun moduleOutput(relativeOutputFile: String): ModuleOutputEntry {
    return ModuleOutputEntry(
      path = libDir.resolve(relativeOutputFile),
      owner = ModuleItem(moduleName = "intellij.example", relativeOutputFile = relativeOutputFile, reason = null),
      size = 0,
      hash = 0,
      relativeOutputFile = relativeOutputFile,
    )
  }

  private fun handedOver(relativeOutputFile: String, assetOrdinal: Int): AssembledPrepackedPluginContentJar {
    return AssembledPrepackedPluginContentJar(
      jar = PrepackedPluginContentJar(
        pluginMainModule = "intellij.example.plugin",
        contentModule = "intellij." + relativeOutputFile.substringAfterLast('/').removeSuffix(".jar"),
        relativeOutputFile = relativeOutputFile,
      ),
      assetOrdinal = assetOrdinal,
    )
  }
}
