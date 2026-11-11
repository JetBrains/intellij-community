// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import java.awt.event.KeyListener
import javax.swing.JComponent

/**
 * Adds [listener] and removes it when [coroutineScope] is cancelled.
 * A scope that is cancelled already removes the listener at once.
 */
internal fun Editor.addEditorMouseListener(listener: EditorMouseListener, coroutineScope: CoroutineScope) {
  addEditorMouseListener(listener)
  coroutineScope.coroutineContext.job.invokeOnCompletion { removeEditorMouseListener(listener) }
}

/**
 * Adds [listener] and removes it when [coroutineScope] is cancelled.
 * A scope that is cancelled already removes the listener at once.
 */
internal fun Editor.addEditorMouseMotionListener(listener: EditorMouseMotionListener, coroutineScope: CoroutineScope) {
  addEditorMouseMotionListener(listener)
  coroutineScope.coroutineContext.job.invokeOnCompletion { removeEditorMouseMotionListener(listener) }
}

/**
 * Adds [listener] and removes it when [coroutineScope] is cancelled.
 * A scope that is cancelled already removes the listener at once.
 */
internal fun JComponent.addKeyListener(listener: KeyListener, coroutineScope: CoroutineScope) {
  addKeyListener(listener)
  coroutineScope.coroutineContext.job.invokeOnCompletion { removeKeyListener(listener) }
}
