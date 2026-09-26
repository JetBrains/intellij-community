// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.util.Key
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class AbstractEditorTrackingService(
  private val parentScope: CoroutineScope,
  private val editorScopeKey: Key<EditorTrackingScope>,
) {
  /**
   * Reuses one child scope per editor for delayed tracking jobs.
   *
   * The scope is stored in editor user data and canceled on editor disposal,
   * so delayed jobs from long-lived services do not retain editor references.
   */
  protected fun getOrCreateEditorScope(editor: Editor): CoroutineScope {
    return editor.storeInUserDataHolderByTheKey(editorScopeKey) {
      EditorTrackingScope(parentScope)
    }.scope
  }

  /**
   * Guarantees that [compute] is called only once per editor for the provided [key].
   *
   * Disposable values are registered for disposal together with the editor.
   *
   * Inspired by com.intellij.ml.llm.nextEdits.common.util.EditorUtilKt.storeInUserDataHolderByTheKey
   */
  private fun <R : Any> Editor.storeInUserDataHolderByTheKey(key: Key<R>, compute: () -> R): R {
    getUserData(key)?.let { return it }
    val (value, isCreated) = synchronized(this) {
      getUserData(key)?.let { return@synchronized it to false }
      val computed = compute()
      putUserData(key, computed)
      computed to true
    }

    if (isCreated && value is Disposable) {
      EditorUtil.disposeWithEditor(this, value)
    }

    return value
  }
}

@ApiStatus.Internal
class EditorTrackingScope(parentScope: CoroutineScope) : Disposable {
  private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
    if (throwable is CancellationException) return@CoroutineExceptionHandler
    LOG.warn("Editor tracking coroutine failed", throwable)
  }

  val scope: CoroutineScope = parentScope.childScope("EditorTrackingScope", context = exceptionHandler, supervisor = true)

  override fun dispose() {
    scope.cancel()
  }

  companion object {
    private val LOG = logger<EditorTrackingScope>()
  }
}
