// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative

/**
 * Collects inlays during construction.
 */
interface InlayTreeSink {
  /**
   * Saves presentation for later application.
   * @param builder builder for a given inlay entry. It can be executed zero times or once.
   */
  fun addPresentation(position: InlayPosition,
                      tooltip: String? = null,
                      hasBackground: Boolean,
                      builder: PresentationTreeBuilder.() -> Unit)

  /**
   * Explicit branch, which will be executed only if given [optionId] is enabled.
   */
  fun whenOptionEnabled(optionId: String, block: () -> Unit)
}

sealed interface InlayPosition

class InlineInlayPosition(val offset: Int, val relatedToPrevious: Boolean, val priority: Int = 0) : InlayPosition

class EndOfLinePosition(val line: Int) : InlayPosition