// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.contextModality
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.swing.SwingUtilities
import kotlin.coroutines.resume

/**
 * Suspends until the focus transfers that this or a previous EDT event requested are complete.
 * When the coroutine context has a modality state, the function resumes only when this modality state allows it,
 * as [com.intellij.openapi.application.Application.invokeLater] does.
 * A focus transfer between windows can complete later, because the OS grants it asynchronously.
 */
@Suppress("UnusedReceiverParameter")
suspend fun IdeFocusManager.awaitFocusSettlesDown() {
  suspendCancellableCoroutine { continuation ->
    val modality = continuation.context.contextModality()
    SwingUtilities.invokeLater {
      if (modality == null) {
        continuation.resume(Unit)
      }
      else {
        ApplicationManager.getApplication().invokeLater({ continuation.resume(Unit) }, modality)
      }
    }
  }
}

