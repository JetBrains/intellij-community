// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.util.popup

import com.intellij.collaboration.ui.codereview.details.SelectableWrapper
import com.intellij.collaboration.ui.codereview.list.search.PopupConfig
import com.intellij.collaboration.ui.codereview.list.search.ShowDirection
import com.intellij.collaboration.ui.items
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.SearchTextField
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.TextComponentEmptyText
import com.intellij.ui.popup.AbstractPopup
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import java.awt.Point
import javax.swing.JList
import javax.swing.ListModel
import kotlin.coroutines.resume

internal object CollaborationToolsPopupUtil {
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
    TextComponentEmptyText.setupPlaceholderVisibility(searchTextField.textEditor)
  }
}

suspend fun JBPopup.showAndAwait(point: RelativePoint, showDirection: ShowDirection) {
  showPopup(point, showDirection)
  return awaitClose()
}

suspend fun JBPopup.awaitClose() {
  checkDisposed()
  return try {
    suspendCancellableCoroutine { continuation ->
      continueWhenPopupClosed(continuation) { }
    }
  }
  catch (e: CancellationException) {
    cancel()
    throw e
  }
}

suspend fun <T> JBPopup.showAndAwaitListSubmission(point: RelativePoint, showDirection: ShowDirection): T? {
  @Suppress("UNCHECKED_CAST")
  val list = UIUtil.findComponentOfType(content, JList::class.java) as JList<T>
  return showAndAwaitSubmission(list, point, showDirection)
}

suspend fun <T> JBPopup.showAndAwaitSubmission(list: JList<T>, point: RelativePoint, showDirection: ShowDirection): T? {
  showPopup(point, showDirection)
  return waitForChoiceAsync(list)
}

suspend fun <T> JBPopup.showAndAwaitSubmissions(originalListModel: ListModel<SelectableWrapper<T>>, point: RelativePoint, showDirection: ShowDirection): List<T> {
  showPopup(point, showDirection)
  return waitForMultipleChoiceAsync(originalListModel)
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
      continueWhenPopupClosed(continuation) { list.selectedValue }
    }
  }
  catch (e: CancellationException) {
    cancel()
    throw e
  }
}

private suspend fun <T> JBPopup.waitForMultipleChoiceAsync(originalListModel: ListModel<SelectableWrapper<T>>): List<T> {
  checkDisposed()
  return try {
    suspendCancellableCoroutine<List<T>> { continuation ->
      addChoicesPopupListener(continuation) {
        originalListModel.items
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

private fun <T> JBPopup.continueWhenPopupClosed(cont: CancellableContinuation<T>, chosenValue: () -> T) {
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

// TODO: replace with `com/intellij/vcsUtil/VcsUIUtil.kt`
private fun JBPopup.showPopup(relativePoint: RelativePoint, showDirection: ShowDirection) {
  val popup = this
  popup.addListener(object : JBPopupListener {
    override fun beforeShown(event: LightweightWindowEvent) {
      val location = Point(popup.locationOnScreen).apply {
        if (showDirection == ShowDirection.ABOVE) y = relativePoint.screenPoint.y - popup.size.height
      }

      popup.setLocation(location)
      popup.removeListener(this)
    }
  })
  popup.show(relativePoint)
}