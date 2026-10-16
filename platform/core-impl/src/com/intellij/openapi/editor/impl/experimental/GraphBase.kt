// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.experimental.LineageMismatchException
import java.util.concurrent.ThreadLocalRandom

/**
 * The base of an [EventGraphImpl]: the base text, which comes before the first event, and the
 * lineage of the graph. The base keeps the characters of the base text and no line data.
 *
 * The lineage names the call that made the first graph. Every graph that grows from it by an
 * append or a merge keeps the lineage. Two graphs merge only within one lineage, because only
 * there one event id names one event.
 *
 * The value is immutable, so any thread may read it.
 */
internal class GraphBase private constructor(
  private val text: DocumentText,
  private val lineage: Long,
) {

  fun text(): DocumentText {
    return text
  }

  /**
   * Throws [LineageMismatchException] when [other] belongs to another lineage.
   */
  fun assertSameLineage(other: GraphBase) {
    if (lineage != other.lineage) {
      val message = "The bases $this and $other belong to different lineages. " +
                    "Only graphs that grow from one createGraph or createBranch call merge."
      throw LineageMismatchException(message)
    }
  }

  /**
   * The length and not the text, because a base can hold a whole document.
   */
  override fun toString(): String {
    return "GraphBase(length=${text.length()}, lineage=$lineage)"
  }

  companion object {
    /**
     * A base that starts a new lineage at the characters of [text]. The base shares the characters
     * and drops the line data that [text] may cache, so no graph keeps that line data alive.
     */
    fun create(text: DocumentText): GraphBase {
      val withoutLineData = DocumentText.createText(text.chars())
      val lineage = ThreadLocalRandom.current().nextLong()
      return GraphBase(withoutLineData, lineage)
    }
  }
}
