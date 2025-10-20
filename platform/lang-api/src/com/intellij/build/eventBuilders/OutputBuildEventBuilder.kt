// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.eventBuilders

import com.intellij.build.events.BuildEventsNls.*
import com.intellij.build.events.OutputBuildEvent
import com.intellij.execution.process.ProcessOutputType
import org.jetbrains.annotations.ApiStatus.NonExtendable
import org.jetbrains.annotations.CheckReturnValue

@NonExtendable
interface OutputBuildEventBuilder {

  @CheckReturnValue
  fun withId(id: Any?): OutputBuildEventBuilder

  @CheckReturnValue
  fun withParentId(parentId: Any?): OutputBuildEventBuilder

  @CheckReturnValue
  fun withTime(time: Long?): OutputBuildEventBuilder

  @CheckReturnValue // mandatory field
  fun withMessage(message: @Message String): OutputBuildEventBuilder

  @CheckReturnValue
  fun withHint(hint: @Hint String?): OutputBuildEventBuilder

  @CheckReturnValue
  fun withDescription(description: @Description String?): OutputBuildEventBuilder

  @CheckReturnValue
  fun withOutputType(outputType: ProcessOutputType?): OutputBuildEventBuilder

  fun build(): OutputBuildEvent
}
