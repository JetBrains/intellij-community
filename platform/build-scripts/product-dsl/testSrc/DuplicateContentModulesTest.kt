// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Tests for duplicate content module detection in products and test plugins.
 * 
 * Duplicate content modules cause runtime errors:
 * "Plugin has duplicated content modules declarations"
 */
@ExtendWith(TestFailureLogger::class)
class DuplicateContentModulesTest {
  
  @Nested
  inner class ModuleSetDuplicatesTest {
    @Test
    fun `no duplicates when modules are unique across module sets`() {
      val moduleSet1 = createModuleSet("set1", listOf("module.a", "module.b"))
      val moduleSet2 = createModuleSet("set2", listOf("module.c", "module.d"))
      
      val spec = ProductModulesContentSpec(
        productModuleAliases = emptyList(),
        deprecatedXmlIncludes = emptyList(),
        moduleSets = listOf(
          ModuleSetWithOverrides(moduleSet1, emptyMap()),
          ModuleSetWithOverrides(moduleSet2, emptyMap()),
        ),
        additionalModules = emptyList(),
      )
      
      // Should not throw
      val result = buildContentBlocksAndChainMapping(spec)
      assertThat(result.contentBlocks).hasSize(2)
    }
    
    @Test
    fun `detects duplicates between module sets`() {
      val moduleSet1 = createModuleSet("set1", listOf("module.a", "module.duplicate"))
      val moduleSet2 = createModuleSet("set2", listOf("module.b", "module.duplicate"))
      
      val spec = ProductModulesContentSpec(
        productModuleAliases = emptyList(),
        deprecatedXmlIncludes = emptyList(),
        moduleSets = listOf(
          ModuleSetWithOverrides(moduleSet1, emptyMap()),
          ModuleSetWithOverrides(moduleSet2, emptyMap()),
        ),
        additionalModules = emptyList(),
      )
      
      assertThatThrownBy { buildContentBlocksAndChainMapping(spec) }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessageContaining("module.duplicate")
        .hasMessageContaining("Duplicate modules")
    }
  }
  
  @Nested
  inner class AdditionalModulesDuplicatesTest {
    @Test
    fun `detects duplicates between module set and additionalModules`() {
      val moduleSet = createModuleSet("testFrameworks", listOf(
        "intellij.platform.testFramework",
        "intellij.platform.testFramework.common",
      ))
      
      val spec = ProductModulesContentSpec(
        productModuleAliases = emptyList(),
        deprecatedXmlIncludes = emptyList(),
        moduleSets = listOf(ModuleSetWithOverrides(moduleSet, emptyMap())),
        additionalModules = listOf(
          // These duplicate what's in the module set
          ContentModule(ContentModuleName("intellij.platform.testFramework"), ModuleLoadingRuleValue.REQUIRED),
          ContentModule(ContentModuleName("intellij.platform.testFramework.common"), ModuleLoadingRuleValue.REQUIRED),
          // This is unique
          ContentModule(ContentModuleName("intellij.unique.module"), ModuleLoadingRuleValue.REQUIRED),
        ),
      )
      
      assertThatThrownBy { buildContentBlocksAndChainMapping(spec) }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessageContaining("intellij.platform.testFramework")
        .hasMessageContaining("Duplicate")
    }
    
    @Test
    fun `no error when additionalModules are unique`() {
      val moduleSet = createModuleSet("testFrameworks", listOf(
        "intellij.platform.testFramework",
      ))
      
      val spec = ProductModulesContentSpec(
        productModuleAliases = emptyList(),
        deprecatedXmlIncludes = emptyList(),
        moduleSets = listOf(ModuleSetWithOverrides(moduleSet, emptyMap())),
        additionalModules = listOf(
          ContentModule(ContentModuleName("intellij.unique.module"), ModuleLoadingRuleValue.REQUIRED),
        ),
      )
      
      // Should not throw
      val result = buildContentBlocksAndChainMapping(spec)
      assertThat(result.contentBlocks).hasSize(2) // module set + additional modules
    }
  }
  
  @Nested
  inner class TestPluginDuplicatesTest {
    @Test
    fun `test plugin with module set and duplicate additionalModules should fail`() {
      // Simulates the RiderProperties issue:
      // - moduleSet(platformTestFrameworksCore()) includes intellij.platform.testFramework
      // - requiredModule("intellij.platform.testFramework") adds the same module again
      
      val platformTestFrameworksCore = createModuleSet("platform.testFrameworks.core", listOf(
        "intellij.platform.testFramework",
        "intellij.platform.testFramework.common",
        "intellij.platform.testFramework.core",
        "intellij.platform.testFramework.impl",
        "intellij.platform.testFramework.teamCity",
      ))
      
      val testPluginSpec = ProductModulesContentSpec(
        productModuleAliases = emptyList(),
        deprecatedXmlIncludes = emptyList(),
        moduleSets = listOf(ModuleSetWithOverrides(platformTestFrameworksCore, emptyMap())),
        additionalModules = listOf(
          // These are duplicates - already in platformTestFrameworksCore
          ContentModule(ContentModuleName("intellij.platform.testFramework"), ModuleLoadingRuleValue.REQUIRED),
          ContentModule(ContentModuleName("intellij.platform.testFramework.common"), ModuleLoadingRuleValue.REQUIRED),
          ContentModule(ContentModuleName("intellij.platform.testFramework.core"), ModuleLoadingRuleValue.REQUIRED),
          ContentModule(ContentModuleName("intellij.platform.testFramework.impl"), ModuleLoadingRuleValue.REQUIRED),
          ContentModule(ContentModuleName("intellij.platform.testFramework.teamCity"), ModuleLoadingRuleValue.REQUIRED),
          // These are unique
          ContentModule(ContentModuleName("intellij.rider.test.cases"), ModuleLoadingRuleValue.REQUIRED),
        ),
      )
      
      assertThatThrownBy { buildContentBlocksAndChainMapping(testPluginSpec) }
        .isInstanceOf(IllegalStateException::class.java)
        .hasMessageContaining("intellij.platform.testFramework")
        .hasMessageContaining("Duplicate")
    }
  }
  
  private fun createModuleSet(name: String, moduleNames: List<String>): ModuleSet {
    return ModuleSet(
      name = name,
      modules = moduleNames.map { ContentModule(ContentModuleName(it)) },
      nestedSets = emptyList(),
      alias = null,
      selfContained = false,
    )
  }
}
