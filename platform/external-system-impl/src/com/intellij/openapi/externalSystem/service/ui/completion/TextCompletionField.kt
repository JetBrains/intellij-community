// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui.completion

import com.intellij.codeInsight.lookup.impl.LookupCellRenderer
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.observable.util.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.addKeyboardAction
import com.intellij.openapi.ui.getKeyStrokes
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.fields.ExtendableTextField
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.*

abstract class TextCompletionField(private val project: Project) : ExtendableTextField() {

  protected abstract val contributor: TextCompletionContributor<TextCompletionField>

  private val updateMutex = AtomicBoolean()

  private var popup: TextCompletionPopup<TextCompletionInfo, TextCompletionField>? = null
  private val visibleCompletionVariants = ArrayList<TextCompletionInfo>()

  private fun isFocusedParent(): Boolean {
    val focusManager = IdeFocusManager.getInstance(project)
    val focusOwner = focusManager.focusOwner
    return SwingUtilities.isDescendingFrom(focusOwner, this)
  }

  private fun isValidParent(): Boolean {
    return height > 0 && width > 0
  }

  private fun rebuildCompletionVariantsList() {
    val textToComplete = contributor.getTextToComplete(this@TextCompletionField)
    visibleCompletionVariants.clear()
    visibleCompletionVariants.addAll(
      contributor.getCompletionVariants(this@TextCompletionField, textToComplete)
        .filter { it.text.startsWith(textToComplete) }
    )
  }

  private fun hasVariances(): Boolean {
    return visibleCompletionVariants.isNotEmpty()
  }

  fun updatePopup(type: UpdatePopupType) {
    if (isValidParent() && isFocusedParent()) {
      rebuildCompletionVariantsList()
      when (type) {
        UpdatePopupType.UPDATE -> popup?.update()
        UpdatePopupType.SHOW -> showPopup()
        UpdatePopupType.HIDE -> hidePopup()
        UpdatePopupType.SHOW_IF_HAS_VARIANCES ->
          when {
            hasVariances() -> showPopup()
            else -> hidePopup()
          }
      }
    }
  }

  private fun showPopup() {
    if (popup == null) {
      val contributor = object : TextCompletionPopup.Contributor<TextCompletionInfo> {
        override fun getItems(text: String) = visibleCompletionVariants
        override fun fireItemChosen(item: TextCompletionInfo) = fireVariantChosen(item)
      }
      popup = TextCompletionPopup(project, this, contributor, Renderer())
        .also { Disposer.register(it, Disposable { popup = null }) }
        .also { it.showUnderneathOf(this) }
    }
    popup?.update()
  }

  private fun hidePopup() {
    popup?.cancel()
    popup = null
  }

  private fun fireVariantChosen(variant: TextCompletionInfo) {
    updateMutex.lockOrSkip {
      contributor.fireVariantChosen(this, variant)
    }
  }

  init {
    whenFocusGained {
      if (text.isEmpty()) {
        updatePopup(UpdatePopupType.SHOW_IF_HAS_VARIANCES)
      }
    }
    onceWhenFocusGained {
      updatePopup(UpdatePopupType.SHOW_IF_HAS_VARIANCES)
    }
    addKeyboardAction(getKeyStrokes(IdeActions.ACTION_CODE_COMPLETION)) {
      updatePopup(UpdatePopupType.SHOW)
    }
    whenCaretMoved {
      updateMutex.lockOrSkip {
        updatePopup(UpdatePopupType.UPDATE)
      }
    }
    whenTextChanged {
      updateMutex.lockOrSkip {
        // Listener for caret update is invoked after all other modification listeners,
        // But we needed crop text completion by updated caret position.
        // So invokeLater is here to postpone our completion popup update.
        invokeLater {
          updatePopup(UpdatePopupType.SHOW_IF_HAS_VARIANCES)
        }
      }
    }
  }

  enum class UpdatePopupType {
    UPDATE, SHOW, HIDE, SHOW_IF_HAS_VARIANCES
  }

  private inner class Renderer : TextCompletionRenderer<TextCompletionInfo> {
    override fun customizeCellRenderer(text: String, cell: TextCompletionRenderer.Cell<TextCompletionInfo>) {
      val item = cell.item
      val list = cell.list
      with (cell.component) {
        icon = item.icon
        val textStyle = SimpleTextAttributes.STYLE_PLAIN
        val prefix = contributor.getTextToComplete(this@TextCompletionField)
        val prefixForeground = LookupCellRenderer.MATCHED_FOREGROUND_COLOR
        val prefixAttributes = SimpleTextAttributes(textStyle, prefixForeground)
        if (item.text.startsWith(prefix)) {
          append(prefix, prefixAttributes)
          append(item.text.substring(prefix.length))
        }
        else {
          append(item.text)
        }
        val description = item.description
        if (description != null) {
          val descriptionForeground = LookupCellRenderer.getGrayedForeground(cell.isSelected)
          val descriptionAttributes = SimpleTextAttributes(textStyle, descriptionForeground)
          append(" ")
          append(description.trim(), descriptionAttributes)
          val padding = maxOf(preferredSize.width, list.width - (ipad.left + ipad.right))
          appendTextPadding(padding, SwingConstants.RIGHT)
        }
      }
    }
  }
}