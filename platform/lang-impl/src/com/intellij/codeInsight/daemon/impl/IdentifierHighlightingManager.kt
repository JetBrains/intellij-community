// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.openapi.util.Segment
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@JvmRecord
data class IdentifierOccurrence(val range: Segment, val highlightInfoType: HighlightInfoType)

@ApiStatus.Internal
@JvmRecord
data class IdentifierHighlightingResult(
  val occurrences: Collection<IdentifierOccurrence>,
  /**
  targets are the ranges that the caret should react to,
  e.g. if the entire "return xxx;" is highlighted when the caret is on the "return" keyword, the former range is occurrence, and the latter is the target
  This list is used when the caret is moving - if the caret is inside one of the targets, we need to highlight all the occurrences for these targets
   */
  val targets: Collection<Segment>
)

@ApiStatus.Internal
val EMPTY_RESULT: IdentifierHighlightingResult = IdentifierHighlightingResult(listOf(), listOf())