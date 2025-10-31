// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import org.jetbrains.annotations.TestOnly
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.impl.PlatformJarNames.APP_BACKEND_JAR
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference

/**
 * Describes layout of the platform (*.jar files in IDE_HOME/lib directory).
 *
 * By default, it includes all modules specified in [org.jetbrains.intellij.build.productLayout.ProductModulesLayout],
 * all libraries these modules depend on with scope 'Compile' or 'Runtime', and all project libraries from dependencies (with scope 'Compile'
 * or 'Runtime') of plugin modules for plugins which are [org.jetbrains.intellij.build.productLayout.ProductModulesLayout.bundledPluginModules] bundled
 * (or prepared to be [org.jetbrains.intellij.build.productLayout.ProductModulesLayout.pluginModulesToPublish] published) with the product (except
 * project libraries which are explicitly included in layouts of all plugins depending on them by [BaseLayoutSpec.withProjectLibrary]).
 */
class PlatformLayout(@JvmField val descriptorCacheContainer: DescriptorCacheContainer = DescriptorCacheContainer()) : BaseLayout() {
  internal var libAsProductModule: Set<String> = emptySet()

  private val projectLibraryToPolicy: MutableMap<String, ProjectLibraryPackagingPolicy> = HashMap()

  @get:TestOnly
  val excludedProjectLibraries: Sequence<String>
    get() = projectLibraryToPolicy.asSequence().filter { it.value == ProjectLibraryPackagingPolicy.EXCLUDE }.map { it.key }

  internal enum class ProjectLibraryPackagingPolicy {
    EXCLUDE,
    ALWAYS_PACK_TO_PLUGIN,
  }

  fun hasLibrary(name: String, moduleName: String): Boolean {
    return super.hasLibrary(name) || (!moduleName.startsWith(LIB_MODULE_PREFIX) && libAsProductModule.contains(name))
  }

  fun isProjectLibraryExcluded(name: String): Boolean = projectLibraryToPolicy.get(name) == ProjectLibraryPackagingPolicy.EXCLUDE

  internal fun alwaysPackToPlugin(names: List<String>) {
    for (name in names) {
      projectLibraryToPolicy.put(name, ProjectLibraryPackagingPolicy.ALWAYS_PACK_TO_PLUGIN)
    }
  }

  fun isLibraryAlwaysPackedIntoPlugin(name: String): Boolean = projectLibraryToPolicy.get(name) == ProjectLibraryPackagingPolicy.ALWAYS_PACK_TO_PLUGIN

  override fun getRelativeJarPath(moduleName: String): String = APP_BACKEND_JAR

  fun withoutProjectLibrary(libraryName: String) {
    projectLibraryToPolicy.put(libraryName, ProjectLibraryPackagingPolicy.EXCLUDE)
  }

  fun collectProjectLibrariesFromIncludedModules(context: BuildContext, consumer: (JpsLibrary, JpsModule) -> Unit) {
    val libsToUnpack = includedProjectLibraries.mapTo(LinkedHashSet(includedProjectLibraries.size)) { it.libraryName }
    val uniqueGuard = HashSet<String>()
    for (item in includedModules) {
      // libraries are packed into product module
      if (item.isProductModule()) {
        continue
      }

      val moduleName = item.moduleName
      if (!uniqueGuard.add(moduleName)) {
        continue
      }

      val module = context.findRequiredModule(moduleName)
      for (library in JpsJavaExtensionService.dependencies(module).includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME).libraries) {
        if (!isSkippedLibrary(library, libsToUnpack)) {
          consumer(library, module)
        }
      }
    }
  }

  private fun isSkippedLibrary(library: JpsLibrary, libsToUnpack: Collection<String>): Boolean {
    return library.createReference().parentReference is JpsModuleReference || libsToUnpack.contains(library.name) || isProjectLibraryExcluded(library.name)
  }
}