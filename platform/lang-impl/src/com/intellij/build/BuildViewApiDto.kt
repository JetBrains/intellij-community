// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build

import com.intellij.build.events.BuildEventsNls
import com.intellij.ide.ui.icons.IconId
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.rpc.Id
import com.intellij.platform.rpc.UID
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@Serializable
sealed interface BuildViewEvent {
  val buildId: BuildId

  @Serializable
  data class BuildStarted(
    val content: BuildContent,
    override val buildId: BuildId,
    val title: @BuildEventsNls.Title String,
    val startTime: Long,
    val message: @BuildEventsNls.Message String,
    val requestFocus: Boolean,
    val activateToolWindow: Boolean,
  ) : BuildViewEvent

  @Serializable
  data class BuildRemoved(
    override val buildId: BuildId,
  ) : BuildViewEvent

  @Serializable
  data class BuildSelected(
    override val buildId: BuildId,
  ) : BuildViewEvent

  @Serializable
  data class BuildStatusChanged(
    override val buildId: BuildId,
    val message: String,
  ) : BuildViewEvent

  @Serializable
  data class BuildFinished(
    override val buildId: BuildId,
    val message: String,
    val iconId: IconId,
    val selectContent: Boolean,
    val activateToolWindow: Boolean,
    val notification: BuildNotification?,
  ) : BuildViewEvent
}

@Internal
@Serializable
data class BuildContent(
  val id: BuildContentId,
  val categoryId: BuildCategoryId,
  val name: @NlsContexts.TabTitle String,
  val isPinned: Boolean,
)

@Internal
@Serializable
data class BuildContentId(override val uid: UID) : Id

typealias BuildCategoryId = Int

@Internal
@Serializable
data class BuildId(override val uid: UID) : Id

@Internal
@Serializable
data class BuildNotification(
  val title: @NlsContexts.SystemNotificationTitle String,
  val content: @NlsContexts.SystemNotificationText String,
)