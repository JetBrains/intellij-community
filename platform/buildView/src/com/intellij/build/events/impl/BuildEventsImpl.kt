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
import com.intellij.build.eventBuilders.impl.OutputReferenceEventBuilderImpl
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
import com.intellij.build.events.OutputId
import com.intellij.build.events.StartBuildId
import com.intellij.build.events.StartId

internal class BuildEventsImpl : BuildEvents {

  override fun startBuild(
    message: @Message String,
    buildDescriptor: BuildDescriptor,
  ) = StartBuildEventBuilderImpl(message, buildDescriptor)

  override fun finishBuild(
    startBuildId: StartBuildId,
    message: @Message String,
    result: EventResult,
  ) = FinishBuildEventBuilderImpl(startBuildId, message, result)

  override fun start(
    id: StartId,
    message: @Message String,
  ) = StartEventBuilderImpl(id, message)

  override fun finish(
    startId: StartId,
    message: @Message String,
    result: EventResult,
  ) = FinishEventBuilderImpl(startId, message, result)

  override fun output(
    message: @Message String,
  ) = OutputBuildEventBuilderImpl(message)

  override fun outputReference(
    startId: StartId,
    outputIds: List<OutputId>,
  ) = OutputReferenceEventBuilderImpl(startId, outputIds)

  override fun progress(
    startId: StartId,
    message: @Message String,
  ) = ProgressBuildEventBuilderImpl(startId, message)

  override fun message(
    message: @Message String,
    kind: MessageEvent.Kind,
  ) = MessageEventBuilderImpl(message, kind)

  @Deprecated("Use [BuildEvents.message] instead.", replaceWith = ReplaceWith("message(message, kind).withFilePosition(filePosition)"))
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
    startId: StartId,
    message: @Message String,
    isFirstInGroup: Boolean,
    downloadPath: String,
  ) = FileDownloadEventBuilderImpl(startId, message, isFirstInGroup, downloadPath)

  override fun fileDownloaded(
    startId: StartId,
    message: @Message String,
    duration: Long,
    downloadPath: String,
  ) = FileDownloadedEventBuilderImpl(startId, message, duration, downloadPath)

  override fun presentable(
    message: @Message String,
    presentationData: BuildEventPresentationData,
  ) = PresentableBuildEventBuilderImpl(message, presentationData)
}