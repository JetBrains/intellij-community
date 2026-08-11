// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.eventBuilders.impl

import com.intellij.build.FilePosition
import com.intellij.build.eventBuilders.BuildIssueEventBuilder
import com.intellij.build.events.BuildEventsNls.Hint
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.events.impl.FileBuildIssueEventImpl
import com.intellij.build.issue.BuildIssue

internal class BuildIssueEventBuilderImpl(
  private val issue: BuildIssue,
  private val kind: MessageEvent.Kind,
) : BuildIssueEventBuilder {

  private var id: Any? = null
  private var parentId: Any? = null
  private var time: Long? = null
  private var hint: @Hint String? = null

  private var filePosition: FilePosition? = null

  override fun withId(id: Any?): BuildIssueEventBuilderImpl =
    apply { this.id = id }

  override fun withParentId(parentId: Any?): BuildIssueEventBuilderImpl =
    apply { this.parentId = parentId }

  override fun withTime(time: Long?): BuildIssueEventBuilderImpl =
    apply { this.time = time }

  override fun withHint(hint: @Hint String?): BuildIssueEventBuilderImpl =
    apply { this.hint = hint }

  override fun withFilePosition(filePosition: FilePosition?): BuildIssueEventBuilderImpl =
    apply { this.filePosition = filePosition }

  override fun build(): BuildIssueEventImpl =
    when (val filePosition = filePosition) {
      null -> BuildIssueEventImpl(id, parentId, time, hint, issue, kind)
      else -> FileBuildIssueEventImpl(id, parentId, time, hint, issue, kind, filePosition)
    }
}