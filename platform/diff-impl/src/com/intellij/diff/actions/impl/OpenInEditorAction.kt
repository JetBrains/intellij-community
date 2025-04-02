// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.actions.impl

import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUtil
import com.intellij.ide.actions.EditSourceAction
import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil.copyFrom
import com.intellij.openapi.fileEditor.FileNavigator.Companion.getInstance
import com.intellij.openapi.fileEditor.FileNavigatorImpl
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable

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

  override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction>? {
    if (isManuallyHidden(context)) return null
    if (context.getData(DiffDataKeys.NAVIGATABLE_ARRAY) != null) {
      return listOf(this)
    }
    return null
  }

  companion object {
    @JvmStatic
    fun openEditor(project: Project, navigatable: Navigatable, callback: Runnable?): Boolean {
      return openEditor(project, arrayOf(navigatable), callback)
    }

    @JvmStatic
    fun openEditor(project: Project, navigatables: Array<Navigatable>, callback: Runnable?): Boolean {
      val fileNavigator = getInstance() as FileNavigatorImpl
      var success = false
      for (navigatable in navigatables) {
        success = success or fileNavigator.navigateIgnoringContextEditor(navigatable)
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
