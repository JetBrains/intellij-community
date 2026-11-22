// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.buildScripts.distGenerator

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.dev.LayoutAssetSource
import org.jetbrains.intellij.build.impl.BazelTargetsInfo
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * The case-safe name of a split product: the name of its divergent plan file and of its chain stem. Two products whose
 * `dev-build.json` keys differ in case only, `idea` and `Idea`, fold to one path on a case-insensitive file system, so
 * the later one carries a case-safe name.
 */
class DistCaseSafeProductNameTest {
  @Test
  fun `a product without a case-safe name is named by its key`() {
    assertThat(CommunityDistHalf.caseSafeProductName("AndroidStudio")).isEqualTo("AndroidStudio")
    assertThat(CommunityDistHalf.caseSafeProductName("NotSplit")).isEqualTo("NotSplit")
  }

  @Test
  fun `the community IDEA is named by its case-safe name`() {
    assertThat(CommunityDistHalf.caseSafeProductName("Idea")).isEqualTo("idea_community")
  }

  @Test
  fun `the split products of the community half fold to distinct names`() {
    val identities = CommunityDistHalf.splitProducts.map { CommunityDistHalf.caseSafeProductName(it).lowercase() }
    assertThat(identities).doesNotHaveDuplicates()
  }

  @Test
  fun `the guard accepts a later product with a case-safe name`() {
    assertThatCode { checkSplitProductNamesAreCaseSafe(linkedMapOf("idea" to "", "Idea" to "idea_community", "Other" to "")) }
      .doesNotThrowAnyException()
  }

  @Test
  fun `the guard names both products that fold to one name`() {
    assertThatThrownBy { checkSplitProductNamesAreCaseSafe(linkedMapOf("idea" to "", "Other" to "", "Idea" to "")) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("'idea'")
      .hasMessageContaining("'Idea'")
      .hasMessageContaining("caseSafeName")
  }

  @Test
  fun `the guard refuses a case-safe name that does not start with the folded key`() {
    assertThatThrownBy { checkSplitProductNamesAreCaseSafe(linkedMapOf("Idea" to "Idea_community")) }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("'Idea_community'")
      .hasMessageContaining("'idea_'")
  }

  @Test
  fun `a half extends another half that it holds in the same order with equal values`() {
    val upstream = syntheticHalf(linkedMapOf("A" to SplitDistribution(), "B" to SplitDistribution(caseSafeName = "b_x")))

    val extending = linkedMapOf("A" to SplitDistribution(), "C" to SplitDistribution(), "B" to SplitDistribution(caseSafeName = "b_x"))
    assertThatCode { checkSplitDistributionsExtend(upstream, syntheticHalf(extending)) }.doesNotThrowAnyException()
    assertThatThrownBy { checkSplitDistributionsExtend(upstream, syntheticHalf(linkedMapOf("A" to SplitDistribution()))) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("'B'")
    assertThatThrownBy { checkSplitDistributionsExtend(upstream, syntheticHalf(linkedMapOf("A" to SplitDistribution(), "B" to SplitDistribution()))) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("'B'")
    assertThatThrownBy {
      checkSplitDistributionsExtend(upstream, syntheticHalf(linkedMapOf("B" to SplitDistribution(caseSafeName = "b_x"), "A" to SplitDistribution())))
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("order")
  }

  @Test
  fun `the community half has no capability, and its binder refuses a closed source and names it`() {
    assertThat(CommunityDistHalf.capabilities).isEmpty()
    assertThat(CommunityDistHalf.embeddedFrontend).isNull()
    assertThat(CommunityDistHalf.platformPatches).isNull()
    val source = LayoutAssetSource.CidrDependency(name = "cmake", platform = "mac", arch = "aarch64")
    val index = DistBazelIndex(
      targets = BazelTargetsInfo.TargetsFile(modules = emptyMap(), projectLibraries = emptyMap(), pluginDistributionTargets = emptyMap()),
      projectRoot = Path.of("/nonexistent"),
    )
    assertThatThrownBy { CommunityDistHalf.assetBinder.bind(source, index) }
      .isInstanceOf(DistUnplannableLayoutException::class.java)
      .hasMessageContaining("community half")
      .hasMessageContaining(source.toString())
  }
}

/** A half that states only [splitDistributions] and delegates every other fact to the community half. */
private fun syntheticHalf(splitDistributions: Map<String, SplitDistribution>): DistHalf {
  return object : DistHalf by CommunityDistHalf {
    override val name: String
      get() = "synthetic"

    override val splitDistributions: Map<String, SplitDistribution>
      get() = splitDistributions
  }
}
