// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.platform.runtime.product.ProductMode
import org.jetbrains.intellij.build.FrontendModuleFilter
import org.jetbrains.intellij.build.impl.moduleBased.JpsProductModeMatcher
import org.jetbrains.jps.model.JpsNamedElement
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.module.JpsModule

internal class FrontendModuleFilterImpl private constructor(
  private val project: JpsProject,
  private val frontendModeMatcher: JpsProductModeMatcher,
): FrontendModuleFilter {
  companion object {
    fun createFrontendModuleFilter(
      project: JpsProject,
    ): FrontendModuleFilter {
      return FrontendModuleFilterImpl(
        project = project,
        frontendModeMatcher = JpsProductModeMatcher(ProductMode.FRONTEND),
      )
    }
  }

  override fun isModuleCompatibleWithFrontend(moduleName: String): Boolean {
    val module = project.findModuleByName(moduleName)
    return module != null && frontendModeMatcher.matches(module)
  }
}

const val PLATFORM_MODULE_SCRAMBLED_WITH_FRONTEND: String = "intellij.platform.commercial.license"

val PROJECT_LIBRARIES_SCRAMBLED_WITH_FRONTEND: Set<String> = setOf(
  "LicenseServerAPI",
  "LicenseDecoder",
  "jetbrains.codeWithMe.lobby.server.api",
  "jetbrains.codeWithMe.lobby.server.common",
)

/**
 * Returns `true` if the module or library [element] from the platform part which are also included in the frontend JARs and scrambled (differently) there.
 * It's important not to include JARs for these modules and libraries in the platform part to the classpath of the frontend process, because they may cause clashes.
 */
fun isScrambledWithFrontend(element: JpsNamedElement): Boolean = when (element) {
  is JpsModule -> element.name == PLATFORM_MODULE_SCRAMBLED_WITH_FRONTEND
  is JpsLibrary -> element.name in PROJECT_LIBRARIES_SCRAMBLED_WITH_FRONTEND
  else -> false
}

internal object EmptyFrontendModuleFilter : FrontendModuleFilter {
  override fun isModuleCompatibleWithFrontend(moduleName: String): Boolean = false
}
