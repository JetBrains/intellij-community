// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.eventBuilders

import com.intellij.build.events.BuildEventsNls.*
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.issue.BuildIssue
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.NonExtendable
import org.jetbrains.annotations.CheckReturnValue

@Experimental
@NonExtendable
interface BuildIssueEventBuilder {

  @CheckReturnValue
  fun withId(id: Any?): BuildIssueEventBuilder

  @CheckReturnValue
  fun withParentId(parentId: Any?): BuildIssueEventBuilder

  @CheckReturnValue
  fun withTime(time: Long?): BuildIssueEventBuilder

  @CheckReturnValue
  fun withHint(hint: @Hint String?): BuildIssueEventBuilder

  @CheckReturnValue // mandatory field
  fun withKind(kind: MessageEvent.Kind): BuildIssueEventBuilder

  @CheckReturnValue // mandatory field
  fun withIssue(issue: BuildIssue): BuildIssueEventBuilder

  fun build(): BuildIssueEvent
}