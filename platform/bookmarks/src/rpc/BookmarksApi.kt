// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.bookmarks.rpc

import com.intellij.openapi.editor.impl.EditorId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@Rpc
interface BookmarksApi: RemoteApi<Unit> {
  suspend fun addBookmark(projectId: ProjectId, editorId: EditorId, line: Int )

  companion object {
    @JvmStatic
    suspend fun getInstance(): BookmarksApi {
      return RemoteApiProviderService.Companion.resolve(remoteApiDescriptor<BookmarksApi>())
    }
  }
}