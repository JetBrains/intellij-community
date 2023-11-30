// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync.dotnet

import org.jetbrains.intellij.build.images.sync.dotnet.DotnetIconsTransformation.dotnetDarkSuffices
import org.jetbrains.intellij.build.images.sync.dotnet.DotnetIconsTransformation.dotnetExpUiDaySuffix
import org.jetbrains.intellij.build.images.sync.dotnet.DotnetIconsTransformation.dotnetExpUiNightSuffix
import org.jetbrains.intellij.build.images.sync.dotnet.DotnetIconsTransformation.dotnetLightSuffices
import org.jetbrains.intellij.build.images.sync.dotnet.DotnetIconsTransformation.ideaDarkSuffix
import org.jetbrains.intellij.build.images.sync.isAncestorOf
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
internal class DotnetIconsTransformationTest(private val braces: DotnetIcon.Braces) {
  companion object {
    @JvmStatic
    @Parameterized.Parameters
    fun braces(): Collection<Array<Any>> = DotnetIcon.BRACES.map {
      @Suppress("RemoveExplicitTypeArguments")
      arrayOf<Any>(it)
    }
  }

  @Rule
  @JvmField
  val iconsRepo = TemporaryFolder()

  private fun String.brace() : String = if (isNotEmpty()) "${braces.start}$this${braces.end}" else this

  private data class Suffix(val after: String, val before: String, val isExpUi: Boolean = false)

  private fun `icons test`(suffices: List<String>, expected: List<Suffix>) {
    val name = "icon"
    val ext = "svg"
    suffices.forEach {
      val suffix = it.brace()
      iconsRepo.newFile("$name$suffix.$ext").writeText(suffix)
    }
    DotnetIconsTransformation.transformToIdeaFormat(iconsRepo.root.toPath())
    iconsRepo.root.walkTopDown().filter { it.isFile }.toList().also {
      assert(it.count() == expected.count()) {
        "Expected: $expected, actual $it"
      }
    }.forEach { result ->
      val suffix = expected.singleOrNull {
        result.name == "$name${it.after}.$ext" && it.isExpUi == result.isInExpUi()
      }
      assert(suffix != null) {
        "$result doesn't have expected suffix: $expected"
      }
      val content = result.readText()
      assert(content == suffix?.before) {
        "$result check failed, expected content: ${suffix?.before}, actual: $content"
      }
    }
  }

  private fun File.isInExpUi() = iconsRepo.root.resolve("expui").toPath().isAncestorOf(this.toPath())

  @Test
  fun `light icons test`() = `icons test`(
    dotnetLightSuffices + "unknown",
    listOf(Suffix("", dotnetLightSuffices.first().brace()))
  )

  @Test
  fun `dark icons test`() = `icons test`(
    dotnetDarkSuffices + "unknown",
    // expect anything because we don't have basic light icons
    listOf()
  )

  @Test
  fun `expui day icons test`() = `icons test`(
  dotnetLightSuffices + dotnetExpUiDaySuffix,
    listOf(
      Suffix("", dotnetLightSuffices.first().brace()),
      Suffix("", dotnetExpUiDaySuffix.brace(), true),
    )
  )

  @Test
  fun `expui night icons test`() = `icons test`(
    dotnetLightSuffices + dotnetExpUiNightSuffix,
    listOf(
      Suffix("", dotnetLightSuffices.first().brace()),
      Suffix(ideaDarkSuffix, dotnetExpUiNightSuffix.brace(), true),
    )
  )

  @Test
  fun `mixed expui icons test`() = `icons test`(
    dotnetDarkSuffices + dotnetLightSuffices + dotnetExpUiNightSuffix + dotnetExpUiDaySuffix + "unknown",
    listOf(
      Suffix("", dotnetLightSuffices.first().brace()),
      Suffix(ideaDarkSuffix, dotnetDarkSuffices.first().brace()),
      Suffix(ideaDarkSuffix, dotnetExpUiNightSuffix.brace(), true),
      Suffix("", dotnetExpUiDaySuffix.brace(), true),
    )
  )


  @Test
  fun `mixed icons test`() = `icons test`(
    dotnetDarkSuffices + dotnetLightSuffices + "unknown",
    listOf(
      Suffix("", dotnetLightSuffices.first().brace()),
      Suffix(ideaDarkSuffix, dotnetDarkSuffices.first().brace())
    )
  )
}
