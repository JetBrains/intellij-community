// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test

class DevDistPluginPlanFoldTest {
  private val hostPlatforms = listOf("darwin_aarch64", "darwin_x64", "linux_aarch64", "linux_x64", "windows_aarch64", "windows_x64")

  @Test
  fun `two platform texts fold into one body that resolves back to each`() {
    val darwin = planText(variant = "darwin_aarch64", destination = "lib/native/mac", pattern = "macOS/libx.dylib")
    val linux = planText(variant = "linux_x64", destination = "lib/native/linux", pattern = "Linux/libx.so")

    val fold = fold("darwin_aarch64" to darwin, "linux_x64" to linux) as DevDistPluginPlanFold.Folded

    assertThat(fold.slotNames).containsExactly("destination", "pattern")
    assertThat(fold.body)
      .contains("\"variant\": \"{platform}\"")
      .contains("\"destination\": \"{platform:destination}\"")
      .contains("\"root\": \"{platform:destination}\"")
      .contains("\"pattern\": \"{platform:pattern}\"")
      .doesNotContain("darwin_aarch64", "lib/native/mac")
    assertThat(fold.valuesByPlatform.keys).containsExactly("darwin_aarch64", "linux_x64")
    assertThat(fold.valuesByPlatform.getValue("darwin_aarch64"))
      .containsExactly(entry("destination", "lib/native/mac"), entry("pattern", "macOS/libx.dylib"))
    assertThat(fold.valuesByPlatform.getValue("linux_x64"))
      .containsExactly(entry("destination", "lib/native/linux"), entry("pattern", "Linux/libx.so"))
    assertThat(resolvePluginPlanText(fold.body, "darwin_aarch64", fold.valuesByPlatform.getValue("darwin_aarch64"))).isEqualTo(darwin)
    assertThat(resolvePluginPlanText(fold.body, "linux_x64", fold.valuesByPlatform.getValue("linux_x64"))).isEqualTo(linux)
  }

  /** Records that differ only in the variant fold with no slot. Every platform keeps an entry with no value. */
  @Test
  fun `texts that differ only in the variant fold with no slot`() {
    val darwin = planText(variant = "darwin_aarch64", destination = "lib/native", pattern = "libx")
    val linux = planText(variant = "linux_x64", destination = "lib/native", pattern = "libx")

    val fold = fold("darwin_aarch64" to darwin, "linux_x64" to linux) as DevDistPluginPlanFold.Folded

    assertThat(fold.slotNames).isEmpty()
    assertThat(fold.body).contains("\"variant\": \"{platform}\"").doesNotContain("{platform:")
    assertThat(fold.valuesByPlatform).containsOnlyKeys("darwin_aarch64", "linux_x64")
    assertThat(fold.valuesByPlatform.values).allSatisfy { assertThat(it).isEmpty() }
    assertThat(resolvePluginPlanText(fold.body, "darwin_aarch64", emptyMap())).isEqualTo(darwin)
    assertThat(resolvePluginPlanText(fold.body, "linux_x64", emptyMap())).isEqualTo(linux)
  }

  @Test
  fun `a distinct tuple under a reused key refuses the fold`() {
    val darwin = """{"variant": "darwin_aarch64", "first": {"inputs": ["x:0:output"]}, "second": {"inputs": ["y:0:0", "x:0:output"]}}"""
    val linux = """{"variant": "linux_x64", "first": {"inputs": ["x:4:output"]}, "second": {"inputs": ["y:4:0", "x:4:output"]}}"""

    val fold = fold("darwin_aarch64" to darwin, "linux_x64" to linux) as DevDistPluginPlanFold.Refused

    assertThat(fold.path).isEqualTo("$.second.inputs[0]")
    assertThat(fold.platformA).isEqualTo("darwin_aarch64")
    assertThat(fold.platformB).isEqualTo("linux_x64")
    assertThat(fold.reason).isEqualTo("a distinct value tuple reuses the slot key 'inputs'")
  }

  @Test
  fun `an equal tuple under a reused key shares the slot`() {
    val darwin = """{"variant": "darwin_aarch64", "first": {"inputs": ["x:0:output"]}, "second": {"inputs": ["x:0:output"]}}"""
    val linux = """{"variant": "linux_x64", "first": {"inputs": ["x:4:output"]}, "second": {"inputs": ["x:4:output"]}}"""

    val fold = fold("darwin_aarch64" to darwin, "linux_x64" to linux) as DevDistPluginPlanFold.Folded

    assertThat(fold.slotNames).containsExactly("inputs")
    assertThat(fold.body).isEqualTo("""{"variant": "{platform}", "first": {"inputs": ["{platform:inputs}"]}, "second": {"inputs": ["{platform:inputs}"]}}""")
  }

