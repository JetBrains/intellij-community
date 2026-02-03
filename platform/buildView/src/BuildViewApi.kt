// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildView

import com.intellij.build.BuildContentId
import com.intellij.build.BuildId
import com.intellij.build.BuildViewEvent
import com.intellij.frontend.FrontendApplicationInfo
import com.intellij.frontend.FrontendType
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * An API which is used to construct Build tool window content on the frontend.
 */
@Internal
@Rpc
interface BuildViewApi : RemoteApi<Unit> {
  /**
   * Reports builds added/removed and the changes in their properties.
   *
   * This should retrospectively report events (potentially conflated) for existing (non-deleted) builds
   */
  suspend fun getBuildViewEventsFlow(projectId: ProjectId): Flow<BuildViewEvent>

  /**
   * Used to report user actions with respect to pinning/unpinning of the build content in the tool window. This impacts whether the same
   * content will be used to display new builds in the same category - if the currently active content is pinned,
   * and a new build is started, the content is 'locked' and a new content is created to display the build, otherwise the active content
   * will be used to display the new build.
   */
  suspend fun setBuildContentPinned(buildContentId: BuildContentId, pinned: Boolean)

  /**
   * Used to report corresponding content being closed in the tool window on the frontend by the user.
   */
  suspend fun disposeBuildContent(buildContentId: BuildContentId)

  /**
   * Used to notify the backend about tool window activation, if it was requested in a [BuildViewEvent.BuildStarted] event.
   */
  suspend fun notifyTooWindowActivated(buildId: BuildId)

  companion object {
    suspend fun getInstance(): BuildViewApi {
      val isCWM = (FrontendApplicationInfo.getFrontendType() as? FrontendType.Remote)?.isGuest() == true
      return if (isCWM) BuildViewApiImpl else RemoteApiProviderService.resolve(remoteApiDescriptor<BuildViewApi>())
    }
  }
}