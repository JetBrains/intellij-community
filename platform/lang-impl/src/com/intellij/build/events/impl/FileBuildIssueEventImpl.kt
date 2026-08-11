// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events.impl

import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEventsNls.Hint
import com.intellij.build.events.FileMessageEvent
import com.intellij.build.events.FileMessageEventResult
import com.intellij.build.events.MessageEvent
import com.intellij.build.issue.BuildIssue

internal class FileBuildIssueEventImpl(
  id: Any?,
  parentId: Any?,
  time: Long?,
  hint: @Hint String?,
  issue: BuildIssue,
  kind: MessageEvent.Kind,
  private val filePosition: FilePosition,
) : BuildIssueEventImpl(id, parentId, time, hint, issue, kind),
    FileMessageEvent {

  override fun getFilePosition(): FilePosition = filePosition

  override fun getResult(): FileMessageEventResult {
    val messageResult = super.getResult()
    return object : FileMessageEventResult {
      override fun getFilePosition() = this@FileBuildIssueEventImpl.filePosition
      override fun getKind() = messageResult.kind
      override fun getDetails() = messageResult.details
    }
  }
}
