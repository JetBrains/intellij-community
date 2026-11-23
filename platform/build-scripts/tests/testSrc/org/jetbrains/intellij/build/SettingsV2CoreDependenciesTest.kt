// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.platform.buildScripts.pluginModelTool.discoverCommunityModuleSetSources
import com.intellij.platform.buildScripts.pluginModelTool.loadGeneratorJpsProject
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.impl.PLATFORM_FIXED_JAR_MODULES
import org.jetbrains.intellij.build.productLayout.CoreModuleSets
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

private const val SETTINGS_API_MODULE = "intellij.platform.settings.v2"
private const val SETTINGS_VAULT_MODULE = "intellij.platform.settings.v2.shared"

private val communityProject: JpsProject by lazy { loadGeneratorJpsProject(BuildPaths.COMMUNITY_ROOT.communityRoot) }

private val communityModuleSets: List<ModuleSet> by lazy { discoverCommunityModuleSetSources().values.flatMap { it.moduleSets } }

/**
 * Checks that the settings v2 API module and the settings vault module depend only on core modules.
 *
 * A module of [CoreModuleSets.corePlatform] must be able to depend on the settings engine. A product that has only
 * [CoreModuleSets.corePlatform] must load the engine. Thus, each production module dependency of a settings module
 * must be a module of [CoreModuleSets.corePlatform] or of its nested module sets. A module of the fixed boot jars
 * ([PLATFORM_FIXED_JAR_MODULES]) is not a content module, and every product has it, so it is also allowed. A settings
 * module may also depend on the other settings module of this check.
 *
 * The dependencies come from the `*.iml` files of the community project.
 */
class SettingsV2CoreDependenciesTest {
  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = [SETTINGS_API_MODULE, SETTINGS_VAULT_MODULE])
  fun `settings module depends only on core modules`(settingsModuleName: String) {
    val corePlatform = CoreModuleSets.corePlatform()
    val allowedModuleNames = collectModuleNames(corePlatform) +
                             PLATFORM_FIXED_JAR_MODULES.values.flatten() +
                             SETTINGS_API_MODULE + SETTINGS_VAULT_MODULE
    val settingsModule = communityProject.modules.single { it.name == settingsModuleName }

    val disallowedDependencies = collectProductionModuleDependencyNames(settingsModule)
      .filter { it !in allowedModuleNames }
      .map { "$it (${findOwningModuleSetNames(it)})" }

    assertThat(disallowedDependencies)
      .describedAs("the dependencies of $settingsModuleName outside ${corePlatform.name} and the boot jars")
      .isEmpty()
  }
}

private fun collectModuleNames(moduleSet: ModuleSet): Set<String> {
  return moduleSet.modules.mapTo(HashSet()) { it.moduleId.name } + moduleSet.nestedSets.flatMap(::collectModuleNames)
}

private fun collectProductionModuleDependencyNames(module: JpsModule): List<String> {
  val javaExtensionService = JpsJavaExtensionService.getInstance()
  return module.dependenciesList.dependencies
    .filterIsInstance<JpsModuleDependency>()
    .filter { javaExtensionService.getDependencyExtension(it)?.scope != JpsJavaDependencyScope.TEST }
    .map { it.moduleReference.moduleName }
}

private fun findOwningModuleSetNames(moduleName: String): String {
  return communityModuleSets
    .filter { moduleSet -> moduleSet.modules.any { it.moduleId.name == moduleName } }
    .joinToString { it.name }
    .ifEmpty { "no community module set" }
}
