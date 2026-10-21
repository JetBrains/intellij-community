// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.impl.experimental.DocMergeImpl

/**
 * The result of [DocumentBranch.mergeWithOps]: the merged branch, plus the ops that turn the text of the
 * receiver into the text of that branch.
 *
 * An editor applies these ops to its document, so the carets, the markers and every other listener
 * follow a merge like any other edit.
 *
 * The value is immutable, and any thread may read it.
 */
interface DocMerge {
  /**
   * The merged branch: the value that [DocumentBranch.merge] returns for the same two branches.
   */
  fun branch(): DocumentBranch

  /**
   * The ops from the text of the receiver to the text of [branch], in apply order.
   *
   * Apply them one after another. The offset of each op indexes the text after all the ops before
   * it. So the text of the receiver, with every op applied in turn, equals the text of [branch]. A
   * text between two ops need not be the text of any version.
   *
   * Each op is an insert or a delete, and no op is empty. A merge that changes no text has no ops,
   * but two ops can still cancel each other. The list cannot change, and every call returns the
   * same list.
   *
   * A move arrives as an insert and a delete with move offsets, as `DocumentEvent.getMoveOffset`
   * gives them, when the moved text arrives unchanged. Otherwise it arrives as plain ops.
   *
   * For a fast-forward, the first call replays the change. It throws when the ops do not build the
   * text of [branch], which happens only when the two branches broke the agent contract.
   */
  fun ops(): List<DocumentOp.Text>

  companion object {
    /**
     * A merge with [ops] ready. The list must not change after this call.
     */
    @JvmStatic
    fun ready(branch: DocumentBranch, ops: List<DocumentOp.Text>): DocMerge {
      return DocMergeImpl(branch, lazyOf(ops))
    }

    /**
     * A merge whose ops [lazyOps] makes on the first call of [ops]. [lazyOps] must return a list
     * that cannot change. It can run more than once when threads race, so it must give equal lists.
     */
    @JvmStatic
    fun deferred(branch: DocumentBranch, lazyOps: () -> List<DocumentOp.Text>): DocMerge {
      return DocMergeImpl(branch, lazy(LazyThreadSafetyMode.PUBLICATION, lazyOps))
    }
  }
}
