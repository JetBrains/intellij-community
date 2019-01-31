// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations.style.StyleManager
import javax.swing.Action
import javax.swing.JButton

class ResizableDarculaTitleButtons(private val myCloseAction: Action,
                                          private val myRestoreAction: Action,
                                          private val myIconifyAction: Action,
                                          private val myMaximizeAction: Action,
                                          private val myHelpAction: HelpAction
) : DarculaTitleButtons(myCloseAction, myHelpAction) {
  companion object{
    fun create(myCloseAction: Action, myRestoreAction: Action, myIconifyAction: Action, myMaximizeAction: Action, myHelpAction: HelpAction) : ResizableDarculaTitleButtons {
      val darculaTitleButtons = ResizableDarculaTitleButtons(myCloseAction, myRestoreAction, myIconifyAction, myMaximizeAction, myHelpAction)
      darculaTitleButtons.createChildren()
      return darculaTitleButtons
    }
  }

  private val myRestoreButton: JButton = createButton("Restore", myRestoreAction)
  private val myMaximizeButton: JButton = createButton("Maximize", myMaximizeAction)
  private val myMinimizeButton: JButton = createButton("Iconify", myIconifyAction)

  override fun fillButtonPane() {
    super.fillButtonPane()
    addComponent(myMinimizeButton)
    addComponent(myMaximizeButton)
    addComponent(myRestoreButton)
  }

  override fun updateVisibility() {
    super.updateVisibility()
    myMinimizeButton.isVisible = myIconifyAction.isEnabled
    myRestoreButton.isVisible = myRestoreAction.isEnabled
    myMaximizeButton.isVisible = myMaximizeAction.isEnabled
  }

  override fun updateStyles() {
    super.updateStyles()
    StyleManager.applyStyle(myRestoreButton, getStyle(if(isSelected) AllIcons.Windows.Restore else AllIcons.Windows.RestoreInactive, AllIcons.Windows.Restore))
    StyleManager.applyStyle(myMaximizeButton, getStyle(if(isSelected) AllIcons.Windows.Maximize else AllIcons.Windows.MaximizeInactive, AllIcons.Windows.Maximize))
    StyleManager.applyStyle(myMinimizeButton, getStyle(if(isSelected) AllIcons.Windows.Minimize else AllIcons.Windows.MinimizeInactive, AllIcons.Windows.Minimize))
  }


}