// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events.impl

import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEventsNls
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.FileMessageEvent
import com.intellij.build.events.FileMessageEventResult
import com.intellij.build.events.MessageEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.lang.LangBundle
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class FileBuildIssueEventImpl(
  parentId: Any,
  private val issue: BuildIssue,
  private val kind: MessageEvent.Kind,
  private val filePosition: FilePosition,
) : AbstractBuildEvent(null, parentId, null, issue.title, null, issue.description),
    FileMessageEvent, BuildIssueEvent {

  override fun getIssue(): BuildIssue = issue

  override fun getNavigatable(project: Project): Navigatable? = issue.getNavigatable(project)

  override fun getKind(): MessageEvent.Kind = kind

  override fun getGroup(): @BuildEventsNls.Title String = LangBundle.message("build.event.title.build.issues")

  override fun getFilePosition(): FilePosition = filePosition

  override fun getResult(): FileMessageEventResult = object : FileMessageEventResult {
    override fun getFilePosition() = this@FileBuildIssueEventImpl.filePosition
    override fun getKind() = this@FileBuildIssueEventImpl.kind
    override fun getDetails() = this@FileBuildIssueEventImpl.description
  }
}
