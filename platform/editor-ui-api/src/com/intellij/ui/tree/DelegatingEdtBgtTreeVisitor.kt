// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree

import com.intellij.util.ui.EDT
import org.jetbrains.annotations.ApiStatus
import javax.swing.tree.TreePath

@ApiStatus.Internal
abstract class DelegatingEdtBgtTreeVisitor(private val delegate: TreeVisitor) : EdtBgtTreeVisitor {

  override fun visit(path: TreePath): TreeVisitor.Action {
    if (visitThread() == TreeVisitor.VisitThread.EDT) { // everything is done on the EDT
      EDT.assertIsEdt()
      val preVisitResult = preVisitEDT(path)
      if (preVisitResult != null) {
        return preVisitResult
      }
      return postVisitEDT(path, delegate.visit(path))
    }
    else { // only the delegating part is done on the EDT, the rest is invoked directly
      return delegate.visit(path)
    }
  }

  override fun visitThread(): TreeVisitor.VisitThread = delegate.visitThread()

}
