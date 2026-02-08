// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Unit tests for [buildContentBlocksAndChainMapping] in ContentBlockBuilder.kt.
 * Tests content block construction, chain mapping, and alias collection.
 */
@ExtendWith(TestFailureLogger::class)
class ContentBlockBuilderTest {
  @Nested
  inner class ContentBlocksTest {
    @Test
    fun `empty spec produces empty content blocks`() {
      val spec = productModules { }
      
      val result = buildContentBlocksAndChainMapping(spec)
      
      assertThat(result.contentBlocks).isEmpty()
      assertThat(result.moduleToSetChainMapping).isEmpty()
    }

    @Test
    fun `single module set produces single content block`() {
      val moduleSet = ModuleSet(
        name = "test",
        modules = listOf(ContentModule(ContentModuleName("module.a")), ContentModule(ContentModuleName("module.b")))
      )
      val spec = productModules { moduleSet(moduleSet) }
      
      val result = buildContentBlocksAndChainMapping(spec)
      
      assertThat(result.contentBlocks).hasSize(1)
      assertThat(result.contentBlocks[0].source).isEqualTo("test")
      assertThat(result.contentBlocks[0].modules.map { it.name.value }).containsExactly("module.a", "module.b")
    }

    @Test
    fun `nested module sets produce separate content blocks`() {
      val nestedSet = ModuleSet(
        name = "nested",
        modules = listOf(ContentModule(ContentModuleName("nested.module")))
      )
      val parentSet = ModuleSet(
        name = "parent",
        modules = listOf(ContentModule(ContentModuleName("parent.module"))),
        nestedSets = listOf(nestedSet)
      )
      val spec = productModules { moduleSet(parentSet) }
      
      val result = buildContentBlocksAndChainMapping(spec)
      
      assertThat(result.contentBlocks).hasSize(2)
      assertThat(result.contentBlocks.map { it.source }).containsExactly("parent", "nested")
    }

    @Test
    fun `additional modules produce separate content block`() {
      val moduleSet = ModuleSet(
        name = "test",
        modules = listOf(ContentModule(ContentModuleName("set.module")))
      )
      val spec = productModules {
        moduleSet(moduleSet)
        module("additional.module")
      }
      
      val result = buildContentBlocksAndChainMapping(spec)
      
      assertThat(result.contentBlocks).hasSize(2)
      assertThat(result.contentBlocks.map { it.source }).contains("test", ADDITIONAL_MODULES_BLOCK)
      
      val additionalBlock = result.contentBlocks.find { it.source == ADDITIONAL_MODULES_BLOCK }
      assertThat(additionalBlock?.modules?.map { it.name.value }).containsExactly("additional.module")
    }
  }

  @Nested
  inner class LoadingOverridesTest {
    @Test
    fun `loading override is applied to module`() {
      val moduleSet = ModuleSet(
        name = "test",
        modules = listOf(ContentModule(ContentModuleName("module.a")), ContentModule(ContentModuleName("module.b")))
      )
      val spec = productModules {
        moduleSet(moduleSet) {
          overrideAsEmbedded("module.a")
        }
      }
      
      val result = buildContentBlocksAndChainMapping(spec)
      
      val block = result.contentBlocks[0]
      val moduleA = block.modules.find { it.name.value == "module.a" }
      val moduleB = block.modules.find { it.name.value == "module.b" }
      
      assertThat(moduleA?.loading).isEqualTo(ModuleLoadingRuleValue.EMBEDDED)
      assertThat(moduleB?.loading).isEqualTo(ModuleLoadingRuleValue.OPTIONAL)
    }

    @Test
    fun `multiple overrides are applied`() {
      val moduleSet = ModuleSet(
        name = "test",
        modules = listOf(
          ContentModule(ContentModuleName("module.a")),
          ContentModule(ContentModuleName("module.b")),
          ContentModule(ContentModuleName("module.c"))
        )
      )
      val spec = productModules {
        moduleSet(moduleSet) {
          overrideAsEmbedded("module.a")
          overrideAsEmbedded("module.b")
        }
      }
      
      val result = buildContentBlocksAndChainMapping(spec)
      
      val block = result.contentBlocks[0]
      assertThat(block.modules.filter { it.loading == ModuleLoadingRuleValue.EMBEDDED }.map { it.name.value })
        .containsExactlyInAnyOrder("module.a", "module.b")
    }

    @Test
    fun `overrides apply only to first occurrence of module set`() {
      // When a module set is referenced multiple times, first with overrides and then nested,
      // the overrides from the first reference should be used
      val sharedSet = ModuleSet(
        name = "shared",
        modules = listOf(ContentModule(ContentModuleName("shared.module")))
      )
      val parentSet = ModuleSet(
        name = "parent",
        modules = listOf(ContentModule(ContentModuleName("parent.module"))),
        nestedSets = listOf(sharedSet)
      )
      val spec = productModules {
        // First: reference shared set with overrides
        moduleSet(sharedSet) {
          overrideAsEmbedded("shared.module")
        }
        // Second: reference parent which nests shared set (no overrides)
        moduleSet(parentSet)
      }
      
      val result = buildContentBlocksAndChainMapping(spec)
      
      // shared.module should have embedded override from first reference
      val sharedBlock = result.contentBlocks.find { it.source == "shared" }
      assertThat(sharedBlock?.modules?.find { it.name.value == "shared.module" }?.loading)
        .isEqualTo(ModuleLoadingRuleValue.EMBEDDED)
    }
  }

