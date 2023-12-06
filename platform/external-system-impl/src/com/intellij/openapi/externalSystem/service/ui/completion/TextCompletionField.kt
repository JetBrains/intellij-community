// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui.completion

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.externalSystem.service.ui.completion.collector.TextCompletionCollector
import com.intellij.openapi.observable.util.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.addKeyboardAction
import com.intellij.openapi.ui.getKeyStrokes
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.util.containers.DisposableWrapperList
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.text.BadLocationException
import com.intellij.openapi.observable.util.whenTextChangedFromUi as whenTextChangedFromUiImpl

abstract class TextCompletionField<T>(
  private val project: Project?
) : ExtendableTextField() {

  var renderer: TextCompletionRenderer<T> = TextCompletionRenderer.Default()

  var completionType: CompletionType = CompletionType.REPLACE_TEXT

  @get:ApiStatus.OverrideOnly
  protected open val completionCollector: TextCompletionCollector<T> =
    TextCompletionCollector.basic {
      @Suppress("DEPRECATION")
      getCompletionVariants()
    }

  @ScheduledForRemoval
  @Deprecated("Please, override completionCollector instead")
  protected open fun getCompletionVariants(): List<T> {
    throw UnsupportedOperationException("Please, override completionCollector property")
  }

  private val updateMutex = AtomicBoolean()

  private var lastUpdateCompletionPopupTime: Long = 0L

  private var lastUpdateCompletionPopupEvent: UpdatePopupType = UpdatePopupType.HIDE

  private var popup: TextCompletionPopup<T>? = null

  private val completionVariantChosenListeners = DisposableWrapperList<(T) -> Unit>()

  fun getTextToComplete(): @NlsSafe String {
    return when (completionType) {
      CompletionType.REPLACE_TEXT -> text
      CompletionType.REPLACE_WORD -> getWordUnderCaret()
    }
  }

  private fun getFilteredCompletionVariants(): List<T> {
    val textToComplete = getTextToComplete()
    val isPassAllVariances = lastUpdateCompletionPopupEvent == UpdatePopupType.SHOW_ALL_VARIANCES
    return completionCollector.getCompletionVariants()
      .filter { isPassAllVariances || textToComplete in renderer.getText(it) }
  }

  private fun fireVariantChosen(variant: T) {
    updateMutex.lockOrSkip {
      when (completionType) {
        CompletionType.REPLACE_TEXT -> replaceText(variant)
        CompletionType.REPLACE_WORD -> replaceWordUnderCaret(variant)
      }
      completionVariantChosenListeners.forEach { listener ->
        listener(variant)
      }
    }
  }

  private fun whenCompletionVariantChosen(parentDisposable: Disposable? = null, listener: (T) -> Unit) {
    when (parentDisposable) {
      null -> completionVariantChosenListeners.add(listener)
      else -> completionVariantChosenListeners.add(listener, parentDisposable)
    }
  }

  private fun replaceText(variant: T) {
    text = renderer.getText(variant)
  }

  private fun replaceWordUnderCaret(variant: T) {
    val variantText = renderer.getText(variant)
    val caretPosition = getBoundedCaretPosition()
    val wordRange = getWordRange(caretPosition)
    document.remove(wordRange.first, wordRange.last - wordRange.first + 1)
    document.insertString(wordRange.first, variantText, null)
  }

  private fun getWordUnderCaret(): String {
    val caretPosition = getBoundedCaretPosition()
    val wordRange = getWordRange(caretPosition)
    val textToCompleteRange = wordRange.first until caretPosition
    return text.substring(textToCompleteRange)
  }

  private fun getWordRange(offset: Int): IntRange {
    var wordStartPosition = 0
    for (word in text.split(" ")) {
      val wordEndPosition = wordStartPosition + word.length
      if (offset in wordStartPosition..wordEndPosition) {
        return wordStartPosition until wordEndPosition
      }
      wordStartPosition = wordEndPosition + 1
    }
    throw BadLocationException(text, offset)
  }

  private fun getBoundedCaretPosition(): Int {
    return maxOf(0, minOf(text.length, caretPosition))
  }

  private fun isFocused(): Boolean {
    val focusManager = IdeFocusManager.getInstance(project)
    val frame = focusManager.lastFocusedIdeWindow
    val focusOwner = focusManager.getLastFocusedFor(frame)
    return focusOwner == this
  }

  fun updatePopup(type: UpdatePopupType) {
    if (!isShowing || !isFocused()) return

    when (type) {
      UpdatePopupType.SHOW -> showPopup()
      UpdatePopupType.HIDE -> hidePopup()
      UpdatePopupType.UPDATE -> updatePopup()
      UpdatePopupType.SHOW_ALL_VARIANCES -> showPopup()
      UpdatePopupType.SHOW_IF_HAS_VARIANCES -> Unit
    }
    when (type) {
      UpdatePopupType.SHOW -> Unit
      UpdatePopupType.HIDE -> return
      UpdatePopupType.UPDATE -> return
      UpdatePopupType.SHOW_ALL_VARIANCES -> Unit
      UpdatePopupType.SHOW_IF_HAS_VARIANCES -> Unit
    }

    val updatePopupTime = System.currentTimeMillis()
    lastUpdateCompletionPopupTime = updatePopupTime
    lastUpdateCompletionPopupEvent = type
    val modalityState = ModalityState.stateForComponent(this)
    completionCollector.collectCompletionVariants(modalityState) { completion ->
      if (lastUpdateCompletionPopupTime == updatePopupTime) {
        when (lastUpdateCompletionPopupEvent) {
          UpdatePopupType.UPDATE -> Unit
          UpdatePopupType.SHOW -> updatePopup()
          UpdatePopupType.HIDE -> Unit
          UpdatePopupType.SHOW_ALL_VARIANCES -> updatePopup()
          UpdatePopupType.SHOW_IF_HAS_VARIANCES ->
            if (completion.isNotEmpty())
              showPopup()
        }
      }
    }
  }

  private fun updatePopup() {
    popup?.update()
  }

  private fun showPopup() {
    if (popup == null) {
      val contributor = object : TextCompletionPopup.Contributor<T> {
        override fun getItems() = getFilteredCompletionVariants()
        override fun fireItemChosen(item: T) = fireVariantChosen(item)
      }
      popup = TextCompletionPopup(project, this, contributor, renderer)
        .also { Disposer.register(it, Disposable { popup = null }) }
        .apply { showUnderneathOfTextComponent() }
    }
    updatePopup()
  }

  private fun hidePopup() {
    popup?.cancel()
    popup = null
  }

  fun whenTextChangedFromUi(parentDisposable: Disposable? = null, listener: (String) -> Unit) {
    whenTextChangedFromUiImpl(parentDisposable, listener)
    whenCompletionVariantChosen(parentDisposable) {
      invokeLater(ModalityState.stateForComponent(this)) {
        listener(text)
      }
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
    UPDATE, SHOW, HIDE, SHOW_IF_HAS_VARIANCES, SHOW_ALL_VARIANCES
  }

  enum class CompletionType {
    REPLACE_WORD, REPLACE_TEXT
  }
}