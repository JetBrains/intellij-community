// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.impl.PlatformJarNames.APP_JAR
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference

/**
 * Describes layout of the platform (*.jar files in IDE_HOME/lib directory).
 *
 * By default, it includes all modules specified in [org.jetbrains.intellij.build.ProductModulesLayout],
 * all libraries these modules depend on with scope 'Compile' or 'Runtime', and all project libraries from dependencies (with scope 'Compile'
 * or 'Runtime') of plugin modules for plugins which are [org.jetbrains.intellij.build.ProductModulesLayout.bundledPluginModules] bundled
 * (or prepared to be [org.jetbrains.intellij.build.ProductModulesLayout.pluginModulesToPublish] published) with the product (except
 * project libraries which are explicitly included into layouts of all plugins depending on them by [BaseLayoutSpec.withProjectLibrary]).
 */
class PlatformLayout: BaseLayout() {
  @Internal
  @JvmField
  val excludedProjectLibraries: MutableSet<String> = HashSet()

  fun withProjectLibrary(data: ProjectLibraryData) {
    includedProjectLibraries.add(data)
  }

  override fun withModule(moduleName: String) {
    withModule(moduleName, APP_JAR)
  }

  fun withoutProjectLibrary(libraryName: String) {
    excludedProjectLibraries.add(libraryName)
  }

  inline fun collectProjectLibrariesFromIncludedModules(context: BuildContext, consumer: (JpsLibrary, JpsModule) -> Unit) {
    val libsToUnpack = includedProjectLibraries.mapTo(HashSet()) { it.libraryName }
    for (moduleName in includedModules.asSequence().map { it.moduleName }.distinct()) {
      val module = context.findRequiredModule(moduleName)
      for (library in JpsJavaExtensionService.dependencies(module).includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME).libraries) {
        if (!isSkippedLibrary(library, libsToUnpack)) {
          consumer(library, module)
        }
      }
    }
  }

  fun isSkippedLibrary(library: JpsLibrary, libsToUnpack: Collection<String>): Boolean {
    return library.createReference().parentReference is JpsModuleReference ||
           libsToUnpack.contains(library.name) ||
           excludedProjectLibraries.contains(library.name)
  }
}