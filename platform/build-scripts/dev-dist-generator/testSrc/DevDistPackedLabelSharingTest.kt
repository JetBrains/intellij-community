// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.buildScripts.devDistGenerator

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

private const val VCS_SET = "intellij.platform.vcs"
private const val VCS_LOG_SET = "intellij.platform.vcs.log"

private const val VCS_IMPL = "intellij.platform.vcs.impl"
private const val VCS_IMPL_LABEL = "@community//platform/vcs-impl:vcs-impl_content_module_jar"
private const val VCS_DVCS = "intellij.platform.vcs.dvcs.impl"
private const val VCS_DVCS_LABEL = "@community//platform/dvcs-impl:dvcs-impl_content_module_jar"
private const val VCS_LOG = "intellij.platform.vcs.log.impl"
private const val VCS_LOG_LABEL = "@community//platform/vcs-log/impl:vcs-log-impl_content_module_jar"
private const val DIRECT_LABEL = "//remote-dev/frontend-customization:frontend-split-customization_idea_content_module_jar"

/** A set with one nested set. A member without a `content_module_jar` target has no `packed` entry. */
private val TABLE: Map<String, ModuleSetData> = listOf(
  ModuleSetData(
    name = VCS_SET,
    modules = listOf(VCS_DVCS, VCS_IMPL, "intellij.platform.vcs.unpacked"),
    nested = listOf(VCS_LOG_SET),
    packed = mapOf(VCS_DVCS to VCS_DVCS_LABEL, VCS_IMPL to VCS_IMPL_LABEL),
  ),
  ModuleSetData(name = VCS_LOG_SET, modules = listOf(VCS_LOG), nested = emptyList(), packed = mapOf(VCS_LOG to VCS_LOG_LABEL)),
).associateBy { it.name }

/**
 * A `platform_lib` payload writes only the packing labels no referenced module set carries. Two products that share a
 * set write the set's labels once, in `dev_dist_module_sets.bzl`. A product that does not hand over the label of a set
 * member fails the generator, because the set carries the label for every product.
 *
 * The labels that the module system loads are shared the same way. A set names a member as loaded only when every
 * product that reaches the set loads it. A member with a mixed verdict stays with the products.
 */
class DevDistPackedLabelSharingTest {
  @Test
  fun `a product that hands over every set member writes only the direct labels`() {
    val direct = sharePackedLabels(
      product = "idea",
      handedOver = listOf(VCS_LOG_LABEL, VCS_IMPL_LABEL, VCS_DVCS_LABEL, DIRECT_LABEL),
      moduleSets = listOf(VCS_SET),
      table = TABLE,
    )

    assertThat(direct).containsExactly(DIRECT_LABEL)
  }

  @Test
  fun `a product that leaves a set member out fails`() {
    assertThatThrownBy {
      sharePackedLabels(
        product = "Other",
        handedOver = listOf(VCS_LOG_LABEL, VCS_DVCS_LABEL),
        moduleSets = listOf(VCS_SET),
        table = TABLE,
      )
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("'Other'")
      .hasMessageContaining(VCS_IMPL)
  }

  @Test
  fun `a product that leaves a member of a nested set out fails`() {
    assertThatThrownBy {
      sharePackedLabels(
        product = "idea",
        handedOver = listOf(VCS_IMPL_LABEL, VCS_DVCS_LABEL),
        moduleSets = listOf(VCS_SET),
        table = TABLE,
      )
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining(VCS_LOG)
  }

  @Test
  fun `a set the table does not have leaves the labels with the product`() {
    val direct = sharePackedLabels(
      product = "idea",
      handedOver = listOf(VCS_IMPL_LABEL),
      moduleSets = listOf("intellij.platform.gone"),
      table = TABLE,
    )

    assertThat(direct).containsExactly(VCS_IMPL_LABEL)
  }

  @Test
  fun `a member that every product loads goes to the set, and a mixed member stays with the products`() {
    val table = moduleSetModuleSystemLoaded(
      table = TABLE.values.toList(),
      payloads = listOf(
        ProductModuleSystemLoading(
          product = "idea",
          moduleSets = listOf(VCS_SET),
          moduleSystemLoaded = listOf(VCS_IMPL_LABEL, VCS_DVCS_LABEL, VCS_LOG_LABEL),
        ),
        ProductModuleSystemLoading(
          product = "Other",
          moduleSets = listOf(VCS_SET),
          moduleSystemLoaded = listOf(VCS_IMPL_LABEL, VCS_LOG_LABEL, DIRECT_LABEL),
        ),
      ),
    ).associateBy { it.name }

    assertThat(table.getValue(VCS_SET).moduleSystemLoaded).containsExactly(VCS_IMPL)
    // The nested set is reached through the top-level set.
    assertThat(table.getValue(VCS_LOG_SET).moduleSystemLoaded).containsExactly(VCS_LOG)

    assertThat(shareModuleSystemLoadedLabels(
      product = "idea",
      moduleSystemLoaded = listOf(VCS_IMPL_LABEL, VCS_DVCS_LABEL, VCS_LOG_LABEL),
      moduleSets = listOf(VCS_SET),
      table = table,
    )).containsExactly(VCS_DVCS_LABEL)
    assertThat(shareModuleSystemLoadedLabels(
      product = "Other",
      moduleSystemLoaded = listOf(VCS_IMPL_LABEL, VCS_LOG_LABEL, DIRECT_LABEL),
      moduleSets = listOf(VCS_SET),
      table = table,
    )).containsExactly(DIRECT_LABEL)
  }

  @Test
  fun `a set that only one product reaches takes the loaded members of that product`() {
    val table = moduleSetModuleSystemLoaded(
      table = TABLE.values.toList(),
      payloads = listOf(
        ProductModuleSystemLoading(product = "idea", moduleSets = listOf(VCS_SET), moduleSystemLoaded = listOf(VCS_DVCS_LABEL)),
        ProductModuleSystemLoading(product = "Other", moduleSets = listOf(VCS_LOG_SET), moduleSystemLoaded = listOf(VCS_LOG_LABEL)),
      ),
    ).associateBy { it.name }

    assertThat(table.getValue(VCS_SET).moduleSystemLoaded).containsExactly(VCS_DVCS)
    // `idea` reaches the nested set too, and its module system does not load the member.
    assertThat(table.getValue(VCS_LOG_SET).moduleSystemLoaded).isEmpty()
  }

  @Test
  fun `a product whose module system does not load a loaded set member fails`() {
    val table = TABLE.mapValues { (name, moduleSet) ->
      if (name == VCS_LOG_SET) moduleSet.copy(moduleSystemLoaded = listOf(VCS_LOG)) else moduleSet
    }

    assertThatThrownBy {
      shareModuleSystemLoadedLabels(
        product = "Other",
        moduleSystemLoaded = listOf(VCS_IMPL_LABEL),
        moduleSets = listOf(VCS_SET),
        table = table,
      )
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("'Other'")
      .hasMessageContaining(VCS_LOG)
  }
}
