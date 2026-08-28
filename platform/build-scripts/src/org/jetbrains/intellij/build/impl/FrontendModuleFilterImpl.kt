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

internal object EmptyFrontendModuleFilter : FrontendModuleFilter {
  override fun isModuleCompatibleWithFrontend(moduleName: String): Boolean = false
}

/**
 * The filter a product with an embedded frontend root module gets, over the JPS project alone.
 *
 * Public because a second caller needs the same answer outside an assembly: the dev-distribution descriptor plan
 * precomputes `separate-jar` per (plugin, content module), and a packed dev distribution keeps
 * [org.jetbrains.intellij.build.BuildOptions.enableEmbeddedFrontend] at its default, so it gets this filter and not
 * [EmptyFrontendModuleFilter].
 */
fun createFrontendModuleFilter(project: JpsProject): FrontendModuleFilter = FrontendModuleFilterImpl.createFrontendModuleFilter(project)

/** The filter a product without an embedded frontend root module gets: nothing is frontend-compatible. */
fun emptyFrontendModuleFilter(): FrontendModuleFilter = EmptyFrontendModuleFilter
