// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.experimental.DocumentBranch
import com.intellij.openapi.editor.ex.experimental.DocMerge

/**
 * See [DocMerge]. The ops are ready when the merge replayed and recorded them. They are deferred
 * when the merge needed no replay, because the ops would cost one.
 *
 * Thread safety: the branch and a ready list are final. A deferred list sits behind a lazy value in
 * the publication mode. Two threads that ask at once can both build it, but every caller gets the
 * one list that won. No lock is held while the replay runs. The build reads only immutable values,
 * so a second build gives an equal list. After the build the lazy value drops the computation, and
 * with it the values that only the computation needed.
 */
internal class DocMergeImpl(
    private val branch: DocumentBranch,
    private val ops: Lazy<List<DocumentOp.Text>>,
) : DocMerge {

  override fun branch(): DocumentBranch {
    return branch
  }

  override fun ops(): List<DocumentOp.Text> {
    return ops.value
  }

  /**
   * The branch and the number of ops. It never builds deferred ops.
   */
  override fun toString(): String {
    val opsText = if (ops.isInitialized()) "${ops.value.size} ops" else "ops deferred"
    return "DocMerge($branch, $opsText)"
  }
}
