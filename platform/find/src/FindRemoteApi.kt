// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.find

import com.intellij.find.FindModel
import com.intellij.ide.ui.SerializableTextChunk
import com.intellij.ide.ui.colors.ColorId
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.ui.SimpleTextAttributes
import com.intellij.usages.TextChunk
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color

@ApiStatus.Internal
@Rpc
interface FindRemoteApi : RemoteApi<Unit> {

  suspend fun findByModel(findModel: FindModel, projectId: ProjectId, filesToScanInitially: List<VirtualFileId>): Flow<FindInFilesResult>

  companion object {
    @JvmStatic
    suspend fun getInstance(): FindRemoteApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<FindRemoteApi>())
    }
  }
}

@Serializable
data class FindInFilesResult(
  val presentation: List<SerializableTextChunk>,
  val line: Int,
  val offset: Int,
  val originalOffset: Int,
  val length: Int,
  val originalLength: Int,
  val fileId: VirtualFileId,
  @param:NlsSafe val presentablePath: String,
  val merged: Boolean,
  val backgroundColor: ColorId?,
)
