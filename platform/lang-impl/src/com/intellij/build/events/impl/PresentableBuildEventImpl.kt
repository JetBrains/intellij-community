// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events.impl

import com.intellij.build.events.BuildEventPresentationData
import com.intellij.build.events.BuildEventsNls.Description
import com.intellij.build.events.BuildEventsNls.Hint
import com.intellij.build.events.BuildEventsNls.Message
import com.intellij.build.events.PresentableBuildEvent
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class PresentableBuildEventImpl(
  id: Any?,
  parentId: Any?,
  time: Long?,
  message: @Message String,
  hint: @Hint String?,
  description: @Description String?,
  private val presentableData: BuildEventPresentationData,
) : AbstractBuildEvent(id, parentId, time, message, hint, description),
    PresentableBuildEvent {

  override fun getPresentationData(): BuildEventPresentationData {
    return presentableData
  }
}
