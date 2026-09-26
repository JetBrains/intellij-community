// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.impl.EditorId
import com.intellij.openapi.editor.impl.findEditorOrNull
import com.intellij.platform.rpc.lite.LiteRemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * Reports that the user hid a question hint, such as the auto-import popup.
 *
 * Split mode only. The frontend shows the hint, and the backend keeps the decision, so the frontend
 * calls the backend implementation. A monolith needs no call, because
 * [com.intellij.codeInsight.hint.HintManager] marks the same hint in the process which shows it.
 *
 * @see com.intellij.ui.LightweightHint.isDismissedByEscape
 */
@ApiStatus.Internal
@Rpc
interface QuestionHintApi : RemoteApi<Unit> {

  /**
   * Marks the question hint of this editor as dismissed by the user, then hides that hint.
   * The call does nothing when the editor is gone, or when the editor shows no question hint.
   */
  suspend fun dismiss(editorId: EditorId)

  companion object {
    /** Only a split frontend calls this, and it always has a backend to await. */
    @JvmStatic
    suspend fun getInstance(): QuestionHintApi {
      return LiteRemoteApiProviderService.awaitConnectionAndResolve(remoteApiDescriptor<QuestionHintApi>())
    }
  }
}

/**
 * Serves [QuestionHintApi] on the remote-dev backend. The backend module also registers it for the
 * monolith, where nothing calls it.
 */
@ApiStatus.Internal
class QuestionHintApiImpl : QuestionHintApi {
  override suspend fun dismiss(editorId: EditorId) {
    val editor = editorId.findEditorOrNull() ?: return
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        HintManagerImpl.getInstanceImpl().dismissCurrentQuestionHint(editor)
      }
    }
  }
}
