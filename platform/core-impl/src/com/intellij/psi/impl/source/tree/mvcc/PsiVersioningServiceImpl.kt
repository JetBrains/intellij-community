// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.mvcc

import com.intellij.lang.ASTNode
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.psi.util.PsiVersioningService
import com.intellij.util.asSafely

internal class PsiVersioningServiceImpl : PsiVersioningService {
  override fun <T> runInVersionedEnvironment(node: ASTNode, action: () -> T): T {
    val versioned = node.asSafely<TreeElement>()?.isVersioned ?: return action()
    return InternalPsiVersioning.inVersionedEnvironment(versioned, action::invoke)
  }

  override fun <T> runAndFreezePsiVersion(action: () -> T): T {
    return InternalPsiVersioning.freezePsiVersion(action)
  }
}
