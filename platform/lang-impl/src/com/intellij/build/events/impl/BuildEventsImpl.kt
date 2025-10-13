// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events.impl

import com.intellij.build.eventBuilders.impl.*
import com.intellij.build.events.BuildEvents

private class BuildEventsImpl : BuildEvents {

  override fun startBuild() = StartBuildEventBuilderImpl()

  override fun finishBuild() = FinishBuildEventBuilderImpl()

  override fun start() = StartEventBuilderImpl()

  override fun finish() = FinishEventBuilderImpl()

  override fun output() = OutputBuildEventBuilderImpl()

  override fun progress() = ProgressBuildEventBuilderImpl()

  override fun message() = MessageEventBuilderImpl()

  override fun fileMessage() = FileMessageEventBuilderImpl()

  override fun buildIssue() = BuildIssueEventBuilderImpl()

  override fun fileDownload() = FileDownloadEventBuilderImpl()

  override fun fileDownloaded() = FileDownloadedEventBuilderImpl()

  override fun presentable() = PresentableBuildEventBuilderImpl()
}