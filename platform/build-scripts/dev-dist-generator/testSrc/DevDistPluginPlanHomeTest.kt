// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.buildScripts.devDistGenerator

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** The plan home rule over a synthetic index: the half of the main module, and the labels a community plan text names. */
class DevDistPluginPlanHomeTest {
  @TempDir
  lateinit var dir: Path

  private lateinit var index: DevDistBazelIndex

  @BeforeEach
  fun createIndex() {
    index = syntheticIndex(
      dir,
      "intellij.ultimate" to "//plugins/ultimate:ultimate.jar",
      "intellij.root" to "//:root.jar",
      "intellij.community" to "@community//plugins/community:community.jar",
    )
  }

  /** A plan text with one library source per label of [labels]. */
  private fun planText(vararg labels: String): String {
    return labels.joinToString(prefix = "{\"sources\": [", postfix = "]}") { "{\"input\": \"$it\", \"kind\": \"library\"}" }
  }

  @Test
  fun `the label scan finds every quoted label and skips a URL`() {
    val text = """{"a": "@ultimate_lib//:foo", "url": "https://example.com//x", "b": "//plugins/x:y", "c": "@lib//:z", "d": "plugins//x"}"""

    assertThat(planTextLabels(text).toList()).containsExactly("@ultimate_lib//:foo", "//plugins/x:y", "@lib//:z")
  }

  @Test
  fun `an ultimate plugin has its own package whatever its plan names`() {
    val home = devDistPluginPlanHome("intellij.ultimate", listOf(planText("@ultimate_lib//:foo", "@community//platform:core")), index)

    assertThat(home.directory).isEqualTo("plugins/ultimate")
    assertThat(home.packageLabel).isEqualTo("//plugins/ultimate")
    assertThat(home.callIsCrossHalf).isFalse()
    assertThat(home.exportsPlanFiles).isFalse()
    assertThat(home.isModulePackage).isTrue()
    assertThat(home.path("intellij.ultimate.dev-plan.json")).isEqualTo("plugins/ultimate/intellij.ultimate.dev-plan.json")
    assertThat(home.label("intellij.ultimate.dev-plan.json")).isEqualTo("//plugins/ultimate:intellij.ultimate.dev-plan.json")
  }

  @Test
  fun `an ultimate plugin in the root package has an empty directory`() {
    val home = devDistPluginPlanHome("intellij.root", emptyList(), index)

    assertThat(home.directory).isEmpty()
    assertThat(home.packageLabel).isEqualTo("//")
    assertThat(home.path("intellij.root.dev-plan.json")).isEqualTo("intellij.root.dev-plan.json")
    assertThat(home.label("intellij.root.dev-plan.json")).isEqualTo("//:intellij.root.dev-plan.json")
  }

  @Test
  fun `a community plugin whose plan names only community repositories has its community package and exports the plan files`() {
    val texts = listOf(planText("@lib//:foo", "@community//platform:core"), planText("//plugins/community:resources"))

    val home = devDistPluginPlanHome("intellij.community", texts, index)

    assertThat(home.directory).isEqualTo("community/plugins/community")
    assertThat(home.packageLabel).isEqualTo("@community//plugins/community")
    assertThat(home.callIsCrossHalf).isTrue()
    assertThat(home.exportsPlanFiles).isTrue()
    assertThat(home.isModulePackage).isTrue()
    assertThat(home.label("intellij.community.dev-plan.json")).isEqualTo("@community//plugins/community:intellij.community.dev-plan.json")
  }

  @Test
  fun `a community plugin whose plan names another repository has the cross-half package`() {
    val texts = listOf(planText("@lib//:foo"), planText("@ultimate_lib//:profiler-ultimate-jmc-flightrecorder"))

    val home = devDistPluginPlanHome("intellij.community", texts, index)

    assertThat(home.directory).isEqualTo("build/dev-dist-descriptors/intellij.community")
    assertThat(home.packageLabel).isEqualTo("//build/dev-dist-descriptors/intellij.community")
    assertThat(home.callIsCrossHalf).isTrue()
    assertThat(home.exportsPlanFiles).isFalse()
    assertThat(home.isModulePackage).isFalse()
    assertThat(home.path("intellij.community.dev-plan.json")).isEqualTo("build/dev-dist-descriptors/intellij.community/intellij.community.dev-plan.json")
  }

  @Test
  fun `a plugin the index does not place has no home`() {
    assertThatThrownBy { devDistPluginPlanHome("intellij.unplaced", emptyList(), index) }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessage("Plugin 'intellij.unplaced' has no Bazel package, so its plan files have no home")
  }

  @Test
  fun `a home refuses a target label, an export without a cross-half call, and an unsafe directory`() {
    assertThatThrownBy { DevDistPluginPlanHome("plugins/x", "//plugins/x:x", callIsCrossHalf = false, exportsPlanFiles = false) }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("is a package, not a target")
    assertThatThrownBy { DevDistPluginPlanHome("plugins/x", "@ultimate_lib//", callIsCrossHalf = false, exportsPlanFiles = false) }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("is a package of the main repository or of the community half")
    assertThatThrownBy { DevDistPluginPlanHome("plugins/x", "//plugins/x", callIsCrossHalf = false, exportsPlanFiles = true) }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("only when the call lives cross-half")
    for (directory in listOf("plugins/../x", "plugins//x", "plugins/x.", "plugins/nul", "plugins/x y")) {
      assertThatThrownBy { DevDistPluginPlanHome(directory, "//plugins/x", callIsCrossHalf = false, exportsPlanFiles = false) }
        .isInstanceOf(IllegalArgumentException::class.java)
        .hasMessage("Unsafe plan home directory: '$directory'")
    }
  }
}
