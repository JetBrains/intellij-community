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

sealed interface InlayPosition {
  @get:ApiStatus.Internal
  val priority: Int
}

class InlineInlayPosition(val offset: Int, val relatedToPrevious: Boolean, override val priority: Int = 0) : InlayPosition

class EndOfLinePosition @JvmOverloads constructor(val line: Int, override val priority: Int = 0) : InlayPosition

@ApiStatus.Experimental
class AboveLineIndentedPosition(val offset: Int, val verticalPriority: Int = 0, override val priority: Int = 0) : InlayPosition