// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.find

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.Nls
import java.awt.Color

@ApiStatus.Internal
@Rpc
interface FindRemoteApi : RemoteApi<Unit> {

  suspend fun findByModel(model: FindInProjectModel): Flow<FindInProjectResult>


  suspend fun navigate(request: RdFindInProjectNavigation)

  companion object {
    @JvmStatic
    suspend fun getInstance(): FindRemoteApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<FindRemoteApi>())
    }
  }
}

@Serializable
data class FindInProjectModel (
  val projectId: ProjectId,
  val stringToFind: String,
  val isWholeWordsOnly: Boolean,
  val isRegularExpressions: Boolean,
  val isCaseSensitive: Boolean,
  val isProjectScope: Boolean,
  val fileFilter: String?,
  val moduleName: String?,
  val searchContext: String,
  val scopeId: Int?
)

@Serializable
data class FindInProjectResult (
  val presentation: List<RdTextChunk>,
  val line: Int?,
  val offset: Int,
  val length: Int,
  val fileId: VirtualFileId,
  val path: String
)

@Serializable
data class RdTextChunk (
  val text: @Nls String,
  val attributes: RdSimpleTextAttributes
)

@Serializable
data class RdSimpleTextAttributes (
  val fgColor: Int? = null,
  val bgColor: Int? = null,
  val waveColor: Int? = null,
  val style: Int = 0
)

@Serializable
data class RdFindInProjectNavigation (
  val fileId: VirtualFileId,
  val offset: Int,
  val requestFocus: Boolean
)