// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.util.Computable
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.Obsolete

/**
 * Use [writeAction].
 */
@Obsolete
fun <T> runWriteAction(runnable: () -> T): T {
  return ApplicationManager.getApplication().runWriteAction(Computable(runnable))
}

fun <T> runUndoTransparentWriteAction(runnable: () -> T): T {
  return CommandProcessor.getInstance().withUndoTransparentAction().use {
    ApplicationManager.getApplication().runWriteAction(Computable(runnable))
  }
}

/**
 * Use [readAction].
 */
fun <T> runReadAction(runnable: () -> T): T {
  return ApplicationManager.getApplication().runReadAction(Computable(runnable))
}

/**
 * @suppress Internal use only
 */
@Internal
fun <T> invokeAndWaitIfNeeded(modalityState: ModalityState? = null, runnable: () -> T): T {
  val app = ApplicationManager.getApplication()
  if (app.isDispatchThread) {
    return runnable()
  }
  else {
    var resultRef: T? = null
    app.invokeAndWait({ resultRef = runnable() }, modalityState ?: ModalityState.defaultModalityState())
    @Suppress("UNCHECKED_CAST")
    return resultRef as T
  }
}

fun runInEdt(modalityState: ModalityState? = null, runnable: () -> Unit) {
  val app = ApplicationManager.getApplication()
  if (app.isDispatchThread) {
    runnable()
  }
  else {
    invokeLater(modalityState, runnable)
  }
}

@Obsolete
fun invokeLater(modalityState: ModalityState? = null, runnable: () -> Unit) {
  ApplicationManager.getApplication().invokeLater({ runnable() }, modalityState ?: ModalityState.defaultModalityState())
}
