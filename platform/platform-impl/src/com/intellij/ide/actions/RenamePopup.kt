// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.SwingHelper
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Font
import java.awt.Point
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

private const val OUTLINE_PROPERTY = "JComponent.outline"
private const val ERROR_VALUE = "error"

@ApiStatus.Internal
class RenamePopup(
  @NlsContexts.Label private val labelText: String,
  @Nls private val initialValue: String,
  private val onApply: (@Nls String) -> Unit
) {

  fun show(
    anchorComponent: JComponent,
    disposable: Disposable,
    focusBackComponent: Component? = null,
    balloonPosition: Balloon.Position = Balloon.Position.above
  ) {
    val textField = JTextField(initialValue)
    textField.selectAll()

    val label = JBLabel(labelText)
    label.font = StartupUiUtil.labelFont.deriveFont(Font.BOLD)

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
      .setDisposable(disposable)
      .setHideOnKeyOutside(true)
      .setHideOnClickOutside(true)
      .setRequestFocus(true)
      .setBlockClicksThroughBalloon(true)
      .createBalloon()

    textField.addKeyListener(object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent?) {
        if (e != null && e.keyCode == KeyEvent.VK_ENTER) {
          if (textField.text.isEmpty()) {
            textField.putClientProperty(OUTLINE_PROPERTY, ERROR_VALUE)
            textField.repaint()
            return
          }
          onApply(textField.text)
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

    val targetPoint = Point(anchorComponent.width / 2, if (balloonPosition == Balloon.Position.above) 0 else anchorComponent.height)
    balloon.show(RelativePoint(anchorComponent, targetPoint), balloonPosition)
    balloon.addListener(object : JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        if (focusBackComponent != null) {
          IdeFocusManager.findInstance().requestFocus(focusBackComponent, false)
        }
      }
    })
  }
}
