// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.eventBuilders

import com.intellij.build.BuildViewSettingsProvider
import com.intellij.build.events.BuildEventsNls.Description
import com.intellij.build.events.BuildEventsNls.Hint
import com.intellij.build.events.StartBuildEvent
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.NonExtendable
import org.jetbrains.annotations.CheckReturnValue

@NonExtendable
interface StartBuildEventBuilder {

  @CheckReturnValue
  fun withParentId(parentId: Any?): StartBuildEventBuilder

  @CheckReturnValue
  fun withHint(hint: @Hint String?): StartBuildEventBuilder

  @CheckReturnValue
  fun withDescription(description: @Description String?): StartBuildEventBuilder

  @Experimental
  @CheckReturnValue
  fun withBuildViewSettings(buildViewSettings: BuildViewSettingsProvider?): StartBuildEventBuilder

  fun build(): StartBuildEvent
}
