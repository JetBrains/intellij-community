// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.buildScripts.devDistGenerator

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** The plan home rule over a synthetic index: the half of the main module and the half of the run. */
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

  @Test
  fun `an ultimate plugin has its own package`() {
    val home = devDistPluginPlanHome("intellij.ultimate", index)

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
    val home = devDistPluginPlanHome("intellij.root", index)

    assertThat(home.directory).isEmpty()
    assertThat(home.packageLabel).isEqualTo("//")
    assertThat(home.path("intellij.root.dev-plan.json")).isEqualTo("intellij.root.dev-plan.json")
    assertThat(home.label("intellij.root.dev-plan.json")).isEqualTo("//:intellij.root.dev-plan.json")
  }

  @Test
  fun `the ultimate half homes a community plugin in the cross-half package`() {
    val home = devDistPluginPlanHome("intellij.community", index)

    assertThat(home.directory).isEqualTo("build/dev-dist-descriptors/intellij.community")
    assertThat(home.packageLabel).isEqualTo("//build/dev-dist-descriptors/intellij.community")
    assertThat(home.callIsCrossHalf).isTrue()
    assertThat(home.exportsPlanFiles).isFalse()
    assertThat(home.isModulePackage).isFalse()
    assertThat(home.path("intellij.community.dev-plan.json")).isEqualTo("build/dev-dist-descriptors/intellij.community/intellij.community.dev-plan.json")
  }

  @Test
  fun `the community half homes a community plugin in its own package, relative to the community root`() {
    val communityIndex = DevDistBazelIndex(targets = index.targets, projectRoot = dir, planPackageIsCommunity = true)

    val home = devDistPluginPlanHome("intellij.community", communityIndex)

    assertThat(home.directory).isEqualTo("plugins/community")
    assertThat(home.packageLabel).isEqualTo("@community//plugins/community")
    assertThat(home.callIsCrossHalf).isFalse()
    assertThat(home.exportsPlanFiles).isFalse()
    assertThat(home.isModulePackage).isTrue()
  }

  @Test
  fun `a plugin the index does not place has no home`() {
    assertThatThrownBy { devDistPluginPlanHome("intellij.unplaced", index) }
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
