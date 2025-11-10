// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.jps.model.module.JpsModule
import org.junit.jupiter.api.Test
import java.nio.file.Path

class ProductModulesContentSpecTest {
  @Test
  fun `test valid overrides for existing modules`() {
    // Create a simple module set with some modules
    val moduleSet = ModuleSet(
      name = "testSet",
      modules = listOf(
        ContentModule("module.a"),
        ContentModule("module.b"),
        ContentModule("module.c")
      )
    )

    val spec = productModules {
      moduleSet(moduleSet) {
        overrideAsEmbedded("module.a")
        overrideAsEmbedded("module.b")
      }
    }

    // This should not throw - all overridden modules exist
    val result = buildProductContentXml(
      spec = spec,
      moduleOutputProvider = MockModuleOutputProvider(),
      inlineXmlIncludes = false,
      inlineModuleSets = true,
      productPropertiesClass = "TestProperties",
      generatorCommand = "test",
      isUltimateBuild = false
    )

    assertThat(result.xml).contains("module.a")
    assertThat(result.xml).contains("loading=\"embedded\"")
  }

  @Test
  fun `test invalid overrides for non-existent modules`() {
    val moduleSet = ModuleSet(
      name = "testSet",
      modules = listOf(
        ContentModule("module.a"),
        ContentModule("module.b")
      )
    )

    val spec = productModules {
      moduleSet(moduleSet) {
        overrideAsEmbedded("module.a")
        overrideAsEmbedded("module.nonexistent")
      }
    }

    // This should throw because module.nonexistent doesn't exist
    assertThatThrownBy {
      buildProductContentXml(
        spec = spec,
        moduleOutputProvider = MockModuleOutputProvider(),
        inlineXmlIncludes = false,
        inlineModuleSets = true,
        productPropertiesClass = "TestProperties",
        generatorCommand = "test",
        isUltimateBuild = false
      )
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("Invalid loading overrides for module set 'testSet'")
      .hasMessageContaining("module.nonexistent")
  }

  @Test
  fun `test cannot override modules from nested sets`() {
    val nestedSet = ModuleSet(
      name = "nested",
      modules = listOf(
        ContentModule("nested.module.a"),
        ContentModule("nested.module.b")
      )
    )

    val parentSet = ModuleSet(
      name = "parent",
      modules = listOf(
        ContentModule("parent.module.a")
      ),
      nestedSets = listOf(nestedSet)
    )

    val spec = productModules {
      moduleSet(parentSet) {
        // Trying to override nested module should fail
        overrideAsEmbedded("parent.module.a")
        overrideAsEmbedded("nested.module.a")
      }
    }

    // This should throw because nested.module.a is not a direct module of parent
    assertThatThrownBy {
      buildProductContentXml(
        spec = spec,
        moduleOutputProvider = MockModuleOutputProvider(),
        inlineXmlIncludes = false,
        inlineModuleSets = true,
        productPropertiesClass = "TestProperties",
        generatorCommand = "test",
        isUltimateBuild = false
      )
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("Invalid loading overrides for module set 'parent'")
      .hasMessageContaining("cannot override nested set modules")
      .hasMessageContaining("nested.module.a")
  }

  @Test
  fun `test empty overrides work fine`() {
    val moduleSet = ModuleSet(
      name = "testSet",
      modules = listOf(
        ContentModule("module.a")
      )
    )

    val spec = productModules {
      moduleSet(moduleSet)
    }

    // This should not throw
    val result = buildProductContentXml(
      spec = spec,
      moduleOutputProvider = MockModuleOutputProvider(),
      inlineXmlIncludes = false,
      inlineModuleSets = true,
      productPropertiesClass = "TestProperties",
      generatorCommand = "test",
      isUltimateBuild = false
    )

    assertThat(result.xml).contains("module.a")
  }

  @Test
  fun `test multiple invalid overrides are all reported`() {
    val moduleSet = ModuleSet(
      name = "testSet",
      modules = listOf(
        ContentModule("module.a")
      )
    )

    val spec = productModules {
      moduleSet(moduleSet) {
        overrideAsEmbedded("module.nonexistent1")
        overrideAsEmbedded("module.nonexistent2")
        overrideAsEmbedded("module.a")
      }
    }

    // Should report both nonexistent modules
    assertThatThrownBy {
      buildProductContentXml(
        spec = spec,
        moduleOutputProvider = MockModuleOutputProvider(),
        inlineXmlIncludes = false,
        inlineModuleSets = true,
        productPropertiesClass = "TestProperties",
        generatorCommand = "test",
        isUltimateBuild = false
      )
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("module.nonexistent1")
      .hasMessageContaining("module.nonexistent2")
  }

  @Test
  fun `test selective inlining with overrides generates correct XML`() {
    val nestedSet = ModuleSet(
      name = "nested",
      modules = listOf(
        ContentModule("nested.module.a"),
        ContentModule("nested.module.b")
      )
    )

    val parentSet = ModuleSet(
      name = "parent",
      modules = listOf(
        ContentModule("parent.module.a"),
        ContentModule("parent.module.b")
      ),
      nestedSets = listOf(nestedSet)
    )

    val spec = productModules {
      moduleSet(parentSet) {
        overrideAsEmbedded("parent.module.a")
      }
    }

    // Test with inlineModuleSets = false (selective inlining mode)
    val result = buildProductContentXml(
      spec = spec,
      moduleOutputProvider = MockModuleOutputProvider(),
      inlineXmlIncludes = false,
      inlineModuleSets = false,
      productPropertiesClass = "TestProperties",
      generatorCommand = "test",
      isUltimateBuild = false
    )

    // Should inline parent modules with loading attributes
    assertThat(result.xml).contains("parent.module.a")
    assertThat(result.xml).contains("loading=\"embedded\"")
    assertThat(result.xml).contains("parent.module.b")

    // Should generate xi:include for nested set
    assertThat(result.xml).contains("<xi:include href=\"/META-INF/intellij.moduleSets.nested.xml\"/>")
  }

  @Test
  fun `test loading attributes are correctly applied in inlined mode`() {
    val moduleSet = ModuleSet(
      name = "testSet",
      modules = listOf(
        ContentModule("module.a"),
        ContentModule("module.b"),
        ContentModule("module.c")
      )
    )

    val spec = productModules {
      moduleSet(moduleSet) {
        overrideAsEmbedded("module.a")
        overrideAsEmbedded("module.b")
      }
    }

    // Test with inlineModuleSets = true (full inlining mode)
    val result = buildProductContentXml(
      spec = spec,
      moduleOutputProvider = MockModuleOutputProvider(),
      inlineXmlIncludes = false,
      inlineModuleSets = true,
      productPropertiesClass = "TestProperties",
      generatorCommand = "test",
      isUltimateBuild = false
    )

    // Verify embedded modules have loading attribute
    assertThat(result.xml).containsPattern("<module name=\"module\\.a\" loading=\"embedded\"/>")
    assertThat(result.xml).containsPattern("<module name=\"module\\.b\" loading=\"embedded\"/>")

    // Verify non-overridden module does not have loading attribute
    assertThat(result.xml).containsPattern("<module name=\"module\\.c\"/>")
    assertThat(result.xml).doesNotContain("module.c\" loading")
  }

  @Test
  fun `test nested set override prevents duplicate module entries`() {
    // This test covers the real-world Rider scenario:
    // Parent set (commercialIdeBase) contains nested set (rdCommon)
    // Product also references rdCommon directly with overrides
    // Expected: rdCommon modules appear ONCE with overrides, not twice
    
    val deeplyNestedSet = ModuleSet(
      name = "rdCommon",
      modules = listOf(
        ContentModule("rd.module.a"),
        ContentModule("rd.module.b"),
        ContentModule("rd.module.c")
      )
    )

    val middleSet = ModuleSet(
      name = "ideUltimate",
      modules = listOf(
        ContentModule("ide.module.a")
      ),
      nestedSets = listOf(deeplyNestedSet)
    )

    val parentSet = ModuleSet(
      name = "commercialIdeBase",
      modules = listOf(
        ContentModule("commercial.module.a")
      ),
      nestedSets = listOf(middleSet)
    )

    val spec = productModules {
      // Include parent (which contains rdCommon nested deeply)
      moduleSet(parentSet)
      
      // Also reference rdCommon directly with overrides
      moduleSet(deeplyNestedSet) {
        overrideAsEmbedded("rd.module.a")
        overrideAsEmbedded("rd.module.b")
      }
    }

    val result = buildProductContentXml(
      spec = spec,
      moduleOutputProvider = MockModuleOutputProvider(),
      inlineXmlIncludes = false,
      inlineModuleSets = false,
      productPropertiesClass = "TestProperties",
      generatorCommand = "test",
      isUltimateBuild = false
    )

    // Verify rdCommon modules appear exactly ONCE with loading attributes
    // Use regex to count only actual module elements, not comments
    val rdModuleACount = Regex("""<module name="rd\.module\.a"""").findAll(result.xml).count()
    val rdModuleBCount = Regex("""<module name="rd\.module\.b"""").findAll(result.xml).count()
    assertThat(rdModuleACount).isEqualTo(1)
    assertThat(rdModuleBCount).isEqualTo(1)
    
    // Verify the modules have the embedded loading attribute
    assertThat(result.xml).contains("<module name=\"rd.module.a\" loading=\"embedded\"/>")
    assertThat(result.xml).contains("<module name=\"rd.module.b\" loading=\"embedded\"/>")
    
    // Verify parent and middle sets were selectively inlined (not xi:included)
    assertThat(result.xml).contains("commercial.module.a")
    assertThat(result.xml).contains("ide.module.a")
    
    // Verify no xi:include for sets containing overridden nested sets
    assertThat(result.xml).doesNotContain("<xi:include href=\"/META-INF/intellij.moduleSets.commercialIdeBase.xml\"/>")
    assertThat(result.xml).doesNotContain("<xi:include href=\"/META-INF/intellij.moduleSets.ideUltimate.xml\"/>")
  }

  @Test
  fun `test overrides preserved when module set nested and directly referenced with full inlining`() {
    // This test covers the Rider ClassNotFoundException bug:
    // When inlineModuleSets = true (full inlining mode), overrides are lost if:
    // - A module set is nested inside another set
    // - The same module set is also directly referenced with overrides
    // Bug: Only the first encounter's overrides are kept due to processedSets deduplication
    
    val rdCommon = ModuleSet(
      name = "rdCommon",
      modules = listOf(
        ContentModule("intellij.rd.platform"),
        ContentModule("intellij.rd.ui")
      )
    )

    val commercialIdeBase = ModuleSet(
      name = "commercialIdeBase",
      modules = listOf(
        ContentModule("commercial.module")
      ),
      nestedSets = listOf(rdCommon)
    )

    val spec = productModules {
      moduleSet(commercialIdeBase)  // Includes rdCommon nested (no overrides)
      moduleSet(rdCommon) {          // Also reference rdCommon with overrides
        overrideAsEmbedded("intellij.rd.platform")
        overrideAsEmbedded("intellij.rd.ui")
      }
    }

    // Test with inlineModuleSets = true (full inlining mode) - this is where the bug occurs
    val result = buildProductContentXml(
      spec = spec,
      moduleOutputProvider = MockModuleOutputProvider(),
      inlineXmlIncludes = false,
      inlineModuleSets = true,  // Full inlining mode
      productPropertiesClass = "TestProperties",
      generatorCommand = "test",
      isUltimateBuild = false
    )

    // Verify rd modules have loading="embedded" attribute
    assertThat(result.xml).contains("<module name=\"intellij.rd.platform\" loading=\"embedded\"/>")
    assertThat(result.xml).contains("<module name=\"intellij.rd.ui\" loading=\"embedded\"/>")
    
    // Verify they don't appear without loading attribute (bug would cause this)
    assertThat(result.xml).doesNotContainPattern("<module name=\"intellij\\.rd\\.platform\"\\s*/>")
    assertThat(result.xml).doesNotContainPattern("<module name=\"intellij\\.rd\\.ui\"\\s*/>")
    
    // Verify modules appear exactly once
    val rdPlatformCount = Regex("""<module name="intellij\.rd\.platform"""").findAll(result.xml).count()
    val rdUiCount = Regex("""<module name="intellij\.rd\.ui"""").findAll(result.xml).count()
    assertThat(rdPlatformCount).describedAs("rd.platform should appear exactly once").isEqualTo(1)
    assertThat(rdUiCount).describedAs("rd.ui should appear exactly once").isEqualTo(1)
  }
}

/**
 * Mock ModuleOutputProvider for testing that doesn't load modules.
 */
private class MockModuleOutputProvider : ModuleOutputProvider {
  override fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray? {
    TODO("Not yet implemented")
  }

  override fun findModule(name: String): JpsModule? {
    TODO("Not yet implemented")
  }

  override fun findRequiredModule(name: String): JpsModule {
    TODO("Not yet implemented")
  }

  override fun getModuleOutputRoots(module: JpsModule, forTests: Boolean): List<Path> {
    TODO("Not yet implemented")
  }
}