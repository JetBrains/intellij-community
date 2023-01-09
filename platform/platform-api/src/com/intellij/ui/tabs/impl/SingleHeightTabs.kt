// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.tabs.TabInfo
import com.intellij.util.ui.JBUI
import java.awt.Dimension

open class SingleHeightTabs(project: Project?, focusManager: IdeFocusManager?, parent: Disposable) : JBEditorTabs(project, focusManager, parent) {
  companion object {
    const val UNSCALED_PREF_HEIGHT = 28
  }

  constructor(project: Project?, parent: Disposable) : this(project = project, focusManager = null, parent = parent)

  override fun createTabLabel(info: TabInfo): TabLabel = SingleHeightLabel(this, info)

  open inner class SingleHeightLabel(tabs: JBTabsImpl, info: TabInfo) : TabLabel(tabs, info) {
    override fun getPreferredSize(): Dimension {
      val size = super.getPreferredSize()
      return Dimension(size.width, getPreferredHeight())
    }

    protected open fun getPreferredHeight(): Int {
      return JBUI.scale(UNSCALED_PREF_HEIGHT)
    }
  }
}