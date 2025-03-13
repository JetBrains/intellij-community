// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.fus

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.vfs.impl.http.RemoteFileState

internal object JsonHttpFileLoadingUsageCollector : CounterUsagesCollector() {
  private val jsonHttpFileResolveGroup = EventLogGroup(
    id = "json.http.file.resolve",
    version = 1,
  )

  internal val jsonSchemaHighlightingSessionData =
    jsonHttpFileResolveGroup.registerVarargEvent(
      "json.schema.highlighting.session.finished",
      JsonHttpFileNioFile,
      JsonHttpFileNioFileCanBeRead,
      JsonHttpFileNioFileLength,
      JsonHttpFileVfsFile,
      JsonHttpFileSyncRefreshVfsFile,
      JsonHttpFileVfsFileValidity,
      JsonHttpFileDownloadState
    )

  override fun getGroup(): EventLogGroup = jsonHttpFileResolveGroup
}

internal val JsonHttpFileNioFile = EventFields.Boolean("nio_file_resolve_status", "Remote schema found via nio.file api")
internal val JsonHttpFileNioFileCanBeRead = EventFields.Boolean("nio_file_can_read_status", "Remote schema found via nio.file api can be read")
internal val JsonHttpFileNioFileLength = EventFields.RoundedLong("nio_file_length_status", "Remote schema found via nio.file api length")
internal val JsonHttpFileVfsFile = EventFields.Boolean("vfs_file_resolve_status", "Remote schema found via VFS api")
internal val JsonHttpFileSyncRefreshVfsFile = EventFields.Boolean("vfs_refresh_file_resolve_status", "Remote schema found via VFS api after explicit synchronous refresh")
internal val JsonHttpFileVfsFileValidity = EventFields.Boolean("vfs_file_validity_status", "Remote schema VFS file validity")
internal val JsonHttpFileDownloadState = EventFields.Enum<JsonRemoteSchemaDownloadState>("http_file_download_status", "Remote file download state")

internal enum class JsonRemoteSchemaDownloadState {
  DOWNLOADING_NOT_STARTED,
  DOWNLOADING_IN_PROGRESS,
  DOWNLOADED,
  ERROR_OCCURRED,
  NO_STATE;

  companion object {
    fun fromRemoteFileState(state: RemoteFileState?): JsonRemoteSchemaDownloadState {
      return when (state) {
        null -> NO_STATE
        RemoteFileState.DOWNLOADING_NOT_STARTED -> DOWNLOADING_NOT_STARTED
        RemoteFileState.DOWNLOADING_IN_PROGRESS -> DOWNLOADING_IN_PROGRESS
        RemoteFileState.DOWNLOADED -> DOWNLOADED
        RemoteFileState.ERROR_OCCURRED -> ERROR_OCCURRED
      }
    }
  }
}
