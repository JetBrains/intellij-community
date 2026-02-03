// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.eventBuilders.impl

import com.intellij.build.eventBuilders.ProgressBuildEventBuilder
import com.intellij.build.events.BuildEventsNls.Description
import com.intellij.build.events.BuildEventsNls.Hint
import com.intellij.build.events.BuildEventsNls.Message
import com.intellij.build.events.impl.ProgressBuildEventImpl
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class ProgressBuildEventBuilderImpl(
  private val startId: Any,
  private val message: @Message String
) : ProgressBuildEventBuilder {

  private var parentId: Any? = null
  private var time: Long? = null
  private var hint: @Hint String? = null
  private var description: @Description String? = null

  private var total: Long? = null
  private var progress: Long? = null
  private var unit: String? = null

  override fun withParentId(parentId: Any?): ProgressBuildEventBuilderImpl =
    apply { this.parentId = parentId }

  override fun withTime(time: Long?): ProgressBuildEventBuilderImpl =
    apply { this.time = time }

  override fun withHint(hint: @Hint String?): ProgressBuildEventBuilderImpl =
    apply { this.hint = hint }

  override fun withDescription(description: @Description String?): ProgressBuildEventBuilderImpl =
    apply { this.description = description }

  override fun withTotal(total: Long?): ProgressBuildEventBuilderImpl =
    apply { this.total = total }

  override fun withProgress(progress: Long?): ProgressBuildEventBuilderImpl =
    apply { this.progress = progress }

  override fun withUnit(unit: String?): ProgressBuildEventBuilderImpl =
    apply { this.unit = unit }

  override fun build(): ProgressBuildEventImpl =
    ProgressBuildEventImpl(startId, parentId, time, message, hint, description, total, progress, unit)
}
