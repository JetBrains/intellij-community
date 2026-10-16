// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.experimental.LineageMismatchException
import java.util.concurrent.ThreadLocalRandom

/**
 * The base of an [EventGraphImpl]: the base text, the base version, and the lineage of the graph.
 * The base text is the document at the base version, and it comes before the first event. The base
 * keeps the characters of the base text and no line data.
 *
 * A new base sits at [VersionImpl.ROOT]. A trim moves the base to a later version of the lineage.
 *
 * The lineage names the call that made the first graph. Every graph that grows from it by an
 * append, a merge or a trim keeps the lineage. Two graphs merge only within one lineage, because
 * only there one event id names one event.
 *
 * The value is immutable, so any thread may read it.
 */
internal class GraphBase private constructor(
  private val text: DocumentText,
  private val lineage: Long,
  private val version: VersionImpl,
) {

  fun text(): DocumentText {
    return text
  }

  /**
   * The base version, by event id. The graph holds none of its heads.
   */
  fun version(): VersionImpl {
    return version
  }

  /**
   * A base of this lineage at the later version [version], whose document is [text].
   */
  fun movedTo(text: DocumentText, version: VersionImpl): GraphBase {
    return GraphBase(withoutLineData(text), lineage, version)
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
    return "GraphBase(length=${text.length()}, lineage=$lineage, version=$version)"
  }

  companion object {
    /**
     * A base that starts a new lineage at the characters of [text].
     */
    fun create(text: DocumentText): GraphBase {
      val lineage = ThreadLocalRandom.current().nextLong()
      return GraphBase(withoutLineData(text), lineage, VersionImpl.ROOT)
    }

    /**
     * The characters of [text] in a value of their own. The base shares the characters and drops the
     * line data that [text] may cache, so no graph keeps that line data alive.
     */
    private fun withoutLineData(text: DocumentText): DocumentText {
      return DocumentText.createText(text.chars())
    }
  }
}
