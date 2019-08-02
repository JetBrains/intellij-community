// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.tabs.TabInfo
import java.awt.Dimension

open class SingleHeightTabs(project: Project?,
                            actionManager: ActionManager,
                            focusManager: IdeFocusManager,
                            parent: Disposable) : JBEditorTabs(project, actionManager, focusManager, parent) {

  override fun createTabLabel(info: TabInfo): TabLabel {
    return SingleHeightLabel(this, info)
  }

  open inner class SingleHeightLabel(tabs: JBTabsImpl, info: TabInfo) : TabLabel(tabs, info) {
    var height: Int? = null

    init {
      TabsHeightController.addListener({
        height = it
      }, this)
    }


    /**
     * TODO fix TabLabel and use setMinimumSize insted of this ugly huck
     */
    override fun getPreferredSize(): Dimension {
      val size = super.getPreferredSize()
      height ?: return size

      val insets = insets
      val layoutInsets = layoutInsets

      insets.top += layoutInsets.top
      insets.bottom += layoutInsets.bottom

      val newHeight = height!! - insets.top - insets.bottom
      return if(size.height >= newHeight) size else Dimension(size.width, newHeight)
    }

/*    private fun updateMinSize(height: Int) {
      val size = super.getMinimumSize()

      val insets = insets
      val layoutInsets = layoutInsets

      insets.top += layoutInsets.top
      insets.bottom += layoutInsets.bottom

      val newHeight = height - insets.top - insets.bottom
      if (size.height < newHeight) minimumSize = Dimension(size.width, newHeight)
    }*/
  }
}