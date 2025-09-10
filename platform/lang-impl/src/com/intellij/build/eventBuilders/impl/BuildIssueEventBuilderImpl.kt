// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.eventBuilders.impl

import com.intellij.build.eventBuilders.BuildIssueEventBuilder
import com.intellij.build.events.BuildEventsNls.Hint
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.issue.BuildIssue
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class BuildIssueEventBuilderImpl : BuildIssueEventBuilder {

  private var id: Any? = null
  private var parentId: Any? = null
  private var time: Long? = null
  private var hint: @Hint String? = null

  private var kind: MessageEvent.Kind? = null

  private var issue: BuildIssue? = null

  override fun withId(id: Any?): BuildIssueEventBuilderImpl =
    apply { this.id = id }

  override fun withParentId(parentId: Any?): BuildIssueEventBuilderImpl =
    apply { this.parentId = parentId }

  override fun withTime(time: Long?): BuildIssueEventBuilderImpl =
    apply { this.time = time }

  override fun withHint(hint: @Hint String?): BuildIssueEventBuilderImpl =
    apply { this.hint = hint }

  override fun withKind(kind: MessageEvent.Kind): BuildIssueEventBuilderImpl =
    apply { this.kind = kind }

  override fun withIssue(issue: BuildIssue): BuildIssueEventBuilderImpl =
    apply { this.issue = issue }

  override fun build(): BuildIssueEventImpl {
    return BuildIssueEventImpl(
      id,
      parentId,
      time,
      hint,
      kind ?: throw IllegalStateException("The BuildIssueEvent's 'kind' property should be defined"),
      issue ?: throw IllegalStateException("The BuildIssueEvent's 'issue' property should be defined")
    )
  }
}