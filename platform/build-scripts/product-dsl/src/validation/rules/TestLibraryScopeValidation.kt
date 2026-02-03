// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productLayout.validation.rules

import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.productLayout.validation.FileChangeType
import org.jetbrains.intellij.build.productLayout.validation.FileDiff
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModuleReference
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import java.nio.file.Files

/**
 * Represents a testing library dependency that should have 'test' scope.
 */
private data class TestLibraryViolation(
  @JvmField val libraryName: String,
  @JvmField val currentScope: JpsJavaDependencyScope,
)

/**
 * Validates that modules included in product distributions don't have testing library
 * dependencies in production scope (COMPILE, RUNTIME, PROVIDED).
 *
 * Testing libraries (JUnit, assertJ, mockito, etc.) should have 'test' scope to avoid
 * being included in product distributions where they aren't needed.
 *
 * This validation checks modules that are in product distributions (from the product model)
 * and skips modules that are explicitly test frameworks or have production exceptions.
 *
 * Instead of returning errors, it returns diffs that can be applied to fix the `.iml` files.
 *
 * @param modulesToCheck Set of module names to validate (typically all modules in products)
 * @param testingLibraries Set of library names that are considered testing libraries
 * @param outputProvider Module output provider for accessing JPS modules
 * @return List of diffs for `.iml` files that need fixing
 */
internal fun validateTestLibraryScopes(
  modulesToCheck: Set<String>,
  testingLibraries: Set<String>,
  outputProvider: ModuleOutputProvider,
): List<FileDiff> {
  if (testingLibraries.isEmpty()) {
    return emptyList()
  }

  val javaExtensionService = JpsJavaExtensionService.getInstance()
  // Group violations by module name
  val violationsByModule = HashMap<String, MutableList<TestLibraryViolation>>()

  for (moduleName in modulesToCheck) {
    // Skip test framework modules - they're allowed to have testing libraries in production scope
    if (isTestFrameworkModule(moduleName)) {
      continue
    }

    // Skip modules that are explicitly allowed to have testing libraries in production scope
    if (isAllowedTestLibraryInProduction(moduleName)) {
      continue
    }

    val module = outputProvider.findModule(moduleName) ?: continue

    // Skip modules without production sources
    if (!module.sourceRoots.any { !it.rootType.isForTests }) {
      continue
    }

    val moduleDependencies = module.dependenciesList.dependencies

    for (dep in moduleDependencies) {
      if (dep !is JpsLibraryDependency) {
        continue
      }

      val libRef = dep.libraryReference

      // Skip module-level libraries (local to a module, not project libraries)
      if (libRef.parentReference is JpsModuleReference) {
        continue
      }

      val libName = libRef.libraryName
      if (libName !in testingLibraries) {
        continue
      }

      // Check the scope - only production scopes are violations
      val scope = javaExtensionService.getDependencyExtension(dep)?.scope ?: continue
      if (scope == JpsJavaDependencyScope.TEST) {
        continue
      }

      violationsByModule.computeIfAbsent(moduleName) { ArrayList() }.add(TestLibraryViolation(
        libraryName = libName,
        currentScope = scope,
      ))
    }
  }

  // Generate diffs for each module with violations
  val diffs = ArrayList<FileDiff>()
  for ((moduleName, violations) in violationsByModule) {
    val module = outputProvider.findModule(moduleName) ?: continue
    val imlDir = JpsModelSerializationDataService.getBaseDirectory(module)?.toPath() ?: continue
    val imlFile = imlDir.resolve("${module.name}.iml")

    if (!Files.exists(imlFile)) {
      continue
    }

    val currentContent = Files.readString(imlFile)
    val fixedContent = applyTestLibraryScopeFixes(currentContent, violations)

    if (fixedContent != currentContent) {
      val context = buildString {
        append("Module $moduleName has testing libraries in production scope:\n")
        for (v in violations) {
          append("  - ${v.libraryName} (${v.currentScope}) → should be TEST scope\n")
        }
        append("Run 'Generate Product Layouts' to fix automatically.")
      }
      diffs.add(FileDiff(
        context = context,
        path = imlFile,
        expectedContent = fixedContent,
        actualContent = currentContent,
        changeType = FileChangeType.MODIFY,
      ))
    }
  }

  return diffs
}

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

/**
 * Determines if a module is a test framework module (allowed to have testing libraries in production).
 */
private fun isTestFrameworkModule(moduleName: String): Boolean {
  return moduleName.endsWith(".testFramework") ||
         moduleName.contains(".testFramework.") ||
         moduleName.endsWith("TestFramework") ||
         moduleName.endsWith(".testGuiFramework")
}

/**
 * Determines if a module is explicitly allowed to have testing libraries in production scope.
 *
 * These are modules that:
 * - Provide testing utilities (RT modules, test runners)
 * - Are used by build scripts for testing
 * - Have other legitimate reasons for testing library access
 */
private fun isAllowedTestLibraryInProduction(moduleName: String): Boolean {
  return moduleName in ALLOWED_TEST_LIBRARY_MODULES
}

/**
 * Modules explicitly allowed to have testing libraries in production scope.
 * Matches the literal exceptions from the original test.
 */
private val ALLOWED_TEST_LIBRARY_MODULES = setOf(
  "intellij.tools.internalUtilities",
  "intellij.java.rt",
  "intellij.junit.rt",
  "intellij.junit.v5.rt",
  "intellij.junit.v6.rt",
  "intellij.java.coverage.rt",
  "intellij.testng",
  "intellij.testng.rt",
  "intellij.featuresTrainer",
  "intellij.platform.testExtensions",
  "intellij.tools.testsBootstrap",
  "intellij.idea.community.build.tests",
  "intellij.platform.vcs.tests",
  "intellij.cucumber.jvmFormatter",
  "fleet.plugins.uitests",
  "intellij.idea.community.build.tasks",
  "intellij.groovy.spock.rt",
  "intellij.tools.ide.starter.junit5",
  "intellij.database.util.test",
  "intellij.performanceTesting.remoteDriver",
  "intellij.tools.ide.starter.bus",
  "intellij.rider.test.framework",
  "intellij.rider.test.framework.core",
  "intellij.rider.test.framework.testng",
  "intellij.rider.test.framework.junit",
  "intellij.rider.test.framework.perforator",
  "intellij.rider.test.framework.unit",
  "intellij.rider.test.framework.integration.testng",
  "intellij.rider.test.framework.integration.junit",
  "intellij.ide.starter.extended",
  "intellij.ide.starter.extended.allure",
  "intellij.ide.starter",
  "intellij.tools.ide.starter.driver",
  "intellij.ide.starter.dockerized",
  "intellij.ml.llm.integration.testFramework",
)
