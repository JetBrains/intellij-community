// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync.dotnet

import org.jetbrains.intellij.build.images.sync.dotnet.DotnetIconsTransformation.dotnetDarkSuffices
import org.jetbrains.intellij.build.images.sync.dotnet.DotnetIconsTransformation.dotnetLightSuffices
import org.jetbrains.intellij.build.images.sync.dotnet.DotnetIconsTransformation.ideaDarkSuffix
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DotnetIconsTransformationTest {
  @Rule
  @JvmField
  val iconsRepo = TemporaryFolder()

  private data class Suffix(val after: String, val before: String)

  private fun `icons test`(suffices: List<String>, expected: List<Suffix>) {
    val name = "icon"
    val ext = "svg"
    suffices.forEach {
      val suffix = if (it.isNotEmpty()) "[$it]" else ""
      iconsRepo.newFile("$name$suffix.$ext").writeText(suffix)
    }
    DotnetIconsTransformation.transformToIdeaFormat(iconsRepo.root.toPath())
    iconsRepo.root.walkTopDown().filter { it.isFile }.toList().also {
      assert(it.count() == expected.count()) {
        "Expected: $expected, actual $it"
      }
    }.forEach { result ->
      val suffix = expected.singleOrNull {
        result.name == "$name${it.after}.$ext"
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

  @Test
  fun `light icons test`() = `icons test`(
    dotnetLightSuffices + "unknown",
    listOf(Suffix("", "[${dotnetLightSuffices.first()}]"))
  )

  @Test
  fun `dark icons test`() = `icons test`(
    dotnetDarkSuffices + "unknown",
    listOf(Suffix(ideaDarkSuffix, "[${dotnetDarkSuffices.first()}]"))
  )

  @Test
  fun `mixed icons test`() = `icons test`(
    dotnetDarkSuffices + dotnetLightSuffices + "unknown",
    listOf(
      Suffix("", "[${dotnetLightSuffices.first()}]"),
      Suffix(ideaDarkSuffix, "[${dotnetDarkSuffices.first()}]")
    )
  )
}