  @Nested
  inner class ChainMappingTest {
    @Test
    fun `chain mapping tracks module to module set path`() {
      val moduleSet = ModuleSet(
        name = "test",
        modules = listOf(ContentModule(ContentModuleName("module.a")))
      )
      val spec = productModules { moduleSet(moduleSet) }
      
      val result = buildContentBlocksAndChainMapping(spec)
      
      assertThat(result.moduleToSetChainMapping[ContentModuleName("module.a")])
        .containsExactly("$MODULE_SET_PREFIX" + "test")
    }

    @Test
    fun `chain mapping tracks nested path`() {
      val nestedSet = ModuleSet(
        name = "nested",
        modules = listOf(ContentModule(ContentModuleName("nested.module")))
      )
      val parentSet = ModuleSet(
        name = "parent",
        modules = listOf(ContentModule(ContentModuleName("parent.module"))),
        nestedSets = listOf(nestedSet)
      )
      val spec = productModules { moduleSet(parentSet) }
      
      val result = buildContentBlocksAndChainMapping(spec)
      
      assertThat(result.moduleToSetChainMapping[ContentModuleName("parent.module")])
        .containsExactly("$MODULE_SET_PREFIX" + "parent")
      assertThat(result.moduleToSetChainMapping[ContentModuleName("nested.module")])
        .containsExactly("$MODULE_SET_PREFIX" + "parent", "$MODULE_SET_PREFIX" + "nested")
    }

    @Test
    fun `additional modules are not in chain mapping`() {
      val spec = productModules {
        module("additional.module")
      }
      
      val result = buildContentBlocksAndChainMapping(spec)
      
      assertThat(result.moduleToSetChainMapping).doesNotContainKey(ContentModuleName("additional.module"))
    }
  }

  @Nested
  inner class AliasCollectionTest {
    @Test
    fun `alias is collected when collectModuleSetAliases is true`() {
      val moduleSet = ModuleSet(
        name = "test",
        modules = listOf(ContentModule(ContentModuleName("module.a"))),
        alias = PluginId("test.alias")
      )
      val spec = productModules { moduleSet(moduleSet) }
      
      val result = buildContentBlocksAndChainMapping(spec, collectModuleSetAliases = true)
      
      assertThat(result.aliasToSource).containsEntry("test.alias", "module set 'test'")
    }

    @Test
    fun `alias is not collected when collectModuleSetAliases is false`() {
      val moduleSet = ModuleSet(
        name = "test",
        modules = listOf(ContentModule(ContentModuleName("module.a"))),
        alias = PluginId("test.alias")
      )
      val spec = productModules { moduleSet(moduleSet) }
      
      val result = buildContentBlocksAndChainMapping(spec, collectModuleSetAliases = false)
      
      assertThat(result.aliasToSource).isEmpty()
    }

    @Test
    fun `multiple aliases from nested sets are collected`() {
      val nestedSet = ModuleSet(
        name = "nested",
        modules = listOf(ContentModule(ContentModuleName("nested.module"))),
        alias = PluginId("nested.alias")
      )
      val parentSet = ModuleSet(
        name = "parent",
        modules = listOf(ContentModule(ContentModuleName("parent.module"))),
        nestedSets = listOf(nestedSet),
        alias = PluginId("parent.alias")
      )
      val spec = productModules { moduleSet(parentSet) }
      
      val result = buildContentBlocksAndChainMapping(spec, collectModuleSetAliases = true)
      
      assertThat(result.aliasToSource)
        .containsEntry("parent.alias", "module set 'parent'")
        .containsEntry("nested.alias", "module set 'nested'")
    }
  }

  @Nested
  inner class IncludeDependenciesTest {
    @Test
    fun `includeDependencies flag is tracked`() {
      val moduleSet = ModuleSet(
        name = "test",
        modules = listOf(
          ContentModule(ContentModuleName("module.with.deps"), includeDependencies = true),
          ContentModule(ContentModuleName("module.no.deps"), includeDependencies = false)
        )
      )
      val spec = productModules { moduleSet(moduleSet) }
      
      val result = buildContentBlocksAndChainMapping(spec)
      
      assertThat(result.moduleToIncludeDependencies).containsEntry(ContentModuleName("module.with.deps"), true)
      assertThat(result.moduleToIncludeDependencies).doesNotContainKey(ContentModuleName("module.no.deps"))
    }
  }

  @Nested
  inner class DuplicateDetectionTest {
    @Test
    fun `duplicate modules in same set throws error`() {
      val moduleSet = ModuleSet(
        name = "test",
        modules = listOf(
          ContentModule(ContentModuleName("duplicate.module")),
          ContentModule(ContentModuleName("duplicate.module"))
        )
      )
      val spec = productModules { moduleSet(moduleSet) }
      
      assertThatThrownBy { buildContentBlocksAndChainMapping(spec) }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessageContaining("duplicate.module")
    }

    @Test
    fun `duplicate modules in different sets throws error`() {
      val set1 = ModuleSet(
        name = "set1",
        modules = listOf(ContentModule(ContentModuleName("duplicate.module")))
      )
      val set2 = ModuleSet(
        name = "set2",
        modules = listOf(ContentModule(ContentModuleName("duplicate.module")))
      )
      val spec = productModules {
        moduleSet(set1)
        moduleSet(set2)
      }
      
      assertThatThrownBy { buildContentBlocksAndChainMapping(spec) }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessageContaining("duplicate.module")
    }

    @Test
    fun `duplicate in additional modules and module set throws error`() {
      val moduleSet = ModuleSet(
        name = "test",
        modules = listOf(ContentModule(ContentModuleName("duplicate.module")))
      )
      val spec = productModules {
        moduleSet(moduleSet)
        module("duplicate.module")
      }
      
      assertThatThrownBy { buildContentBlocksAndChainMapping(spec) }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessageContaining("duplicate.module")
    }
  }
}
