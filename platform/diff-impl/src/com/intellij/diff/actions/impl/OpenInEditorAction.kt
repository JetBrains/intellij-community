// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.actions.impl

import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUtil
import com.intellij.ide.actions.EditSourceAction
import com.intellij.ide.util.EditSourceUtil
import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil.copyFrom
import com.intellij.openapi.fileEditor.FileNavigator
import com.intellij.openapi.fileEditor.FileNavigator.Companion.getInstance
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement

open class OpenInEditorAction : EditSourceAction(), DumbAware, ActionPromoter {
  init {
    copyFrom(this, "EditSource")
    isEnabledInModalContext = true
  }

  override fun update(e: AnActionEvent) {
    if (isManuallyHidden(e.dataContext)) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val navigatables = e.getData(DiffDataKeys.NAVIGATABLE_ARRAY)
    if (e.project == null || navigatables == null || !navigatables.any(Navigatable::canNavigate)) {
      e.presentation.isVisible = true
      e.presentation.isEnabled = false
      return
    }

    e.presentation.isEnabledAndVisible = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    if (isManuallyHidden(e.dataContext)) return

    val project = e.project ?: return

    val callback = e.getData(DiffDataKeys.NAVIGATION_CALLBACK)
    val navigatables = e.getData(DiffDataKeys.NAVIGATABLE_ARRAY) ?: return

    openEditor(project, navigatables, callback)
  }

  final override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction>? {
    if (isManuallyHidden(context)) return null
    if (context.getData(DiffDataKeys.NAVIGATABLE_ARRAY) != null) {
      return listOf(this)
    }
    return null
  }

  final override fun suppress(actions: List<AnAction>, context: DataContext): List<AnAction>? {
    return if (context.getData(DiffDataKeys.DIFF_CONTEXT) != null) {
      actions.filterNot { it === this }
    }
    else {
      super.suppress(actions, context)
    }
  }

  companion object {
    @JvmStatic
    @Deprecated("Use openEditor(navigatable, callback)")
    fun openEditor(project: Project, navigatable: Navigatable, callback: Runnable?): Boolean = openEditor(navigatable, callback)

    @JvmStatic
    fun openEditor(navigatable: Navigatable, callback: Runnable?): Boolean = openEditor(arrayOf(navigatable), callback)

    @JvmStatic
    @Deprecated("Use openEditor(navigatables, callback)")
    fun openEditor(project: Project, navigatables: Array<Navigatable>, callback: Runnable?): Boolean = openEditor(navigatables, callback)

    /**
     * Performs navigation ignoring [OpenFileDescriptor.NAVIGATE_IN_EDITOR]
     */
    @JvmStatic
    fun openEditor(navigatables: Array<Navigatable>, callback: Runnable?): Boolean {
      val fileNavigator = getInstance()
      var success = false
      for (navigatable in navigatables) {
        success = success or navigate(fileNavigator, navigatable)
      }
      if (success && callback != null) {
        callback.run()
      }
      return success
    }
  }
}

private fun isManuallyHidden(dataContext: DataContext): Boolean {
  val request = dataContext.getData(DiffDataKeys.DIFF_REQUEST)
  val context = dataContext.getData(DiffDataKeys.DIFF_CONTEXT)
  return DiffUtil.isUserDataFlagSet(DiffUserDataKeys.GO_TO_SOURCE_DISABLE, request, context)
}

/**
 * [OpenFileDescriptor.NAVIGATE_IN_EDITOR] of the diff viewer belongs to the viewer, which uses it to keep navigation
 * inside its own tab, and must not be reused for the source target: the whole point of this action is to leave the diff.
 */
private fun navigate(fileNavigator: FileNavigator, navigatable: Navigatable): Boolean {
  if (!navigatable.canNavigate()) {
    return false
  }

  val descriptor = when (navigatable) {
    is OpenFileDescriptor -> navigatable
    is PsiElement -> EditSourceUtil.getDescriptor(navigatable) as? OpenFileDescriptor
    else -> null
  }
  if (descriptor != null) {
    fileNavigator.navigate(descriptor, requestFocus = true, requestedEditor = null)
  }
  else {
    // an opaque legacy navigatable navigates by itself, so there is no channel to state the policy
    navigatable.navigate(true)
  }
  return true
}
