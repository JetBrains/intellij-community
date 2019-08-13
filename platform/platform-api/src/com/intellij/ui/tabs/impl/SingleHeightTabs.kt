// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.tabs.TabInfo
import com.intellij.util.ui.JBUI
import java.awt.Dimension

open class SingleHeightTabs(project: Project?,
                            actionManager: ActionManager,
                            focusManager: IdeFocusManager,
                            parent: Disposable) : JBEditorTabs(project, actionManager, focusManager, parent) {


  companion object {
    @JvmStatic
    val UNSCALED_PREF_HEIGHT = 28
  }

  override fun createTabLabel(info: TabInfo): TabLabel {
    return SingleHeightLabel(this, info)
  }

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