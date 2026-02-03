// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree

import com.intellij.ui.tree.TreeVisitor.Action
import com.intellij.util.ui.EDT
import org.jetbrains.annotations.ApiStatus
import javax.swing.tree.TreePath

/**
 * A complicated visitor for mixing EDT/BGT logic.
 *
 * It's intended to be used this way: call [preVisitEDT] on the EDT first,
 * and if it returns a non-null value, consider it to be the end result
 * of visiting the node. Otherwise, call [visit] on either the EDT or a BGT
 * (depending on what [visitThread] returns) and then pass its
 * result to [postVisitEDT] on the EDT and consider its return value to be
 * the end result. Subclasses must implement the [doVisit] method
 * which will be called by the `visit` method.
 *
 * An important implementation note: in order for the visitor
 * to be compatible with callers that aren't even aware of `visitThread`,
 * the implementation of the `visit()` method does the pre-visit/visit/post-visit
 * logic itself if the calling thread is the EDT, which could happen in two cases:
 *
 * 1. The `visitThread` method returns `EDT`.
 *
 * 2. The caller isn't aware of `visitThread`, which is the case with pure EDT models,
 * for example very simple or very fast models, or some models used in unit tests.
 *
 * In the case when the `visitThread` method returns `BGT` and the caller is BGT-aware,
 * it must comply to the contract and call `preVisitEDT`, `visit` and `postVisitEDT` methods
 * according to the logic described above. The `visit` method, when called on a BGT, will
 * simply delegate to `doVisit` and will _not_ call `preVisitEDT` and `postVisitEDT`.
 */
@ApiStatus.Internal
abstract class EdtBgtTreeVisitor : TreeVisitor {

  abstract fun preVisitEDT(path: TreePath): Action?

  final override fun visit(path: TreePath): Action {
    // Can't check visitThread() here because the tree model isn't strictly required to obey it.
    // That is, an async model will invoke this method on a BGT if visitThread() returns BGT,
    // but a purely EDT model will simply call it on the EDT regardless, which is often the case
    // for simple models or unit tests. Therefore, to avoid skipping preVisitEDT/postVisitEDT completely,
    // we check the actual thread instead. If it's the EDT, then we must invoke everything directly here,
    // regardless of whether it's because visitThread() == EDT or because the model is simply not even
    // aware of visitThread().
    if (EDT.isCurrentThreadEdt()) {
      val preVisitResult = preVisitEDT(path)
      if (preVisitResult != null) {
        return preVisitResult
      }
      return postVisitEDT(path, doVisit(path))
    }
    else { // only the delegating part is done on the EDT, the rest is invoked directly
      return doVisit(path)
    }
  }

  abstract fun doVisit(path: TreePath): Action

  abstract fun postVisitEDT(path: TreePath, visitResult: Action): Action

}
