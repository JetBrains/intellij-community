// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContextMenuActionBase
import com.intellij.openapi.wm.impl.content.BaseLabel
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.Content
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import java.awt.Font
import java.awt.Point
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.Box
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

private const val OUTLINE_PROPERTY = "JComponent.outline"
private const val ERROR_VALUE = "error"


@Suppress("ComponentNotRegistered")
open class ToolWindowTabRenameActionBase(val toolWindowId: String, @NlsContexts.Label val labelText: String) : ToolWindowContextMenuActionBase() {
  override fun update(e: AnActionEvent, toolWindow: ToolWindow, selectedContent: Content?) {
    val id = toolWindow.id
    e.presentation.isEnabledAndVisible = e.project != null && id == toolWindowId && selectedContent != null
  }

  override fun actionPerformed(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
    val contextComponent = e.getData(PlatformDataKeys.CONTEXT_COMPONENT)
    val tabLabel = if (contextComponent is BaseLabel) contextComponent else e.getData(ToolWindowContentUi.SELECTED_CONTENT_TAB_LABEL)
    val tabLabelContent = tabLabel?.content ?: return
    showContentRenamePopup(tabLabel, tabLabelContent)
  }

  private fun showContentRenamePopup(baseLabel: BaseLabel, content: Content) {
    val textField = JTextField(content.displayName)
    textField.selectAll()

    val label = JBLabel(labelText)
    label.font = UIUtil.getLabelFont().deriveFont(Font.BOLD)

    val panel = SwingHelper.newLeftAlignedVerticalPanel(label, Box.createVerticalStrut(JBUI.scale(2)), textField)
    panel.addFocusListener(object : FocusAdapter() {
      override fun focusGained(e: FocusEvent?) {
        IdeFocusManager.findInstance().requestFocus(textField, false)
      }
    })

    val balloon = JBPopupFactory.getInstance().createDialogBalloonBuilder(panel, null)
      .setShowCallout(true)
      .setCloseButtonEnabled(false)
      .setAnimationCycle(0)
      .setDisposable(content)
      .setHideOnKeyOutside(true)
      .setHideOnClickOutside(true)
      .setRequestFocus(true)
      .setBlockClicksThroughBalloon(true)
      .createBalloon()

    textField.addKeyListener(object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent?) {
        if (e != null && e.keyCode == KeyEvent.VK_ENTER) {
          if (!Disposer.isDisposed(content)) {
            if (textField.text.isEmpty()) {
              textField.putClientProperty(OUTLINE_PROPERTY, ERROR_VALUE)
              textField.repaint()
              return
            }
            content.displayName = textField.text
            contentNameUpdated(content)
          }
          balloon.hide()
        }
      }
    })

    textField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        val outlineValue = textField.getClientProperty(OUTLINE_PROPERTY)
        if (outlineValue == ERROR_VALUE) {
          textField.putClientProperty(OUTLINE_PROPERTY, null)
          textField.repaint()
        }
      }
    })

    balloon.show(RelativePoint(baseLabel, Point(baseLabel.width / 2, 0)), Balloon.Position.above)
  }

  open fun contentNameUpdated(content: Content) {}
}