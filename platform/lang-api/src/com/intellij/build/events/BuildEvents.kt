// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events

import com.intellij.build.eventBuilders.*
import com.intellij.openapi.components.service
import com.intellij.util.application
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface BuildEvents {

  fun startBuild(): StartBuildEventBuilder

  fun finishBuild(): FinishBuildEventBuilder

  fun start(): StartEventBuilder

  fun finish(): FinishEventBuilder

  fun output(): OutputBuildEventBuilder

  fun progress(): ProgressBuildEventBuilder

  fun message(): MessageEventBuilder

  fun fileMessage(): FileMessageEventBuilder

  fun buildIssue(): BuildIssueEventBuilder

  fun fileDownload(): FileDownloadEventBuilder

  fun fileDownloaded(): FileDownloadedEventBuilder

  fun presentable(): PresentableBuildEventBuilder

  companion object {

    @JvmStatic
    fun getInstance(): BuildEvents {
      return application.service()
    }
  }
}