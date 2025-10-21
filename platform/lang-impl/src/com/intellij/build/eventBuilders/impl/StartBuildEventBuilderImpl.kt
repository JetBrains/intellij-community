// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.eventBuilders.impl

import com.intellij.build.BuildDescriptor
import com.intellij.build.BuildViewSettingsProvider
import com.intellij.build.eventBuilders.StartBuildEventBuilder
import com.intellij.build.events.BuildEventsNls.Description
import com.intellij.build.events.BuildEventsNls.Hint
import com.intellij.build.events.BuildEventsNls.Message
import com.intellij.build.events.impl.StartBuildEventImpl
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class StartBuildEventBuilderImpl : StartBuildEventBuilder {

  private var parentId: Any? = null
  private var message: @Message String? = null
  private var hint: @Hint String? = null
  private var description: @Description String? = null

  private var buildDescriptor: BuildDescriptor? = null
  private var buildViewSettings: BuildViewSettingsProvider? = null

  override fun withParentId(parentId: Any?): StartBuildEventBuilderImpl =
    apply { this.parentId = parentId }

  override fun withMessage(message: @Message String): StartBuildEventBuilderImpl =
    apply { this.message = message }

  override fun withHint(hint: @Hint String?): StartBuildEventBuilderImpl =
    apply { this.hint = hint }

  override fun withDescription(description: @Description String?): StartBuildEventBuilderImpl =
    apply { this.description = description }

  override fun withBuildDescriptor(buildDescriptor: BuildDescriptor): StartBuildEventBuilderImpl =
    apply { this.buildDescriptor = buildDescriptor }

  override fun withBuildViewSettings(buildViewSettings: BuildViewSettingsProvider?): StartBuildEventBuilderImpl =
    apply { this.buildViewSettings = buildViewSettings }

  override fun build(): StartBuildEventImpl =
    StartBuildEventImpl(
      parentId,
      message ?: throw IllegalStateException("The StartBuildEvent's 'message' property should be defined"),
      hint,
      description,
      buildDescriptor ?: throw IllegalStateException("The StartBuildEvent's 'buildDescriptor' property should be defined"),
      buildViewSettings
    )
}
