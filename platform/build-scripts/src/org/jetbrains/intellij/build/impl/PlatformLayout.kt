// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.impl.PlatformJarNames.APP_BACKEND_JAR

/**
 * Describes layout of the platform (*.jar files in IDE_HOME/lib directory).
 *
 * It includes all modules specified in [org.jetbrains.intellij.build.productLayout.ProductModulesLayout] and the module libraries they depend on.
 *
 * Project libraries are never added implicitly - only the ones declared by [BaseLayoutSpec.withProjectLibrary] are packed.
 * A project library that a plugin module references is not packed for the plugin; the platform or the plugin layout declares it.
 */
class PlatformLayout(@JvmField val descriptorCacheContainer: DescriptorCacheContainer = DescriptorCacheContainer()) : BaseLayout() {
  private val productModuleOutputFileOverrides: MutableMap<String, String> = HashMap()

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
}
