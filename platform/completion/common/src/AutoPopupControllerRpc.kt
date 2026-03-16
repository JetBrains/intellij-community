// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common

import com.intellij.openapi.editor.impl.EditorId
import com.intellij.platform.completion.common.protocol.RpcCompletionItemId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus

/**
 * This is a hack for Java plugin
 * Available in split mode only, do not try to use in monolith mode.
 */
@ApiStatus.Internal
@Rpc
interface AutoPopupControllerRpc : RemoteApi<Unit> {
  suspend fun autoPopupParameterInfo(editorId: EditorId, projectId: ProjectId, item: RpcCompletionItemId?)

  companion object {
    @JvmStatic
    suspend fun getInstance(): AutoPopupControllerRpc {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<AutoPopupControllerRpc>())
    }
  }
}