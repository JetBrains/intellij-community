// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

/**
 * The union graph of [EventGraphImpl.mergeFromImpl], plus the two questions a caller asks about it.
 * [sourceSize] is the size of the graph the merge started from, so the result can answer them itself.
 */
internal class MergeResult(
  private val graph: EventGraphImpl,
  private val sourceSize: Int,
  private val isFastForward: Boolean,
) {
  /**
   * Whether the other graph brought nothing: its whole history was already here.
   */
  fun addsNothing(): Boolean {
    return graph.size() == sourceSize
  }

  /**
   * Whether the source history sits inside the other one. The other branch's text is
   * then already the merged text, so no replay is needed. A merge that adds nothing is none.
   */
  fun isFastForward(): Boolean {
    return isFastForward
  }

  fun graph(): EventGraphImpl {
    return graph
  }

  /**
   * States the two answers and the size of the union, and NOT the graph. A graph prints a
   * whole box diagram, which no message wants inside another one.
   */
  override fun toString(): String {
    return "MergeResult(units=${graph.size()}, runs=${graph.runCount()}, " +
           "addsNothing=${addsNothing()}, fastForward=${isFastForward()})"
  }
}
