// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events.impl

import com.intellij.build.eventBuilders.impl.*
import com.intellij.build.events.BuildEvents
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class BuildEventsImpl : BuildEvents {

  override fun startBuild(): StartBuildEventBuilderImpl = StartBuildEventBuilderImpl()

  override fun finishBuild(): FinishBuildEventBuilderImpl = FinishBuildEventBuilderImpl()

  override fun start(): StartEventBuilderImpl = StartEventBuilderImpl()

  override fun finish(): FinishEventBuilderImpl = FinishEventBuilderImpl()

  override fun output(): OutputBuildEventBuilderImpl = OutputBuildEventBuilderImpl()

  override fun progress(): ProgressBuildEventBuilderImpl = ProgressBuildEventBuilderImpl()

  override fun message(): MessageEventBuilderImpl = MessageEventBuilderImpl()

  override fun fileMessage(): FileMessageEventBuilderImpl = FileMessageEventBuilderImpl()

  override fun buildIssue(): BuildIssueEventBuilderImpl = BuildIssueEventBuilderImpl()

  override fun fileDownload(): FileDownloadEventBuilderImpl = FileDownloadEventBuilderImpl()

  override fun fileDownloaded(): FileDownloadedEventBuilderImpl = FileDownloadedEventBuilderImpl()

  override fun presentable(): PresentableBuildEventBuilderImpl = PresentableBuildEventBuilderImpl()
}