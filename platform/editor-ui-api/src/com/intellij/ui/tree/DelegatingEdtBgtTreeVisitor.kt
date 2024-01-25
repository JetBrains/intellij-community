// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree

import com.intellij.util.ui.EDT
import org.jetbrains.annotations.ApiStatus
import javax.swing.tree.TreePath

@ApiStatus.Internal
abstract class DelegatingEdtBgtTreeVisitor(private val delegate: TreeVisitor) : EdtBgtTreeVisitor() {

  override fun doVisit(path: TreePath): TreeVisitor.Action = delegate.visit(path)

  override fun visitThread(): TreeVisitor.VisitThread = delegate.visitThread()

}
