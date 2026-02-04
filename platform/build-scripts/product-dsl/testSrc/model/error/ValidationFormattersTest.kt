// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.model.error

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.TargetName
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.model.ModuleSourceInfo
import org.jetbrains.intellij.build.productLayout.stats.AnsiStyle
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Path

// Helper functions to match old API in tests
private fun formatValidationError(error: ValidationError, useAnsi: Boolean): String = error.format(AnsiStyle(useAnsi))
private fun formatValidationErrors(errors: List<ValidationError>, useAnsi: Boolean): String = errors.joinToString("") { it.format(AnsiStyle(useAnsi)) }
private fun getErrorId(error: ValidationError): String = error.errorId()

/**
 * Unit tests for validation error IDs and formatting.
 */
@ExtendWith(TestFailureLogger::class)
class ValidationFormattersTest {
  @Nested
  inner class GetErrorIdTest {
    @Test
    fun `FileDiff error ID contains filename`() {
      val error = FileDiff(
        path = Path.of("/some/path/test-file.xml"),
        context = "diff content",
        expectedContent = "new content",
        actualContent = "old content",
        changeType = FileChangeType.MODIFY
      )
      
      assertThat(getErrorId(error)).isEqualTo("diff:test-file.xml")
    }
    
    @Test
    fun `XIncludeResolutionError error ID contains plugin name and path`() {
      val error = XIncludeResolutionError(
        pluginName = "my.plugin",
        xIncludePath = "/META-INF/included.xml",
        debugInfo = "debug info",
        context = "context"
      )
      
      assertThat(getErrorId(error)).isEqualTo("xinclude:my.plugin:/META-INF/included.xml")
    }
    
    @Test
    fun `MissingDependenciesError error ID contains context`() {
      val error = MissingDependenciesError(
        missingModules = mapOf(ContentModuleName("dep.a") to setOf(ContentModuleName("mod.a"))),
        moduleSourceInfo = emptyMap(),
        context = "ProductA"
      )
      
      assertThat(getErrorId(error)).isEqualTo("missing-deps:ProductA")
    }
    
    @Test
    fun `MissingModuleSetsError error ID contains context`() {
      val error = MissingModuleSetsError(
        missingModuleSets = setOf("missing.set"),
        context = "ProductB"
      )
      
      assertThat(getErrorId(error)).isEqualTo("missing-sets:ProductB")
    }
    
    @Test
    fun `DuplicateModulesError error ID contains context`() {
      val error = DuplicateModulesError(
        duplicates = mapOf(ContentModuleName("mod.a") to 2),
        context = "ProductC"
      )
      
      assertThat(getErrorId(error)).isEqualTo("duplicates:ProductC")
    }
    
    @Test
    fun `SelfContainedValidationError error ID contains context`() {
      val error = SelfContainedValidationError(
        missingDependencies = mapOf(ContentModuleName("dep.a") to setOf(ContentModuleName("mod.a"))),
        context = "self-contained-set"
      )
      
      assertThat(getErrorId(error)).isEqualTo("self-contained:self-contained-set")
    }
    
    @Test
    fun `PluginDependencyError error ID contains plugin name`() {
      val error = PluginDependencyError(
        pluginName = TargetName("com.example.plugin"),
        missingDependencies = mapOf(ContentModuleName("dep.a") to setOf(ContentModuleName("mod.a"))),
        moduleSourceInfo = emptyMap(),
        structuralViolations = emptyMap(),
        filteredDependencies = emptyMap(),
        unresolvedByProduct = emptyMap(),
        context = "context"
      )
      
      assertThat(getErrorId(error)).isEqualTo("plugin-dep:com.example.plugin")
    }
  }
  
