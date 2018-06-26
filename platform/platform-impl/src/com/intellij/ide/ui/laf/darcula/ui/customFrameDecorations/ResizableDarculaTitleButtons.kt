// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations.style.Properties
import com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations.style.StyleManager
import javax.accessibility.AccessibleContext
import javax.swing.Action
import javax.swing.Icon
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

  private val myRestoreButton: JButton = createButton("Restore", myRestoreAction, AllIcons.Windows.Restore)
  private val myMaximizeButton: JButton = createButton("Maximize", myMaximizeAction, AllIcons.Windows.Maximize)
  private val myMinimizeButton: JButton = createButton("Iconify", myIconifyAction, AllIcons.Windows.Minimize)

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

  private fun createButton(accessibleName : String, action : Action, icon : Icon) : JButton {
    val button = Properties.BasicButton()
    button.action = action
    button.putClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY, accessibleName)

    StyleManager.applyStyle(button, getStyle(icon))
    return button
  }
}