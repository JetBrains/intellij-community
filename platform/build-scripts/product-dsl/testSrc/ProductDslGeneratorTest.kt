// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for generator.kt helper functions.
 */
class ProductDslGeneratorTest {
  // Test fixtures
  private fun createSimpleModuleSet(name: String, vararg moduleNames: String): ModuleSet {
    return ModuleSet(
      name = name,
      modules = moduleNames.map { ContentModule(it) }
    )
  }

  private fun createNestedModuleSet(
    name: String,
    moduleNames: List<String>,
    nestedSets: List<ModuleSet>,
  ): ModuleSet {
    return ModuleSet(
      name = name,
      modules = moduleNames.map { ContentModule(it) },
      nestedSets = nestedSets
    )
  }

  // Tests for containsOverriddenNestedSet()

  @Test
  fun `containsOverriddenNestedSet detects direct nested override`() {
    val overriddenSet = createSimpleModuleSet("overridden", "mod.a")
    val parentSet = createNestedModuleSet("parent", listOf("mod.b"), listOf(overriddenSet))

    val result = containsOverriddenNestedSet(parentSet, setOf(ModuleSetName("overridden")))

    assertThat(result).isTrue()
  }

  @Test
  fun `containsOverriddenNestedSet detects deeply nested override`() {
    val deeplyNested = createSimpleModuleSet("deep", "mod.a")
    val middleNested = createNestedModuleSet("middle", listOf("mod.b"), listOf(deeplyNested))
    val parentSet = createNestedModuleSet("parent", listOf("mod.c"), listOf(middleNested))

    val result = containsOverriddenNestedSet(parentSet, setOf(ModuleSetName("deep")))

    assertThat(result).isTrue()
  }

  @Test
  fun `containsOverriddenNestedSet returns false when no overrides`() {
    val nestedSet = createSimpleModuleSet("nested", "mod.a")
    val parentSet = createNestedModuleSet("parent", listOf("mod.b"), listOf(nestedSet))

    val result = containsOverriddenNestedSet(parentSet, setOf(ModuleSetName("someOtherSet")))

    assertThat(result).isFalse()
  }

  @Test
  fun `containsOverriddenNestedSet returns false for empty override set`() {
    val nestedSet = createSimpleModuleSet("nested", "mod.a")
    val parentSet = createNestedModuleSet("parent", listOf("mod.b"), listOf(nestedSet))

    val result = containsOverriddenNestedSet(parentSet, emptySet())

    assertThat(result).isFalse()
  }

  @Test
  fun `containsOverriddenNestedSet returns false for module set with no nested sets`() {
    val parentSet = createSimpleModuleSet("parent", "mod.a", "mod.b")

    val result = containsOverriddenNestedSet(parentSet, setOf(ModuleSetName("someSet")))

    assertThat(result).isFalse()
  }

  // Tests for findOverriddenNestedSetNames()

  @Test
  fun `findOverriddenNestedSetNames finds direct overridden set`() {
    val overridden1 = createSimpleModuleSet("overridden1", "mod.a")
    val overridden2 = createSimpleModuleSet("overridden2", "mod.b")
    val notOverridden = createSimpleModuleSet("notOverridden", "mod.c")
    val parentSet = createNestedModuleSet(
      "parent",
      listOf("mod.d"),
      listOf(overridden1, notOverridden, overridden2)
    )

    val result = findOverriddenNestedSetNames(parentSet, setOf(ModuleSetName("overridden1"), ModuleSetName("overridden2")))

    assertThat(result).containsExactlyInAnyOrder(ModuleSetName("overridden1"), ModuleSetName("overridden2"))
  }

  @Test
  fun `findOverriddenNestedSetNames finds all overridden sets recursively`() {
    val deepOverridden = createSimpleModuleSet("deepOverridden", "mod.a")
    val deepNormal = createSimpleModuleSet("deepNormal", "mod.b")
    val middleOverridden = createNestedModuleSet(
      "middleOverridden",
      listOf("mod.c"),
      listOf(deepOverridden, deepNormal)
    )
    val middleNormal = createSimpleModuleSet("middleNormal", "mod.d")
    val parentSet = createNestedModuleSet(
      "parent",
      listOf("mod.e"),
      listOf(middleOverridden, middleNormal)
    )

    val result = findOverriddenNestedSetNames(
      parentSet,
      setOf(ModuleSetName("middleOverridden"), ModuleSetName("deepOverridden"))
    )

    assertThat(result).containsExactlyInAnyOrder(ModuleSetName("middleOverridden"), ModuleSetName("deepOverridden"))
  }

  @Test
  fun `findOverriddenNestedSetNames returns empty for no overrides`() {
    val nestedSet = createSimpleModuleSet("nested", "mod.a")
    val parentSet = createNestedModuleSet("parent", listOf("mod.b"), listOf(nestedSet))

    val result = findOverriddenNestedSetNames(parentSet, setOf(ModuleSetName("someOtherSet")))

    assertThat(result).isEmpty()
  }

  @Test
  fun `findOverriddenNestedSetNames returns empty for empty override set`() {
    val nestedSet = createSimpleModuleSet("nested", "mod.a")
    val parentSet = createNestedModuleSet("parent", listOf("mod.b"), listOf(nestedSet))

    val result = findOverriddenNestedSetNames(parentSet, emptySet())

    assertThat(result).isEmpty()
  }

  @Test
  fun `findOverriddenNestedSetNames preserves order of discovery`() {
    val nested1 = createSimpleModuleSet("nested1", "mod.a")
    val nested2 = createSimpleModuleSet("nested2", "mod.b")
    val nested3 = createSimpleModuleSet("nested3", "mod.c")
    val parentSet = createNestedModuleSet(
      "parent",
      listOf("mod.d"),
      listOf(nested1, nested2, nested3)
    )

    val result = findOverriddenNestedSetNames(parentSet, setOf(ModuleSetName("nested1"), ModuleSetName("nested2"), ModuleSetName("nested3")))

    // Should be in order of traversal
    assertThat(result).containsExactly(ModuleSetName("nested1"), ModuleSetName("nested2"), ModuleSetName("nested3"))
  }
}