  @Test
  fun `a shape difference refuses the fold and names the first path`() {
    val darwin = """{"variant": "darwin_aarch64", "assets": [{"destination": "a"}], "extra": {"kind": "tree"}}"""
    val linux = """{"variant": "linux_x64", "assets": [{"destination": "a"}, {"destination": "b"}], "extra": {"kind": "tree"}}"""

    val fold = fold("darwin_aarch64" to darwin, "linux_x64" to linux) as DevDistPluginPlanFold.Refused

    assertThat(fold.path).isEqualTo("$.assets")
    assertThat(fold.platformA).isEqualTo("darwin_aarch64")
    assertThat(fold.platformB).isEqualTo("linux_x64")
    assertThat(fold.reason).isEqualTo("the array lengths differ")
  }

  @Test
  fun `a differing non-string leaf refuses the fold`() {
    val darwin = """{"variant": "darwin_aarch64", "mappings": [{"pattern": "a", "stripComponents": 1}]}"""
    val linux = """{"variant": "linux_x64", "mappings": [{"pattern": "a", "stripComponents": 2}]}"""

    val fold = fold("darwin_aarch64" to darwin, "linux_x64" to linux) as DevDistPluginPlanFold.Refused

    assertThat(fold.path).isEqualTo("$.mappings[0].stripComponents")
    assertThat(fold.reason).isEqualTo("the non-string leaves differ")
  }

  @Test
  fun `a token inside a record text fails the fold`() {
    val darwin = """{"variant": "darwin_aarch64", "destination": "{platform:destination}"}"""
    val linux = """{"variant": "linux_x64", "destination": "lib/linux"}"""

    assertThatThrownBy { fold("darwin_aarch64" to darwin, "linux_x64" to linux) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("darwin_aarch64")
      .hasMessageContaining("platform token")
  }

  @Test
  fun `a slot value that needs an escape fails the fold`() {
    val darwin = """{"variant": "darwin_aarch64", "destination": "lib\"mac"}"""
    val linux = """{"variant": "linux_x64", "destination": "lib/linux"}"""

    assertThatThrownBy { fold("darwin_aarch64" to darwin, "linux_x64" to linux) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("destination")
      .hasMessageContaining("needs a JSON escape")
  }

  @Test
  fun `the six webp records fold and resolve back byte for byte`() {
    val texts = LinkedHashMap<String, String>()
    for (platform in hostPlatforms) texts.put(platform, webpText(platform))

    val fold = foldDevDistPluginPlanTexts(texts) as DevDistPluginPlanFold.Folded

    assertThat(fold.slotNames).containsExactly("destination", "modelSignature", "pattern")
    assertThat(fold.body).contains("\"variant\": \"{platform}\"").doesNotContain("darwin", "aarch64")
    assertThat(fold.valuesByPlatform.getValue("windows_x64"))
      .containsEntry("destination", "lib/libwebp/win/amd64")
      .containsEntry("pattern", "Windows-X86_64/webp_jni.dll")
    for (platform in hostPlatforms) {
      assertThat(resolvePluginPlanText(fold.body, platform, fold.valuesByPlatform.getValue(platform))).isEqualTo(texts.getValue(platform))
    }
  }

  private fun fold(vararg textsByPlatform: Pair<String, String>): DevDistPluginPlanFold {
    val texts = LinkedHashMap<String, String>()
    for ((platform, text) in textsByPlatform) texts.put(platform, text)
    return foldDevDistPluginPlanTexts(texts)
  }

  private fun webpText(platform: String): String {
    val resource = "/devDistPluginPlanFold/intellij.webp.$platform.json"
    val stream = requireNotNull(DevDistPluginPlanFoldTest::class.java.getResourceAsStream(resource)) { "Missing test resource $resource" }
    return stream.use { it.readAllBytes().toString(Charsets.UTF_8) }
  }

  /** A text in the plan file form: pretty-printed, string leaves, a string array, a number and a boolean. */
  private fun planText(variant: String, destination: String, pattern: String): String = """
    {
        "version": 2,
        "plugin": "intellij.sample",
        "variant": "$variant",
        "assets": [
            {
                "destination": "$destination",
                "inputs": [
                    "layout-assets:custom-asset:0:output"
                ],
                "kind": "tree",
                "classPath": false
            }
        ],
        "operations": [
            {
                "id": "layout-assets:custom-asset:0",
                "layoutAssets": {
                    "root": "$destination",
                    "mappings": [
                        {
                            "pattern": "$pattern",
                            "stripComponents": 1
                        }
                    ]
                }
            }
        ]
    }
  """.trimIndent() + "\n"
}
