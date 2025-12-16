// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.vcs.impl.shared.RepositoryId
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import java.awt.Color

@Rpc
@ApiStatus.Internal
interface VcsRepositoryColorsApi : RemoteApi<Unit> {
  suspend fun syncColors(projectId: ProjectId): Flow<VcsRepositoryColorsState>

  companion object {
    suspend fun getInstance(): VcsRepositoryColorsApi =
      RemoteApiProviderService.resolve(remoteApiDescriptor<VcsRepositoryColorsApi>())
  }
}

/**
 * Bring both bright and dark colors not to handle LaF changes
 */
@JvmInline
@ApiStatus.Internal
@Serializable
value class VcsRepositoryColor(val rgb: Int) {
  companion object {
    fun of(color: Color): VcsRepositoryColor = VcsRepositoryColor(color.rgb)

    fun VcsRepositoryColor.toAwtColor(): Color = Color(rgb)
  }
}

@ApiStatus.Internal
@Serializable
data class VcsRepositoryColorsState(
  val colors: Map<RepositoryId, VcsRepositoryColor> = emptyMap(),
)
