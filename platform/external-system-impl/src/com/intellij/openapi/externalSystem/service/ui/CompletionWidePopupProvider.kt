// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui

import com.intellij.codeInsight.lookup.impl.LookupCellRenderer
import com.intellij.lang.LangBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseStep
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.popup.list.ListPopupImpl
import java.awt.Dimension
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.Icon
import javax.swing.JList
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.text.JTextComponent

class CompletionWidePopupProvider(
  private val project: Project,
  private val textComponent: JTextComponent,
  private val completionVariants: (String) -> List<String>,
  private val parentDisposable: Disposable
) {
  private var popup: CompletionPopup? = null
  private val visibleCompletionVariants = ArrayList<String?>()

  private val chooseListeners = ArrayList<(String) -> Unit>()

  fun onVariantChosen(action: (String) -> Unit) {
    chooseListeners.add(action)
  }

  fun showPopup() {
    if (popup == null) {
      popup = CompletionPopup().also {
        Disposer.register(parentDisposable, it)
        Disposer.register(it, Disposable { popup = null })
      }
      popup?.showUnderneathOf(textComponent)
    }
    updatePopup()
  }

  fun hidePopup() {
    popup?.cancel()
    popup = null
  }

  fun updatePopup() {
    val textToComplete = textComponent.text
    val completionVariants = completionVariants(textToComplete)
    visibleCompletionVariants.clear()
    visibleCompletionVariants.add(null)
    for (variant in completionVariants) {
      if (variant.startsWith(textToComplete)) {
        if (visibleCompletionVariants[0] == null) {
          visibleCompletionVariants.removeAt(0)
        }
        visibleCompletionVariants.add(variant)
      }
    }
    popup?.update()
  }

  init {
    textComponent.addFocusListener(object : FocusAdapter() {
      override fun focusGained(e: FocusEvent) {
        textComponent.removeFocusListener(this)
        showPopup()
      }
    })
    textComponent.addKeyboardAction(getKeyStrokes("CodeCompletion")) {
      showPopup()
    }
    textComponent.document.addDocumentListener(
      object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          updatePopup()
        }
      }
    )
  }

  private inner class CompletionPopup : ListPopupImpl(project, null, CompletionPopupStep(), null) {

    override fun getListElementRenderer() = Renderer()

    fun update() {
      listModel.updateOriginalList()

      val insets = textComponent.insets
      val popupWidth = textComponent.width - (insets.right + insets.left)
      val rowNumber = maxOf(1, minOf(list.model.size, list.visibleRowCount))
      val popupHeight = list.fixedCellHeight * rowNumber
      size = Dimension(popupWidth, popupHeight)
    }

    init {
      setMaxRowCount(10)
      setRequestFocus(false)

      list.prototypeCellValue = LangBundle.message("completion.no.suggestions")
      list.background = LookupCellRenderer.BACKGROUND_COLOR
      list.foreground = LookupCellRenderer.getGrayedForeground(false)
      list.selectionBackground = LookupCellRenderer.SELECTED_BACKGROUND_COLOR
      list.selectionForeground = LookupCellRenderer.getGrayedForeground(true)
      list.selectionMode = ListSelectionModel.SINGLE_SELECTION
      list.border = null
      list.isFocusable = false
    }
  }

  private inner class CompletionPopupStep : BaseStep<String?>(), ListPopupStep<String?> {

    override fun isSelectable(value: String?): Boolean = value != null

    override fun getValues(): List<String?> = visibleCompletionVariants

    override fun onChosen(selectedValue: String?, finalChoice: Boolean): PopupStep<*>? {
      if (selectedValue != null) {
        ApplicationManager.getApplication().invokeLater {
          textComponent.text = selectedValue
          chooseListeners.forEach { it(selectedValue) }
        }
      }
      return FINAL_CHOICE
    }

    override fun getTextFor(value: @NlsSafe String?): String = value.toString()
    override fun getIconFor(value: String?): Icon? = null
    override fun getSeparatorAbove(value: String?): ListSeparator? = null
    override fun getDefaultOptionIndex(): Int = -1
    override fun getTitle(): String? = null
    override fun hasSubstep(selectedValue: String?): Boolean = false
    override fun canceled() {}
  }

  private inner class Renderer : SimpleListCellRenderer<String>() {

    override fun customize(list: JList<out String>, value: @NlsSafe String?, index: Int, selected: Boolean, hasFocus: Boolean) {
      if (value != null) {
        val prefix = textComponent.text
        val suffix = if (value.startsWith(prefix)) value.substring(prefix.length) else ""
        val prefixColor = LookupCellRenderer.MATCHED_FOREGROUND_COLOR
        val prefixHexColor = Integer.toHexString(prefixColor.rgb).substring(2)
        @Suppress("HardCodedStringLiteral")
        text = "<html><font color=#$prefixHexColor>$prefix</font>$suffix</html>"
      }
      else {
        text = LangBundle.message("completion.no.suggestions")
      }
    }
  }
}