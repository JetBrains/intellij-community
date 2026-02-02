// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events.impl

import com.intellij.build.BuildDescriptor
import com.intellij.build.FilePosition
import com.intellij.build.eventBuilders.impl.BuildIssueEventBuilderImpl
import com.intellij.build.eventBuilders.impl.FileDownloadEventBuilderImpl
import com.intellij.build.eventBuilders.impl.FileDownloadedEventBuilderImpl
import com.intellij.build.eventBuilders.impl.FileMessageEventBuilderImpl
import com.intellij.build.eventBuilders.impl.FinishBuildEventBuilderImpl
import com.intellij.build.eventBuilders.impl.FinishEventBuilderImpl
import com.intellij.build.eventBuilders.impl.MessageEventBuilderImpl
import com.intellij.build.eventBuilders.impl.OutputBuildEventBuilderImpl
import com.intellij.build.eventBuilders.impl.PresentableBuildEventBuilderImpl
import com.intellij.build.eventBuilders.impl.ProgressBuildEventBuilderImpl
import com.intellij.build.eventBuilders.impl.StartBuildEventBuilderImpl
import com.intellij.build.eventBuilders.impl.StartEventBuilderImpl
import com.intellij.build.events.BuildEventPresentationData
import com.intellij.build.events.BuildEvents
import com.intellij.build.events.BuildEventsNls.Message
import com.intellij.build.events.EventResult
import com.intellij.build.events.MessageEvent
import com.intellij.build.issue.BuildIssue

internal class BuildEventsImpl : BuildEvents {

  override fun startBuild(
    message: @Message String,
    buildDescriptor: BuildDescriptor,
  ) = StartBuildEventBuilderImpl(message, buildDescriptor)

  override fun finishBuild(
    startBuildId: Any,
    message: @Message String,
    result: EventResult,
  ) = FinishBuildEventBuilderImpl(startBuildId, message, result)

  override fun start(
    id: Any,
    message: @Message String,
  ) = StartEventBuilderImpl(id, message)

  override fun finish(
    startId: Any,
    message: @Message String,
    result: EventResult,
  ) = FinishEventBuilderImpl(startId, message, result)

  override fun output(
    message: @Message String,
  ) = OutputBuildEventBuilderImpl(message)

  override fun progress(
    startId: Any,
    message: @Message String,
  ) = ProgressBuildEventBuilderImpl(startId, message)

  override fun message(
    message: @Message String,
    kind: MessageEvent.Kind,
  ) = MessageEventBuilderImpl(message, kind)

  override fun fileMessage(
    message: @Message String,
    kind: MessageEvent.Kind,
    filePosition: FilePosition,
  ) = FileMessageEventBuilderImpl(message, kind, filePosition)

  override fun buildIssue(
    issue: BuildIssue,
    kind: MessageEvent.Kind,
  ) = BuildIssueEventBuilderImpl(issue, kind)

  override fun fileDownload(
    startId: Any,
    message: @Message String,
    isFirstInGroup: Boolean,
    downloadPath: String,
  ) = FileDownloadEventBuilderImpl(startId, message, isFirstInGroup, downloadPath)

  override fun fileDownloaded(
    startId: Any,
    message: @Message String,
    duration: Long,
    downloadPath: String,
  ) = FileDownloadedEventBuilderImpl(startId, message, duration, downloadPath)

  override fun presentable(
    message: @Message String,
    presentationData: BuildEventPresentationData,
  ) = PresentableBuildEventBuilderImpl(message, presentationData)
}