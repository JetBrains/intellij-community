// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl.util

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.codeInsight.hints.declarative.impl.ActionWithContent
import com.intellij.openapi.editor.Inlay
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object DeclarativeInlayHintsUtil {
  fun InlayPosition.getPlacement(): Inlay.Placement =
    when (this) {
      is AboveLineIndentedPosition -> Inlay.Placement.ABOVE_LINE
      is EndOfLinePosition -> Inlay.Placement.AFTER_LINE_END
      is InlineInlayPosition -> Inlay.Placement.INLINE
    }

  fun InlayPosition.getRelatesToPrecedingText(): Boolean =
    when (this) {
      is AboveLineIndentedPosition -> false
      is EndOfLinePosition -> true
      is InlineInlayPosition -> relatedToPrevious
    }

  /** @see com.intellij.openapi.editor.InlayProperties.getPriority */
  fun InlayPosition.getInlayPriority(): Int =
    when (this) {
      is AboveLineIndentedPosition -> verticalPriority
      is EndOfLinePosition -> priority
      is InlineInlayPosition -> priority
    }

  /** Transform [InlayActionData] stored inside a declarative inlay tree node at given index */
  inline fun TinyTree<Any?>.mapInlayActionData(index: Byte, transform: (InlayActionData) -> InlayActionData) {
    val dataPayload = getDataPayload(index)
    if (dataPayload is ActionWithContent) {
      val oldActionData = dataPayload.actionData
      val newActionData = transform(oldActionData)
      if (oldActionData !== newActionData) {
        setDataPayload(
          ActionWithContent(
            newActionData,
            dataPayload.content),
          index
        )
      }
    }
    else if (dataPayload is InlayActionData) {
      val oldActionData = dataPayload
      val newActionData = transform(oldActionData)
      if (oldActionData !== newActionData) {
        setDataPayload(
          newActionData,
          index
        )
      }
    }
    // else: not a node with InlayActionData, do nothing
  }
}