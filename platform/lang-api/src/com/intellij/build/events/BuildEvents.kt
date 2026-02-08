// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events

import com.intellij.build.BuildDescriptor
import com.intellij.build.FilePosition
import com.intellij.build.eventBuilders.BuildIssueEventBuilder
import com.intellij.build.eventBuilders.FileDownloadEventBuilder
import com.intellij.build.eventBuilders.FileDownloadedEventBuilder
import com.intellij.build.eventBuilders.FileMessageEventBuilder
import com.intellij.build.eventBuilders.FinishBuildEventBuilder
import com.intellij.build.eventBuilders.FinishEventBuilder
import com.intellij.build.eventBuilders.MessageEventBuilder
import com.intellij.build.eventBuilders.OutputBuildEventBuilder
import com.intellij.build.eventBuilders.PresentableBuildEventBuilder
import com.intellij.build.eventBuilders.ProgressBuildEventBuilder
import com.intellij.build.eventBuilders.StartBuildEventBuilder
import com.intellij.build.eventBuilders.StartEventBuilder
import com.intellij.build.events.BuildEventsNls.Message
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.components.service
import com.intellij.util.application
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface BuildEvents {

  fun startBuild(
    message: @Message String,
    buildDescriptor: BuildDescriptor,
  ): StartBuildEventBuilder

  fun finishBuild(
    startBuildId: Any,
    message: @Message String,
    result: EventResult,
  ): FinishBuildEventBuilder

  fun start(
    id: Any,
    message: @Message String,
  ): StartEventBuilder

  fun finish(
    startId: Any,
    message: @Message String,
    result: EventResult,
  ): FinishEventBuilder

  fun output(
    message: @Message String,
  ): OutputBuildEventBuilder

  fun progress(
    startId: Any,
    message: @Message String,
  ): ProgressBuildEventBuilder

  fun message(
    message: @Message String,
    kind: MessageEvent.Kind,
  ): MessageEventBuilder

  fun fileMessage(
    message: @Message String,
    kind: MessageEvent.Kind,
    filePosition: FilePosition,
  ): FileMessageEventBuilder

  fun buildIssue(
    issue: BuildIssue,
    kind: MessageEvent.Kind,
  ): BuildIssueEventBuilder

  fun fileDownload(
    startId: Any,
    message: @Message String,
    isFirstInGroup: Boolean,
    downloadPath: String,
  ): FileDownloadEventBuilder

  fun fileDownloaded(
    startId: Any,
    message: @Message String,
    duration: Long,
    downloadPath: String,
  ): FileDownloadedEventBuilder

  fun presentable(
    message: @Message String,
    presentationData: BuildEventPresentationData,
  ): PresentableBuildEventBuilder

  companion object {

    @JvmStatic
    fun getInstance(): BuildEvents {
      return application.service()
    }
  }
}