// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.experimental

import com.intellij.openapi.editor.impl.experimental.LV
import com.intellij.openapi.editor.impl.experimental.LvNamedVersion
import com.intellij.openapi.editor.impl.experimental.VersionImpl
import org.jetbrains.annotations.TestOnly

/**
 * A version of an [EventGraph]: the paper's `Version(G)`, the set of events with no children.
 * The paper also calls this set the frontier.
 *
 * A version identifies a document state: `Events(V)` is the set of all events at or before it,
 * and a replay of that set is the document at this version.
 *
 * A version names its heads by event id. Under the agent contract of [DocumentBranch], one event id
 * names one event in every graph of one lineage. [LineageMismatchException] says what a lineage is.
 * So a version means the same in every graph of its lineage that holds its heads. For example, it
 * means the same in a graph that merged the one that made it. A graph checks only that it holds the
 * event ids, and not that they name the same events or come from its lineage.
 *
 * Two versions are [equals] when they hold the same heads. Under the agent contract, two versions of
 * one lineage then name the same events. A version from [of] is the exception.
 *
 * The value is immutable, and any thread may read it.
 */
interface Version {
  /**
   * `true` for the version before any event.
   */
  fun isRoot(): Boolean

  companion object {
    /**
     * The version before any event: the base version of a new graph. A graph that a trim moved past
     * it throws [TrimmedHistoryException] for it.
     */
    fun root(): Version {
      return VersionImpl.ROOT
    }

    /**
     * A version that names its heads by lv, for a test that builds the graph itself. An lv is local
     * to one graph value, so the graph that takes this version reads the lvs as its own. This
     * version never equals a version that a graph returns.
     */
    @TestOnly
    fun of(lv: LV, vararg lvs: LV): Version {
      return LvNamedVersion(intArrayOf(lv, *lvs))
    }
  }
}
