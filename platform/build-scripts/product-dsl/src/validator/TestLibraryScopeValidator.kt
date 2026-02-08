// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.ContentSourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.productLayout.LIB_MODULE_PREFIX
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.stats.SuppressionType
import org.jetbrains.intellij.build.productLayout.stats.SuppressionUsage
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModuleReference
import java.nio.file.Files

/**
 * Test library scope validation.
 *
 * Purpose: Ensure testing libraries are only in TEST scope for production content modules.
 * Inputs: plugin graph, JPS model, testingLibraries config, suppressions.
 * Output: `.iml` updates and `Slots.TEST_LIBRARY_SCOPE_SUPPRESSIONS`.
 * Auto-fix: yes.
 *
 * Glossary: docs/validators/README.md.
 * Spec: docs/validators/test-library-scope.md.
 */
internal object TestLibraryScopeValidator : PipelineNode {
  override val id get() = NodeIds.TEST_LIBRARY_SCOPE_VALIDATION
  override val produces: Set<DataSlot<*>> get() = setOf(Slots.TEST_LIBRARY_SCOPE_SUPPRESSIONS)

  override suspend fun execute(ctx: ComputeContext) {
    val model = ctx.model
    val graph = model.pluginGraph
    val outputProvider = model.outputProvider
    val javaExtensionService = JpsJavaExtensionService.getInstance()
    val testingLibraries = model.config.testingLibraries
    val allowedLibraries = model.config.testLibraryAllowedInModule
    val suppressionConfig = model.suppressionConfig
    val updateSuppressions = model.updateSuppressions

    if (testingLibraries.isEmpty()) {
      ctx.publish(Slots.TEST_LIBRARY_SCOPE_SUPPRESSIONS, emptyList())
      return
    }

    val violationsByModule = HashMap<ContentModuleName, MutableList<TestLibraryViolation>>()

    // Single-pass: iterate content modules directly (like LibraryModuleValidator)
    graph.query {
      contentModules { contentModule ->
        val moduleName = contentModule.name()
        val moduleNameValue = moduleName.value

        // Skip library wrapper modules
        if (moduleNameValue.startsWith(LIB_MODULE_PREFIX)) return@contentModules

        // Skip test framework modules
        if (isTestFrameworkModule(moduleNameValue)) return@contentModules

        // Check if module is in a test plugin using DSL
        var isTestPlugin = false
        contentModule.contentProductionSources { source ->
          if (source.kind == ContentSourceKind.PLUGIN && source.plugin().isTest) {
            isTestPlugin = true
          }
        }
        if (isTestPlugin) return@contentModules

        // Validate this content module
        val contentModuleName = moduleName
        val module = outputProvider.findModule(moduleNameValue) ?: return@contentModules

        // Skip modules without production sources
        if (!module.sourceRoots.any { !it.rootType.isForTests }) return@contentModules

        val allowedForModule = allowedLibraries.get(contentModuleName)

        for (dep in module.dependenciesList.dependencies) {
          if (dep !is JpsLibraryDependency) continue
          val libRef = dep.libraryReference
          if (libRef.parentReference is JpsModuleReference) continue

          val libName = libRef.libraryName
          if (libName !in testingLibraries) continue
          if (allowedForModule != null && libName in allowedForModule) continue

          val scope = javaExtensionService.getDependencyExtension(dep)?.scope ?: continue
          if (scope == JpsJavaDependencyScope.TEST) continue

          violationsByModule.computeIfAbsent(contentModuleName) { ArrayList() }
            .add(TestLibraryViolation(libName, scope))
        }
      }
    }

    // Apply fixes only for non-suppressed libraries
    // Track suppression usages for the unified suppression architecture
    val suppressionUsages = ArrayList<SuppressionUsage>()
    for ((contentModuleName, violations) in violationsByModule) {
      if (updateSuppressions) {
        for (violation in violations) {
          suppressionUsages.add(SuppressionUsage(contentModuleName, violation.libraryName, SuppressionType.TEST_LIBRARY_SCOPE))
        }
        continue
      }
      val suppressed = suppressionConfig.contentModules
        .get(contentModuleName)?.suppressTestLibraryScope ?: emptySet()
      val active = ArrayList<TestLibraryViolation>()
      for (violation in violations) {
        if (violation.libraryName in suppressed) {
          // Suppression found and applied - report it
          suppressionUsages.add(SuppressionUsage(contentModuleName, violation.libraryName, SuppressionType.TEST_LIBRARY_SCOPE))
        }
        else {
          active.add(violation)
        }
      }
      if (active.isNotEmpty()) {
        val module = outputProvider.findModule(contentModuleName.value) ?: continue
        val imlFile = outputProvider.getModuleImlFile(module)
        val current = withContext(Dispatchers.IO) {
          Files.readString(imlFile)
        }
        val fixed = applyTestLibraryScopeFixes(current, active)
        model.fileUpdater.writeIfChanged(imlFile, current, fixed)
      }
    }

    ctx.publish(Slots.TEST_LIBRARY_SCOPE_SUPPRESSIONS, suppressionUsages)
  }
}

/**
 * Determines if a module is a test framework module.
 * Test framework modules are allowed to have testing libraries in production scope.
 */
private fun isTestFrameworkModule(name: String): Boolean {
  return name.endsWith(".testFramework") ||
         name.contains(".testFramework.") ||
         name.endsWith("TestFramework") ||
         name.endsWith(".testGuiFramework")
}

/**
 * Represents a testing library dependency that should have 'test' scope.
 */
private data class TestLibraryViolation(
  @JvmField val libraryName: String,
  @JvmField val currentScope: JpsJavaDependencyScope,
)

/**
 * Applies fixes to .iml content by changing testing library dependencies to test scope.
 */
private fun applyTestLibraryScopeFixes(content: String, violations: List<TestLibraryViolation>): String {
  var result = content

  for (v in violations) {
    // Match orderEntry for this library and change scope to TEST
    // Handle various attribute orders and presence/absence of scope attribute
    val libName = Regex.escape(v.libraryName)

    // Pattern 1: Has scope attribute - replace it with TEST
    val withScopePattern = Regex(
      """(<orderEntry[^>]*type="library"[^>]*name="$libName"[^>]*)\s+scope="[^"]*"([^>]*/?>)"""
    )
    result = result.replace(withScopePattern) { match ->
      "${match.groupValues[1]} scope=\"TEST\"${match.groupValues[2]}"
    }

    // Pattern 2: No scope attribute (defaults to COMPILE) - add TEST scope
    // Only apply if previous pattern didn't match (no scope="TEST" already present)
    if (!result.contains(Regex("""<orderEntry[^>]*type="library"[^>]*name="$libName"[^>]*scope="TEST""""))) {
      val withoutScopePattern = Regex(
        """(<orderEntry[^>]*type="library"[^>]*name="$libName"[^>]*)\s*/>"""
      )
      result = result.replace(withoutScopePattern) { match ->
        "${match.groupValues[1]} scope=\"TEST\" />"
      }
    }
  }

  return result
}
