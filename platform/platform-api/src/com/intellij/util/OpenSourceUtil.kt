// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Internal

package com.intellij.util

import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.IdeUiService
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus.Internal

internal fun openSourcesFrom(context: DataContext, requestFocus: Boolean) {
  val project = context.getData(CommonDataKeys.PROJECT) ?: return
  val asyncContext = IdeUiService.getInstance().createAsyncDataContext(context)
  val options = NavigationOptions.defaultOptions().requestFocus(requestFocus)
  runWithModalProgressBlocking(project, IdeBundle.message("progress.title.preparing.navigation")) {
    NavigationService.getInstance(project).navigate(asyncContext, options)
  }
}

internal fun navigate(project: Project, requestFocus: Boolean, tryNotToScroll: Boolean, navigatables: Iterable<Navigatable?>?): Boolean {
  if (navigatables == null) {
    return false
  }
  val filteredNavigatables = navigatables.filterNotNull()
  val options = NavigationOptions.defaultOptions().requestFocus(requestFocus).preserveCaret(tryNotToScroll)
  return runWithModalProgressBlocking(project, IdeBundle.message("progress.title.preparing.navigation")) {
    NavigationService.getInstance(project).navigate(filteredNavigatables, options)
  }
}

/**
 * Navigates to source of the specified navigatable.
 *
 * @param requestFocus   specifies whether a focus should be requested or not
 * @param tryNotToScroll specifies whether a corresponding editor should preserve its state if it is possible
 * @return `true` if navigation is done, `false` otherwise
 */
internal fun navigateToSource(project: Project, requestFocus: Boolean, tryNotToScroll: Boolean, navigatable: Navigatable): Boolean {
  val options = NavigationOptions.defaultOptions().requestFocus(requestFocus).preserveCaret(tryNotToScroll)
  return runWithModalProgressBlocking(project, IdeBundle.message("progress.title.preparing.navigation")) {
    NavigationService.getInstance(project).navigate(navigatable, options)
  }
}

internal fun findProject(navigatables: Iterable<Navigatable>): Project? {
  return navigatables.firstNotNullOfOrNull {
    findProject(it)
  }
}

internal fun findProject(navigatable: Navigatable): Project? {
  return when (navigatable) {
    is PsiElement -> navigatable.project
    is OpenFileDescriptor -> navigatable.project
    is NodeDescriptor<*> -> navigatable.project
    else -> null
  }
}
