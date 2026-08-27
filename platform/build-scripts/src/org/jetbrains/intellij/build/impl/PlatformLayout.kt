// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.impl.PlatformJarNames.APP_BACKEND_JAR
import org.jetbrains.intellij.build.productLayout.LIB_MODULE_PREFIX

/**
 * Describes layout of the platform (*.jar files in IDE_HOME/lib directory).
 *
 * It includes all modules specified in [org.jetbrains.intellij.build.productLayout.ProductModulesLayout] and the module libraries they depend on.
 *
 * Project libraries are never added implicitly - only the ones declared by [BaseLayoutSpec.withProjectLibrary] are packed.
 * A project library referenced by a plugin module, but not provided by the platform, fails the build - it must be converted to a content module
 * (see IJPL-252372).
 */
class PlatformLayout(@JvmField val descriptorCacheContainer: DescriptorCacheContainer = DescriptorCacheContainer()) : BaseLayout() {
  internal var libAsProductModule: Set<String> = emptySet()

  private val excludedProjectLibraries: MutableSet<String> = HashSet()
  private val productModuleOutputFileOverrides: MutableMap<String, String> = HashMap()

  fun hasLibrary(name: String, moduleName: String): Boolean {
    return super.hasLibrary(name) || (!moduleName.startsWith(LIB_MODULE_PREFIX) && libAsProductModule.contains(name))
  }

  fun isProjectLibraryExcluded(name: String): Boolean = excludedProjectLibraries.contains(name)

  override fun getRelativeJarPath(moduleName: String): String = APP_BACKEND_JAR

  fun withProductModuleOutputFile(moduleName: String, relativeOutputFile: String) {
    require(!moduleName.isEmpty()) {
      "Module name must be not empty"
    }
    require(!relativeOutputFile.isEmpty()) {
      "Relative output file must be not empty"
    }
    require(!relativeOutputFile.startsWith("/") && relativeOutputFile.endsWith(".jar")) {
      "Relative output file for $moduleName must be a relative JAR path: $relativeOutputFile"
    }

    val previous = productModuleOutputFileOverrides.get(moduleName)
    check(previous == null || previous == relativeOutputFile) {
      "Product module output file for $moduleName is already set to $previous, cannot set to $relativeOutputFile"
    }
    productModuleOutputFileOverrides.put(moduleName, relativeOutputFile)
  }

  internal fun getProductModuleOutputFile(moduleName: String): String? = productModuleOutputFileOverrides.get(moduleName)

  fun withoutProjectLibrary(libraryName: String) {
    excludedProjectLibraries.add(libraryName)
  }
}
