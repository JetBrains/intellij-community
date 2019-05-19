// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.tabs.newImpl.JBEditorTabs
import com.intellij.ui.tabs.newImpl.JBTabsImpl

/**
 * @author yole
 */
object JBTabsFactory {
  @JvmStatic
  val useNewTabs = Registry.`is`("ide.new.tabs")

  @JvmStatic
  fun createTabs(project: Project): JBTabs {
    if (useNewTabs) {
      return JBTabsImpl(project)
    }
    return com.intellij.ui.tabs.impl.JBTabsImpl(project)
  }

  @JvmStatic
  fun createTabs(project: Project?, parentDisposable: Disposable): JBTabs {
    if (useNewTabs) {
      return JBTabsImpl(project, ActionManager.getInstance(), project?.let { IdeFocusManager.getInstance(it) }, parentDisposable)
    }
    return com.intellij.ui.tabs.impl.JBTabsImpl(project, ActionManager.getInstance(), project?.let { IdeFocusManager.getInstance(it) }, parentDisposable)
  }

  @JvmStatic
  fun createEditorTabs(project: Project?, parentDisposable: Disposable): JBEditorTabsBase {
    if (useNewTabs) {
      return JBEditorTabs(project, ActionManager.getInstance(), project?.let { IdeFocusManager.getInstance(it) }, parentDisposable)
    }
    return com.intellij.ui.tabs.impl.JBEditorTabs(project, ActionManager.getInstance(), project?.let { IdeFocusManager.getInstance(it) }, parentDisposable)
  }
}
