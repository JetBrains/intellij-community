// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.search

import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.awt.RelativePoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

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