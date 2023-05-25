// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.keymap.KeymapUtil.getFirstKeyboardShortcutText
import com.intellij.openapi.ui.OptionAction
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Weighted
import com.intellij.ui.UIBundle
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.*
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JButton
import javax.swing.KeyStroke.getKeyStroke

private val DEFAULT_SHOW_POPUP_SHORTCUT = CustomShortcutSet(getKeyStroke(KeyEvent.VK_ENTER, InputEvent.ALT_MASK or InputEvent.SHIFT_MASK))

open class JBOptionButton(action: Action?, options: Array<Action>?) : JButton(action), Weighted {
  var options: Array<Action>? = null
    set(value) {
      val oldOptions = options
      field = value

      firePropertyChange(PROP_OPTIONS, oldOptions, options)
      if (!Arrays.equals(oldOptions, options)) {
        revalidate()
        repaint()
      }
    }

  fun setOptions(actions: List<AnAction>?) {
    options = actions?.map { AnActionWrapper(it) }?.toTypedArray()
  }

  var optionTooltipText: String? = null
    set(value) {
      val oldValue = optionTooltipText
      field = value
      firePropertyChange(PROP_OPTION_TOOLTIP, oldValue, optionTooltipText)
    }

  val isSimpleButton: Boolean get() = options.isNullOrEmpty()

  var addSeparator = true
  var selectFirstItem = true
  var popupBackgroundColor: Color? = null
  var showPopupYOffset = 6
  var popupHandler: ((JBPopup) -> Unit)? = null

  init {
    this.options = options
  }

  override fun getUIClassID(): String = "OptionButtonUI"
  override fun getUI(): OptionButtonUI = super.getUI() as OptionButtonUI

  override fun getWeight(): Double = 0.5

  fun togglePopup() = getUI().togglePopup()
  fun showPopup(actionToSelect: Action? = null, ensureSelection: Boolean = true) = getUI().showPopup(actionToSelect, ensureSelection)
  fun closePopup() = getUI().closePopup()

  @Deprecated("Use setOptions(Action[]) instead", ReplaceWith("setOptions(options)"))
  @ApiStatus.ScheduledForRemoval
  fun updateOptions(options: Array<Action>?) {
    this.options = options
  }

  companion object {
    const val PROP_OPTIONS = "OptionActions"
    const val PROP_OPTION_TOOLTIP = "OptionTooltip"
    const val PLACE = "ActionPlace"

    @JvmStatic
    fun getDefaultShowPopupShortcut() = DEFAULT_SHOW_POPUP_SHORTCUT

    @JvmStatic
    fun getDefaultTooltip() = UIBundle.message("option.button.tooltip.shortcut.text",
                                               getFirstKeyboardShortcutText(getDefaultShowPopupShortcut()))
  }
}

private class AnActionWrapper(action: AnAction) : AbstractAction() {
  init {
    putValue(OptionAction.AN_ACTION, action)
  }

  override fun actionPerformed(e: ActionEvent) = Unit
}