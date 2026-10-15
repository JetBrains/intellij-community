// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.experimental

import com.intellij.openapi.editor.ex.DocumentModState
import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.impl.experimental.DocBranchImpl

/**
 * An immutable document that records its edit history in an Eg-walker [EventGraph]
 * (arXiv 2409.14252). Two divergent copies of one document can then [merge] without losing either
 * side.
 *
 * The branch is a value: [applyOp], [fork], and [merge] return a new branch. [text] gives the
 * document at the branch's version. A merge costs the size of the change and of the concurrent
 * region, not the size of the history.
 *
 * Contract:
 * - Branches that edit concurrently must edit under different [Agent]s. [fork] sets the agent.
 *   Two concurrent edits under one agent can make a merge throw [EventIdClashException], or build
 *   a wrong text.
 * - [merge] expects branches that descend from one [createBranch] value. Branches with no
 *   common history merge into a concatenated document.
 * - `a.merge(b)` and `b.merge(a)` produce the same text. A merge with an ancestor changes
 *   nothing. A merge with a descendant fast-forwards. A repeated merge changes nothing.
 */
interface DocBranch {
  /**
   * The document at this branch's version.
   */
  fun text(): DocumentText

  /**
   * The modification state of [text]. It belongs to this branch, and a merge never takes the one of
   * the other branch. Only a [DocumentOp.ModStamp] changes the stamp.
   */
  fun modState(): DocumentModState

  /**
   * The version of [text]. Under the agent contract, it stays valid in every branch that merges
   * this one, and a replay of it there returns [text].
   */
  fun version(): Version

  /**
   * A branch with [op] applied at this branch's version: the same edit as [DocumentText.applyOp].
   *
   * An op with a move offset is half of a text move. The branch ignores a move offset that does not
   * name the moved text, or that overlaps the text that the op changes.
   *
   * Only a text op records an event.
   */
  fun applyOp(op: DocumentOp): DocBranch

  /**
   * The identity this branch edits under.
   */
  fun agent(): Agent

  /**
   * The event graph that records this branch's history.
   */
  fun graph(): EventGraph

  /**
   * A branch with the same text, mod state and history that edits under [agent].
   */
  fun fork(agent: Agent): DocBranch

  /**
   * A branch that contains the histories of both this branch and [other].
   * Concurrent edits are resolved deterministically; no edit is dropped.
   * The result keeps this branch's [agent].
   *
   * Throws [EventIdClashException] when the two branches broke the agent contract. It does not find
   * every break. A merge that throws changes nothing.
   *
   * The result is the branch of [mergeWithOps] for the same two branches.
   */
  fun merge(other: DocBranch): DocBranch

  /**
   * The same merge as [merge], plus the ops that turn the [text] of this branch into the merged
   * text. An editor needs them to apply a merge to its document. See [DocMerge] for their order
   * and their cost.
   */
  fun mergeWithOps(other: DocBranch): DocMerge

  /**
   * A branch with the same text, mod state, agent and history. It is never `===` to this branch.
   * Edit only one of the two, because they share the agent.
   */
  fun newInstance(): DocBranch

  /**
   * A list of the line ranges of [text], for diagnostics only.
   */
  fun dumpState(): String

  companion object {
    /**
     * A new branch with the text [chars] that edits under [agent].
     */
    @JvmStatic
    fun createBranch(chars: CharSequence, agent: Agent): DocBranch {
      return DocBranchImpl.create(chars, agent)
    }
  }
}
