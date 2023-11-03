// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.util.popup

import com.intellij.collaboration.ui.codereview.details.SelectableWrapper
import com.intellij.collaboration.ui.items
import com.intellij.execution.ui.FragmentedSettingsUtil
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.SearchTextField
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.AbstractPopup
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.swing.JList
import kotlin.coroutines.resume

object CollaborationToolsPopupUtil {
  fun configureSearchField(popup: JBPopup, popupConfig: PopupConfig) {
    val searchTextField = UIUtil.findComponentOfType(popup.content, SearchTextField::class.java)
    if (searchTextField != null) {
      tuneSearchFieldForNewUI(searchTextField)
      setSearchFieldPlaceholder(searchTextField, popupConfig.searchTextPlaceHolder)
    }
  }

  private fun tuneSearchFieldForNewUI(searchTextField: SearchTextField) {
    if (!ExperimentalUI.isNewUI()) return
    AbstractPopup.customizeSearchFieldLook(searchTextField, true)
  }

  private fun setSearchFieldPlaceholder(searchTextField: SearchTextField, placeholderText: @NlsContexts.StatusText String?) {
    placeholderText ?: return
    searchTextField.textEditor.emptyText.text = placeholderText
    FragmentedSettingsUtil.setupPlaceholderVisibility(searchTextField.textEditor)
  }
}

suspend fun JBPopup.showAndAwait(point: RelativePoint) = showAndAwait(point) {}

suspend fun <T> JBPopup.showAndAwait(point: RelativePoint, getResultOnOk: JBPopup.() -> T): T {
  show(point)
  return waitForResultAsync(getResultOnOk)
}

suspend fun JBPopup.awaitClose() = waitForResultAsync { Unit }

private suspend fun <T> JBPopup.waitForResultAsync(getResultOnOk: JBPopup.() -> T): T {
  checkDisposed()
  return try {
    suspendCancellableCoroutine<T> { continuation ->
      addChoicePopupListener(continuation) { getResultOnOk() }
    }
  }
  catch (e: CancellationException) {
    cancel()
    throw e
  }
}

suspend fun <T> JBPopup.showAndAwaitListSubmission(point: RelativePoint): T? {
  @Suppress("UNCHECKED_CAST")
  val list = UIUtil.findComponentOfType(content, JList::class.java) as JList<T>
  return showAndAwaitSubmission(list, point)
}

suspend fun <T> JBPopup.showAndAwaitSubmission(list: JList<T>, point: RelativePoint): T? {
  show(point)
  return waitForChoiceAsync(list)
}

suspend fun <T> JBPopup.showAndAwaitSubmissions(list: JList<SelectableWrapper<T>>, point: RelativePoint): List<T> {
  show(point)
  return waitForMultipleChoiceAsync(list)
}

/**
 * [PopupChooserBuilder.setItemChosenCallback] fires on every selection change
 * [PopupChooserBuilder.setCancelCallback] fires on every popup close
 * So we need a custom listener here
 */
@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun <T> JBPopup.waitForChoiceAsync(list: JList<T>): T {
  checkDisposed()
  return try {
    suspendCancellableCoroutine<T> { continuation ->
      addChoicePopupListener(continuation) { list.selectedValue }
    }
  }
  catch (e: CancellationException) {
    cancel()
    throw e
  }
}

private suspend fun <T> JBPopup.waitForMultipleChoiceAsync(list: JList<SelectableWrapper<T>>): List<T> {
  checkDisposed()
  return try {
    suspendCancellableCoroutine<List<T>> { continuation ->
      addChoicesPopupListener(continuation) {
        list.model.items
          .filter { item -> item.isSelected }
          .map { item -> item.value }
      }
    }
  }
  catch (e: CancellationException) {
    cancel()
    throw e
  }
}

private fun <T> JBPopup.addChoicePopupListener(cont: CancellableContinuation<T>, chosenValue: () -> T) {
  val listener = object : JBPopupListener {
    override fun onClosed(event: LightweightWindowEvent) {
      when {
        event.isOk -> cont.resume(chosenValue())
        else -> cont.cancel()
      }
    }
  }
  addListener(listener)
}

private fun <T> JBPopup.addChoicesPopupListener(cont: CancellableContinuation<List<T>>, chosenValues: () -> List<T>) {
  val listener = object : JBPopupListener {
    override fun onClosed(event: LightweightWindowEvent) {
      cont.resume(chosenValues())
    }
  }
  addListener(listener)
}

@Throws(CancellationException::class)
private suspend fun JBPopup.checkDisposed() {
  if (isDisposed) {
    val ctx = currentCoroutineContext()
    ctx.cancel()
    ctx.ensureActive()
  }
}