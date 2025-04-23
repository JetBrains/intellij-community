// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework

import org.assertj.core.api.SoftAssertions
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency
import java.nio.file.Files
import java.nio.file.Path

/**
 * Utility for checking that private plugin modules are not bundled in the build.
 */
fun checkPrivatePluginModulesAreNotBundled(
  context: BuildContext,
  softly: SoftAssertions,
) {
  val privateModules = getPrivateModules(context)
  if (privateModules.isEmpty()) return

  val visited = mutableSetOf<JpsModule>()
  val bundledPrivateModules = context.productProperties.productLayout.bundledPluginModules.asSequence()
    .mapNotNull { context.findModule(it) }
    .flatMap { it.transitiveDependencies(visited) }
    .filter { privateModules.contains(it.name) }
    .toList()

  // Also check modules in pluginLayouts
  visited.clear()
  val pluginLayoutsPrivateModules = context.productProperties.productLayout.pluginLayouts.asSequence()
    .flatMap { layout -> layout.includedModules.asSequence().map { it.moduleName } }
    .mapNotNull { context.findModule(it) }
    .flatMap { it.transitiveDependencies(visited) }
    .filter { privateModules.contains(it.name) }
    .toList()

  softly.assertThat(bundledPrivateModules).`as`("No private modules should be bundled in bundledPluginModules").isEmpty()
  softly.assertThat(pluginLayoutsPrivateModules).`as`("No private modules should be bundled in pluginLayouts").isEmpty()
}

/**
 * Determines the list of private modules based on the project type.
 * - For an unknown project, returns an empty list
 * - For the ultimate project, reads from the private-plugin-modules.txt file
 */
private fun getPrivateModules(context: BuildContext): Set<String> {
  val projectHome = context.paths.projectHome
  val ultimateHome = BuildPaths.ULTIMATE_HOME

  return when (projectHome) {
    ultimateHome -> readPrivateModulesFromFile(ultimateHome) // Ultimate project
    else -> emptySet()
  }
}

private fun readPrivateModulesFromFile(projectHome: Path): Set<String> {
  val privateModulesFile = projectHome.resolve("build/private-plugin-modules.txt")
  if (!Files.exists(privateModulesFile)) {
    throw IllegalStateException("The private-plugin-modules.txt file is missing in the project root directory: $privateModulesFile")
  }

  return Files.readAllLines(privateModulesFile)
    .filter { it.isNotBlank() && !it.startsWith("#") }
    .map { it.trim() }
    .toSet()
}

private fun JpsModule.transitiveDependencies(visited: MutableSet<JpsModule>): Sequence<JpsModule> {
  if (!visited.add(this)) return emptySequence() // Module already visited
  return sequence {
    yield(this@transitiveDependencies)
    dependenciesList.dependencies.asSequence()
      .filterIsInstance<JpsModuleDependency>()
      .mapNotNull { it.module }
      .forEach { dependencyModule ->
        yieldAll(dependencyModule.transitiveDependencies(visited))
      }
  }
}
