// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.keymap.KeymapUtil.getFirstKeyboardShortcutText
import com.intellij.openapi.ui.OptionAction
import com.intellij.openapi.util.Weighted
import com.intellij.util.containers.ContainerUtil.unmodifiableOrEmptySet
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

      fillOptionInfos()
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

  var isOkToProcessDefaultMnemonics = true

  val isSimpleButton: Boolean get() = options.isNullOrEmpty()

  private val _optionInfos = mutableSetOf<OptionInfo>()
  val optionInfos: Set<OptionInfo> get() = unmodifiableOrEmptySet(_optionInfos)

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
  fun updateOptions(options: Array<Action>?) {
    this.options = options
  }

  private fun fillOptionInfos() {
    _optionInfos.clear()
    _optionInfos += options.orEmpty().filter { it !== action }.map { getMenuInfo(it) }
  }

  private fun getMenuInfo(each: Action): OptionInfo {
    val text = (each.getValue(Action.NAME) as? String).orEmpty()
    var mnemonic = -1
    var mnemonicIndex = -1
    val plainText = StringBuilder()
    for (i in 0 until text.length) {
      val ch = text[i]
      if (ch == '&' || ch == '_') {
        if (i + 1 < text.length) {
          val mnemonicsChar = text[i + 1]
          mnemonic = Character.toUpperCase(mnemonicsChar).toInt()
          mnemonicIndex = i
        }
        continue
      }
      plainText.append(ch)
    }

    return OptionInfo(plainText.toString(), mnemonic, mnemonicIndex, this, each)
  }

  class OptionInfo internal constructor(
    val plainText: String,
    val mnemonic: Int,
    val mnemonicIndex: Int,
    val button: JBOptionButton,
    val action: Action
  )

  companion object {
    const val PROP_OPTIONS = "OptionActions"
    const val PROP_OPTION_TOOLTIP = "OptionTooltip"

    @JvmStatic
    fun getDefaultShowPopupShortcut() = DEFAULT_SHOW_POPUP_SHORTCUT

    @JvmStatic
    fun getDefaultTooltip() = "Show drop-down menu (${getFirstKeyboardShortcutText(getDefaultShowPopupShortcut())})"
  }
}

private class AnActionWrapper(action: AnAction) : AbstractAction() {
  init {
    putValue(OptionAction.AN_ACTION, action)
  }

  override fun actionPerformed(e: ActionEvent) = Unit
}