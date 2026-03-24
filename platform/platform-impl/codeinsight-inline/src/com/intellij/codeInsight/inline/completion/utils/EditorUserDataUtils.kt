// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.utils

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.util.Key
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.annotations.ApiStatus

/**
 * Guarantees that [compute] is called only once per [Editor] for the specified [key].
 *
 * If the stored value is [Disposable], it is automatically disposed together with the editor.
 */
@ApiStatus.Internal
fun <R : Any> Editor.storeInUserDataHolderByTheKey(key: Key<R>, compute: () -> R): R {
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

/**
 * Wrapper for editor-bound child [CoroutineScope].
 *
 * The child scope is a supervisor scope and is canceled on editor disposal.
 */
@ApiStatus.Internal
class EditorDisposableCoroutineScope(parentScope: CoroutineScope) : Disposable {
  private val scopeJob = SupervisorJob(parentScope.coroutineContext[Job])

  @Suppress("RAW_SCOPE_CREATION")
  val scope: CoroutineScope = CoroutineScope(parentScope.coroutineContext + scopeJob)

  override fun dispose() {
    scopeJob.cancel()
  }
}
