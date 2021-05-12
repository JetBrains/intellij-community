// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.completetion

import com.intellij.codeInsight.lookup.impl.LookupCellRenderer
import com.intellij.lang.LangBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.service.ui.addKeyboardAction
import com.intellij.openapi.externalSystem.service.ui.getKeyStrokes
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.ui.popup.util.BaseStep
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.popup.list.ListPopupImpl
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.*
import javax.swing.text.JTextComponent

/**
 * @see TextCompletionContributor
 */
@ApiStatus.Experimental
class TextCompletionPopup<C : JTextComponent>(
  private val project: Project,
  private val parentComponent: C,
  private val contributor: TextCompletionContributor<C>,
  private val parentDisposable: Disposable
) {
  private var popup: Popup? = null
  private val visibleCompletionVariants = ArrayList<TextCompletionInfo?>()

  private val chooseListeners = ArrayList<(TextCompletionInfo) -> Unit>()

  fun onVariantChosen(action: (TextCompletionInfo) -> Unit) {
    chooseListeners.add(action)
  }

  fun updatePopup(forceShowPopup: Boolean = false) {
    if (parentComponent.height > 0 && parentComponent.width > 0) {
      val textToComplete = contributor.getTextToComplete(parentComponent)
      val completionVariants = contributor.getCompletionVariants(parentComponent, textToComplete)
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
      if (!forceShowPopup && visibleCompletionVariants[0] == null) {
        popup?.cancel()
        popup = null
      }
      else if (popup == null) {
        popup = Popup().also {
          Disposer.register(parentDisposable, it)
          Disposer.register(it, Disposable { popup = null })
        }
        popup?.showUnderneathOf(parentComponent)
      }
      popup?.update()
    }
  }

  init {
    parentComponent.addFocusListener(object : FocusAdapter() {
      override fun focusGained(e: FocusEvent) {
        if (contributor.getTextToComplete(parentComponent).isEmpty()) {
          updatePopup()
        }
      }
    })
    parentComponent.addFocusListener(object : FocusAdapter() {
      override fun focusGained(e: FocusEvent) {
        parentComponent.removeFocusListener(this)
        updatePopup()
      }
    })
    parentComponent.addKeyboardAction(getKeyStrokes("CodeCompletion")) {
      updatePopup(forceShowPopup = true)
    }
    contributor.whenTextModified(parentComponent, {
      updatePopup()
    }, parentDisposable)
  }

  private inner class Popup : ListPopupImpl(project, null, PopupStep(), null) {

    override fun getListElementRenderer() = Renderer()

    fun update() {
      listModel.updateOriginalList()

      val insets = parentComponent.insets
      val popupWidth = parentComponent.width - (insets.right + insets.left)
      val rowNumber = maxOf(1, minOf(list.model.size, list.visibleRowCount))
      val popupHeight = list.fixedCellHeight * rowNumber
      size = Dimension(popupWidth, popupHeight)
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
    }
  }

  private inner class PopupStep : BaseStep<TextCompletionInfo?>(), ListPopupStep<TextCompletionInfo?> {

    override fun isSelectable(value: TextCompletionInfo?): Boolean = value != null

    override fun getValues(): List<TextCompletionInfo?> = visibleCompletionVariants

    override fun onChosen(selectedValue: TextCompletionInfo?, finalChoice: Boolean): com.intellij.openapi.ui.popup.PopupStep<*>? {
      if (selectedValue != null) {
        ApplicationManager.getApplication().invokeLater {
          chooseListeners.forEach { it(selectedValue) }
        }
      }
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

      if (value == null) {
        append(LangBundle.message("completion.no.suggestions"))
        return
      }

      icon = value.icon
      val textStyle = SimpleTextAttributes.STYLE_PLAIN
      val prefix = contributor.getTextToComplete(parentComponent)
      val prefixForeground = LookupCellRenderer.MATCHED_FOREGROUND_COLOR
      val prefixAttributes = SimpleTextAttributes(textStyle, prefixForeground)
      append(prefix, prefixAttributes)
      if (value.text.startsWith(prefix)) {
        append(value.text.substring(prefix.length))
      }
      val description = value.description
      if (description != null) {
        val descriptionForeground = LookupCellRenderer.getGrayedForeground(selected)
        val descriptionAttributes = SimpleTextAttributes(textStyle, descriptionForeground)
        append(description, descriptionAttributes)
        appendTextPadding(maxOf(preferredSize.width, list.width) - ipad.right, SwingConstants.RIGHT)
      }
    }
  }
}