// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.util.popup

import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import javax.swing.JList

suspend fun JBPopup.showAndAwait(point: RelativePoint) = showAndAwait(point) {}

suspend fun <T> JBPopup.showAndAwait(point: RelativePoint, getResultOnOk: JBPopup.() -> T): T {
  try {
    show(point)
    return waitForResultAsync(getResultOnOk).await()
  }
  catch (e: CancellationException) {
    cancel()
    throw e
  }
}

private fun <T> JBPopup.waitForResultAsync(getResultOnOk: JBPopup.() -> T): Deferred<T> {
  val result = CompletableDeferred<T>(parent = null)
  addListener(object : JBPopupListener {
    override fun onClosed(event: LightweightWindowEvent) {
      if (event.isOk) {
        val value = getResultOnOk()
        result.complete(value)
      }
      else {
        result.cancel()
      }
    }
  })
  return result
}

suspend fun <T> JBPopup.showAndAwaitListSubmission(point: RelativePoint): T? {
  @Suppress("UNCHECKED_CAST")
  val list = UIUtil.findComponentOfType(content, JList::class.java) as JList<T>
  return showAndAwaitSubmission(list, point)
}

suspend fun <T> JBPopup.showAndAwaitSubmission(list: JList<T>, point: RelativePoint): T? {
  try {
    show(point)
    return waitForChoiceAsync(list).await()
  }
  catch (e: CancellationException) {
    cancel()
    throw e
  }
}

/**
 * [PopupChooserBuilder.setItemChosenCallback] fires on every selection change
 * [PopupChooserBuilder.setCancelCallback] fires on every popup close
 * So we need a custom listener here
 */
private fun <T> JBPopup.waitForChoiceAsync(list: JList<T>): Deferred<T> {
  val result = CompletableDeferred<T>(parent = null)
  addListener(object : JBPopupListener {
    override fun onClosed(event: LightweightWindowEvent) {
      if (event.isOk) {
        val selectedValue = list.selectedValue
        result.complete(selectedValue)
      }
      else {
        result.cancel()
      }
    }
  })
  return result
}