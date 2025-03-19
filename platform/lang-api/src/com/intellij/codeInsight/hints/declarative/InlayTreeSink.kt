// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative

import org.jetbrains.annotations.ApiStatus

/**
 * Collects inlays during construction.
 */
interface InlayTreeSink {
  @Deprecated("", ReplaceWith("Use addPresentation(InlayPosition, List<InlayPosition>?, String?, HintColorKind, () -> Unit) method"))
  fun addPresentation(position: InlayPosition,
                      payloads: List<InlayPayload>? = null,
                      tooltip: String? = null,
                      hasBackground: Boolean,
                      builder: PresentationTreeBuilder.() -> Unit) {
    addPresentation(position,
                    payloads,
                    tooltip,
                    if (hasBackground) HintFormat.default else HintFormat.default.withColorKind(HintColorKind.TextWithoutBackground),
                    builder)
  }

  /**
   * Saves presentation for later application.
   * @param builder builder for a given inlay entry. It will be called in place, and it can be called zero times or once.
   */
  fun addPresentation(position: InlayPosition,
                      payloads: List<InlayPayload>? = null,
                      tooltip: String? = null,
                      hintFormat: HintFormat,
                      builder: PresentationTreeBuilder.() -> Unit)

  /**
   * Explicit branch, which will be executed only if given [optionId] is enabled.
   *
   * @param block block of code to conditionally execute. It will be called in place.
   */
  fun whenOptionEnabled(optionId: String, block: () -> Unit)
}

/**
 * The payload for the whole inlay. It may be used later by the actions from the right-click menu.
 */
class InlayPayload(val payloadName: String, val payload: InlayActionPayload)

sealed interface InlayPosition

class InlineInlayPosition(val offset: Int, val relatedToPrevious: Boolean, val priority: Int = 0) : InlayPosition

class EndOfLinePosition @JvmOverloads constructor(val line: Int, val priority: Int = 0) : InlayPosition

/**
 * Positions an inlay hint above the line that contains [offset].
 *
 * @param verticalPriority Hints with higher [verticalPriority] will be placed closer to the line given by [offset].
 * Hints from the same provider with the same [verticalPriority] will be placed on the same line.
 * @param priority Within a single line, hints are sorted by [priority] in descending order.
 */
@ApiStatus.Experimental
class AboveLineIndentedPosition(val offset: Int, val verticalPriority: Int = 0, val priority: Int = 0) : InlayPosition