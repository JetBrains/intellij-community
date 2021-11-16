// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.completion

import com.intellij.codeInsight.lookup.impl.LookupCellRenderer
import com.intellij.lang.LangBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.*
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.ui.popup.util.BaseStep
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.popup.list.ListPopupImpl
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.event.KeyEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.*
import javax.swing.text.JTextComponent

/**
 * @see TextCompletionContributor
 */
@ApiStatus.Experimental
class TextCompletionPopup<C : JTextComponent>(
  private val project: Project,
  private val textComponent: C,
  private val contributor: TextCompletionContributor<C>
) {
  private val isSkipTextUpdate = AtomicBoolean()

  private var popup: Popup? = null
  private val visibleCompletionVariants = ArrayList<TextCompletionInfo?>()

  private fun isFocusedParent(): Boolean {
    val focusManager = IdeFocusManager.getInstance(project)
    val focusOwner = focusManager.focusOwner
    return SwingUtilities.isDescendingFrom(focusOwner, textComponent)
  }

  private fun isValidParent(): Boolean {
    return textComponent.height > 0 && textComponent.width > 0
  }

  fun updatePopup(type: UpdatePopupType) {
    if (isValidParent() && isFocusedParent()) {
      val textToComplete = contributor.getTextToComplete(textComponent)
      val completionVariants = contributor.getCompletionVariants(textComponent, textToComplete)
      visibleCompletionVariants.clear()
      visibleCompletionVariants.add(null)
      for (variant in completionVariants) {
        if (variant.text.startsWith(textToComplete)) {
          if (visibleCompletionVariants[0] == null) {
            visibleCompletionVariants.removeAt(0)
          }
          visibleCompletionVariants.add(variant)
        }
      }

      val hasVariances = visibleCompletionVariants[0] != null
      when (type) {
        UpdatePopupType.UPDATE -> popup?.update()
        UpdatePopupType.SHOW -> showPopup()
        UpdatePopupType.HIDE -> hidePopup()
        UpdatePopupType.SHOW_IF_HAS_VARIANCES ->
          when {
            hasVariances -> showPopup()
            else -> hidePopup()
          }
      }
    }
  }

  private fun showPopup() {
    if (popup == null) {
      popup = Popup().also {
        Disposer.register(it, Disposable { popup = null })
      }
      popup?.showUnderneathOf(textComponent)
    }
    popup?.update()
  }

  private fun hidePopup() {
    popup?.cancel()
    popup = null
  }

  private fun fireVariantChosen(variant: TextCompletionInfo?) {
    if (variant != null) {
      isSkipTextUpdate.set(true)
      try {
        contributor.fireVariantChosen(textComponent, variant)
      }
      finally {
        isSkipTextUpdate.set(false)
      }
    }
  }

  init {
    textComponent.whenFocusGained {
      if (textComponent.text.isEmpty()) {
        updatePopup(UpdatePopupType.SHOW_IF_HAS_VARIANCES)
      }
    }
    textComponent.whenFirstFocusGained {
      updatePopup(UpdatePopupType.SHOW_IF_HAS_VARIANCES)
    }
    textComponent.addKeyboardAction(getKeyStrokes(IdeActions.ACTION_CODE_COMPLETION)) {
      updatePopup(UpdatePopupType.SHOW)
    }
    textComponent.addCaretListener {
      if (!isSkipTextUpdate.get()) {
        updatePopup(UpdatePopupType.UPDATE)
      }
    }
    textComponent.whenTextModified {
      if (!isSkipTextUpdate.get()) {
        // Listener for caret update is invoked after all other modification listeners,
        // But we needed crop text completion by updated caret position.
        // So invokeLater is here to postpone our completion popup update.
        invokeLater {
          updatePopup(UpdatePopupType.SHOW_IF_HAS_VARIANCES)
        }
      }
    }
  }

  private inner class Popup : ListPopupImpl(project, null, PopupStep(), null) {

    override fun getListElementRenderer() = Renderer()

    fun update() {
      listModel.updateOriginalList()

      val insets = textComponent.insets
      val popupWidth = textComponent.width - (insets.right + insets.left)
      val rowNumber = maxOf(1, minOf(list.model.size, list.visibleRowCount))
      val popupHeight = list.fixedCellHeight * rowNumber
      size = Dimension(popupWidth, popupHeight)
    }

    override fun process(aEvent: KeyEvent) {
      if (aEvent.keyCode != KeyEvent.VK_LEFT &&
          aEvent.keyCode != KeyEvent.VK_RIGHT) {
        super.process(aEvent)
      }
    }

    init {
      setMaxRowCount(10)
      setRequestFocus(false)

      list.prototypeCellValue = TextCompletionInfo("X")
      list.background = LookupCellRenderer.BACKGROUND_COLOR
      list.foreground = JBColor.foreground()
      list.selectionBackground = LookupCellRenderer.SELECTED_BACKGROUND_COLOR
      list.selectionForeground = JBColor.foreground()
      list.selectionMode = ListSelectionModel.SINGLE_SELECTION
      list.border = null
      list.isFocusable = false
      list.font = textComponent.font

      list.removeKeyboardAction(getKeyStrokes(IdeActions.ACTION_COPY))
      list.removeKeyboardAction(getKeyStrokes(IdeActions.ACTION_CUT))
      list.removeKeyboardAction(getKeyStrokes(IdeActions.ACTION_DELETE))
      list.removeKeyboardAction(getKeyStrokes(IdeActions.ACTION_PASTE))
      list.removeKeyboardAction(getKeyStrokes(IdeActions.ACTION_SELECT_ALL))
      list.addKeyboardAction(getKeyStrokes(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_REPLACE)) {
        fireVariantChosen(list.selectedValue as? TextCompletionInfo)
        hidePopup()
      }
    }
  }

  private inner class PopupStep : BaseStep<TextCompletionInfo?>(), ListPopupStep<TextCompletionInfo?> {

    override fun isSelectable(value: TextCompletionInfo?): Boolean = value != null

    override fun getValues(): List<TextCompletionInfo?> = visibleCompletionVariants

    override fun onChosen(selectedValue: TextCompletionInfo?, finalChoice: Boolean): com.intellij.openapi.ui.popup.PopupStep<*>? {
      fireVariantChosen(selectedValue)
      return FINAL_CHOICE
    }

    override fun getTextFor(value: TextCompletionInfo?): String = value?.text ?: ""
    override fun getIconFor(value: TextCompletionInfo?): Icon? = value?.icon
    override fun getSeparatorAbove(value: TextCompletionInfo?): ListSeparator? = null
    override fun getDefaultOptionIndex(): Int = 0
    override fun getTitle(): String? = null
    override fun hasSubstep(selectedValue: TextCompletionInfo?): Boolean = false
    override fun canceled() {}
  }

  private inner class Renderer : ColoredListCellRenderer<TextCompletionInfo?>() {
    override fun customizeCellRenderer(list: JList<out TextCompletionInfo?>,
                                       value: TextCompletionInfo?,
                                       index: Int,
                                       selected: Boolean,
                                       hasFocus: Boolean) {
      // Code completion prefix should be visible under cell selection
      mySelected = false

      myBorder = null

      if (value == null) {
        append(LangBundle.message("completion.no.suggestions"))
        return
      }

      icon = value.icon
      val textStyle = SimpleTextAttributes.STYLE_PLAIN
      val prefix = contributor.getTextToComplete(textComponent)
      val prefixForeground = LookupCellRenderer.MATCHED_FOREGROUND_COLOR
      val prefixAttributes = SimpleTextAttributes(textStyle, prefixForeground)
      if (value.text.startsWith(prefix)) {
        append(prefix, prefixAttributes)
        append(value.text.substring(prefix.length))
      }
      else {
        append(value.text)
      }
      val description = value.description
      if (description != null) {
        val descriptionForeground = LookupCellRenderer.getGrayedForeground(selected)
        val descriptionAttributes = SimpleTextAttributes(textStyle, descriptionForeground)
        append(" ")
        append(description.trim(), descriptionAttributes)
        appendTextPadding(maxOf(preferredSize.width, list.width - (ipad.left + ipad.right)), SwingConstants.RIGHT)
      }
    }
  }

  enum class UpdatePopupType {
    UPDATE, SHOW, HIDE, SHOW_IF_HAS_VARIANCES
  }
}