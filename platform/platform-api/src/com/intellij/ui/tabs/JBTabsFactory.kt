// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.tabs.impl.JBEditorTabs
import com.intellij.ui.tabs.impl.JBTabsImpl


object JBTabsFactory {
  @JvmStatic
  fun createTabs(project: Project): JBTabs {
    return JBTabsImpl(project)
  }

  @JvmStatic
  fun createTabs(project: Project?, parentDisposable: Disposable): JBTabs {
    return JBTabsImpl(project, project?.let { IdeFocusManager.getInstance(it) }, parentDisposable)
  }

  @JvmStatic
  fun createEditorTabs(project: Project?, parentDisposable: Disposable): JBEditorTabsBase {
    return JBEditorTabs(project, project?.let { IdeFocusManager.getInstance(it) }, parentDisposable)
  }
}
