// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.find.FindModel
import com.intellij.ide.IdeBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.PredefinedSearchScopeProviderImpl
import com.intellij.psi.search.SearchScope
import org.jetbrains.annotations.ApiStatus

/**
 * The fixed Find in Files scope of the non-modal welcome screen.
 *
 * The welcome-screen project has no source, so a project-wide search finds nothing and costs a full scan. The
 * recent-files scope holds the editor history plus the open files, which is the only useful set there.
 */
@ApiStatus.Internal
object WelcomeScreenFindScope {
  /** Tells if [project] takes the fixed welcome-screen scope instead of a scope the user chooses. */
  @JvmStatic
  fun isApplicable(project: Project): Boolean = WelcomeScreenProjectProvider.isWelcomeScreenProject(project)

  /** Writes the fixed scope into [model]. Call it only when [isApplicable] holds for [project]. */
  @JvmStatic
  fun applyTo(project: Project, model: FindModel) {
    val recentFiles = PredefinedSearchScopeProviderImpl.recentFilesScope(project, false)
    // An empty history gives LocalSearchScope.EMPTY, whose name is not the scope name. Keep the name, keep it empty.
    val scope: SearchScope =
      if (SearchScope.isEmptyScope(recentFiles)) {
        LocalSearchScope(PsiElement.EMPTY_ARRAY, IdeBundle.message("scope.recent.files"))
      }
      else {
        recentFiles
      }

    model.isProjectScope = false
    model.directoryName = null
    model.moduleName = null
    model.isCustomScope = true
    model.customScope = scope
    model.customScopeName = scope.displayName
  }
}