  @Nested
  inner class FormatValidationErrorTest {
    @Test
    fun `FileDiff uses context as message`() {
      val error = FileDiff(
        path = Path.of("/some/path/test.xml"),
        context = "File content differs from expected",
        expectedContent = "new content",
        actualContent = "old content",
        changeType = FileChangeType.MODIFY
      )
      
      val result = formatValidationError(error, useAnsi = false)
      
      assertThat(result).isEqualTo("File content differs from expected")
    }
    
    @Test
    fun `XIncludeResolutionError formats with plugin name and path`() {
      val error = XIncludeResolutionError(
        pluginName = "my.plugin",
        xIncludePath = "/META-INF/included.xml",
        debugInfo = "File not found",
        context = "context"
      )
      
      val result = formatValidationError(error, useAnsi = false)
      
      assertThat(result)
        .contains("Failed to resolve xi:include in plugin my.plugin")
        .contains("Path: /META-INF/included.xml")
        .contains("Debug: File not found")
        .contains("skipXIncludePaths")
    }
    
    @Test
    fun `MissingModuleSetsError lists missing sets`() {
      val error = MissingModuleSetsError(
        missingModuleSets = setOf("set.a", "set.b"),
        context = "ProductA"
      )
      
      val result = formatValidationError(error, useAnsi = false)
      
      assertThat(result)
        .contains("Product 'ProductA' references non-existent module sets")
        .contains("Module set 'set.a' does not exist")
        .contains("Module set 'set.b' does not exist")
        .contains("Fix: Remove the reference or define the module set")
    }
    
    @Test
    fun `DuplicateModulesError lists duplicates with counts`() {
      val error = DuplicateModulesError(
        duplicates = mapOf(ContentModuleName("mod.a") to 2, ContentModuleName("mod.b") to 3),
        context = "ProductB"
      )
      
      val result = formatValidationError(error, useAnsi = false)
      
      assertThat(result)
        .contains("Product 'ProductB' has duplicate content modules")
        .contains("mod.a (appears 2 times)")
        .contains("mod.b (appears 3 times)")
        .contains("duplicated content modules declarations")
    }
    
    @Test
    fun `SelfContainedValidationError lists missing deps and needing modules`() {
      val error = SelfContainedValidationError(
        missingDependencies = mapOf(
          ContentModuleName("dep.a") to setOf(ContentModuleName("mod.1"), ContentModuleName("mod.2")),
          ContentModuleName("dep.b") to setOf(ContentModuleName("mod.3"))
        ),
        context = "my-set"
      )
      
      val result = formatValidationError(error, useAnsi = false)
      
      assertThat(result)
        .contains("Module set 'my-set' is marked selfContained but has unresolvable dependencies")
        .contains("Missing: dep.a")
        .contains("Missing: dep.b")
        .contains("Needed by: mod.1, mod.2")
        .contains("Needed by: mod.3")
        .contains("To fix:")
        .contains("selfContained=true")
    }
    
    @Test
    fun `PluginDependencyError formats structural violations first`() {
      val error = PluginDependencyError(
        pluginName = TargetName("com.example.plugin"),
        missingDependencies = emptyMap(),
        moduleSourceInfo = mapOf(
          ContentModuleName("mod.required") to ModuleSourceInfo(
            sourcePlugin = TargetName("com.example.plugin"),
            sourceModuleSet = null,
            loadingMode = ModuleLoadingRuleValue.REQUIRED,
            isTestPlugin = false,
            bundledInProducts = emptySet(),
            compatibleWithProducts = emptySet()
          ),
          ContentModuleName("mod.optional") to ModuleSourceInfo(
            sourcePlugin = TargetName("com.example.plugin"),
            sourceModuleSet = null,
            loadingMode = ModuleLoadingRuleValue.OPTIONAL,
            isTestPlugin = false,
            bundledInProducts = emptySet(),
            compatibleWithProducts = emptySet()
          )
        ),
        structuralViolations = mapOf(ContentModuleName("mod.required") to setOf(ContentModuleName("mod.optional"))),
        filteredDependencies = emptyMap(),
        unresolvedByProduct = emptyMap(),
        context = "context"
      )
      
      val result = formatValidationError(error, useAnsi = false)
      
      assertThat(result)
        .contains("STRUCTURAL VIOLATIONS (fix these first)")
        .contains("'mod.required' (required) depends on:")
        .contains("'mod.optional' (optional)")
        .contains("required cannot depend on optional sibling")
        .contains("Fix:")
        .contains("OPTIONAL/ON_DEMAND")
        .contains("REQUIRED/EMBEDDED")
    }
    
    @Test
    fun `PluginDependencyError formats missing dependencies`() {
      val error = PluginDependencyError(
        pluginName = TargetName("com.example.plugin"),
        missingDependencies = mapOf(ContentModuleName("missing.dep") to setOf(ContentModuleName("mod.a"))),
        moduleSourceInfo = mapOf(
          ContentModuleName("mod.a") to ModuleSourceInfo(
            sourcePlugin = TargetName("com.example.plugin"),
            sourceModuleSet = null,
            loadingMode = null,
            isTestPlugin = false,
            bundledInProducts = setOf("ProductA"),
            compatibleWithProducts = emptySet()
          )
        ),
        structuralViolations = emptyMap(),
        filteredDependencies = emptyMap(),
        unresolvedByProduct = emptyMap(),
        context = "context"
      )
      
      val result = formatValidationError(error, useAnsi = false)
      
      assertThat(result)
        .contains("Plugin 'com.example.plugin' has unresolvable content module dependencies")
        .contains("Missing: 'missing.dep'")
        .contains("mod.a")
        .contains("content module")
        .contains("in plugin: com.example.plugin")
        .contains("bundled in: ProductA")
    }

    @Test
    fun `PluginDependencyError includes proposed patches`() {
      val patch = listOf(
        "--- a/path.kt",
        "+++ b/path.kt",
        "@@ -1,1 +1,4 @@",
        " productModules {",
        "+  allowMissingDependencies(",
        "+    \"missing.dep\",",
        "+  )",
      ).joinToString("\n")

      val error = PluginDependencyError(
        pluginName = TargetName("com.example.plugin"),
        missingDependencies = mapOf(ContentModuleName("missing.dep") to setOf(ContentModuleName("mod.a"))),
        moduleSourceInfo = emptyMap(),
        unresolvedByProduct = mapOf("ProductA" to setOf(ContentModuleName("missing.dep"))),
        proposedPatches = listOf(
          ProposedPatch(
            title = "ProductA (path.kt)",
            patch = patch,
          ),
        ),
        context = "context",
      )

      val result = formatValidationError(error, useAnsi = false)

      assertThat(result)
        .contains("Proposed patch")
        .contains("Patch (ProductA")
        .contains("--- a/path.kt")
        .contains("allowMissingDependencies")
    }

    @Test
    fun `PluginDependencyError structural patches use loading hint`() {
      val patch = listOf(
        "--- a/path.xml",
        "+++ b/path.xml",
        "@@ -10,1 +10,1 @@",
        "-    <module name=\"mod.dep\"/>",
        "+    <module name=\"mod.dep\" loading=\"required\"/>",
      ).joinToString("\n")

      val error = PluginDependencyError(
        pluginName = TargetName("com.example.plugin"),
        missingDependencies = emptyMap(),
        moduleSourceInfo = emptyMap(),
        structuralViolations = mapOf(
          ContentModuleName("mod.a") to setOf(ContentModuleName("mod.dep"))
        ),
        filteredDependencies = emptyMap(),
        unresolvedByProduct = emptyMap(),
        proposedPatches = listOf(
          ProposedPatch(
            title = "com.example.plugin (path.xml)",
            patch = patch,
          ),
        ),
        context = "context",
      )

      val result = formatValidationError(error, useAnsi = false)

      assertThat(result)
        .contains("Proposed patch")
        .contains("Set loading=\"required\"")
        .contains("+++ b/path.xml")
    }
    
    @Test
    fun `formatValidationErrors combines multiple errors`() {
      val errors = listOf(
        MissingModuleSetsError(
          missingModuleSets = setOf("set.a"),
          context = "Product1"
        ),
        DuplicateModulesError(
          duplicates = mapOf(ContentModuleName("mod.a") to 2),
          context = "Product2"
        )
      )
      
      val result = formatValidationErrors(errors, useAnsi = false)
      
      assertThat(result)
        .contains("Product 'Product1' references non-existent module sets")
        .contains("Product 'Product2' has duplicate content modules")
    }
    
    @Test
    fun `formatValidationErrors returns empty string for empty list`() {
      val result = formatValidationErrors(emptyList(), useAnsi = false)
      
      assertThat(result).isEmpty()
    }
  }
  
  @Nested
  inner class AnsiSupportTest {
    @Test
    fun `ANSI codes are included when useAnsi is true`() {
      val error = MissingModuleSetsError(
        missingModuleSets = setOf("set.a"),
        context = "ProductA"
      )
      
      val result = formatValidationError(error, useAnsi = true)
      
      // ANSI escape codes start with \u001B[
      assertThat(result).contains("\u001B[")
    }

    @Test
    fun `ANSI codes are included for formatValidationErrors when useAnsi is true`() {
      val errors = listOf(
        MissingModuleSetsError(
          missingModuleSets = setOf("set.a"),
          context = "ProductA"
        )
      )

      val result = formatValidationErrors(errors, useAnsi = true)

      assertThat(result).contains("\u001B[")
    }
    
    @Test
    fun `ANSI codes are excluded when useAnsi is false`() {
      val error = MissingModuleSetsError(
        missingModuleSets = setOf("set.a"),
        context = "ProductA"
      )
      
      val result = formatValidationError(error, useAnsi = false)
      
      assertThat(result).doesNotContain("\u001B[")
    }
  }
}
