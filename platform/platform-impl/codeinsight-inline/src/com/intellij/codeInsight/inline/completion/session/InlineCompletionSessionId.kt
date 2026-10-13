// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.session

import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import fleet.util.UID
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

private val INLINE_COMPLETION_SESSION_ID_KEY = Key.create<InlineCompletionSessionId>("inline.completion.session.id")

@Serializable
@ApiStatus.Internal
data class InlineCompletionSessionId(private val id: UID) {
  companion object {
    fun create(): InlineCompletionSessionId {
      return InlineCompletionSessionId(UID.random())
    }
  }
}

@RequiresEdt
@ApiStatus.Internal
fun InlineCompletionSession.getIdOrNull(): InlineCompletionSessionId? {
  return editor.getUserData(INLINE_COMPLETION_SESSION_ID_KEY)
}

@RequiresEdt
@ApiStatus.Internal
fun InlineCompletionSession.getId(): InlineCompletionSessionId {
  return getIdOrNull() ?: error("Inline completion session ID is not set")
}

@RequiresEdt
internal fun InlineCompletionSession.putId(): InlineCompletionSessionId {
  return putId(InlineCompletionSessionId.create())
}

@RequiresEdt
internal fun InlineCompletionSession.putId(id: InlineCompletionSessionId): InlineCompletionSessionId {
  ThreadingAssertions.assertEventDispatchThread()
  check(!context.isDisposed)
  check(editor.getUserData(INLINE_COMPLETION_SESSION_ID_KEY) == null) { "Inline completion session ID is already set" }
  editor.putUserData(INLINE_COMPLETION_SESSION_ID_KEY, id)
  whenDisposed {
    editor.putUserData(INLINE_COMPLETION_SESSION_ID_KEY, null)
  }
  return id
}
