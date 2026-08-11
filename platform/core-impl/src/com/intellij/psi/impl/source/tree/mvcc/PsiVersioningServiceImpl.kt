// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.mvcc

import com.intellij.lang.ASTNode
import com.intellij.openapi.application.allowUsingFrozenPsi
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.CompositePsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.psi.util.PsiVersioningService
import com.intellij.util.asSafely

internal class PsiVersioningServiceImpl : PsiVersioningService {
  override fun <T> runInVersionedEnvironment(node: ASTNode, action: () -> T): T {
    val versioned = node.asSafely<TreeElement>()?.isVersioned ?: return action()
    return InternalPsiVersioning.inVersionedEnvironment(versioned, action::invoke)
  }

  override fun <T> runAndFreezePsiVersion(action: () -> T): T {
    return if (allowUsingFrozenPsi) {
      InternalPsiVersioning.freezePsiVersion(action)
    } else {
      runReadActionBlocking {
        action()
      }
    }
  }

  override fun getCurrentVersion(): Long {
    return InternalPsiVersioning.getCurrentPsiVersion()
  }

  override fun getNextSibling(element: PsiElement, version: Long): PsiElement? {
    return when (element) {
      is CompositePsiElement -> element.getTreeNextVersioned(version)?.psi
      is LeafPsiElement -> element.getTreeNextVersioned(version)?.psi
      else -> element.nextSibling
    }
  }

  override fun getPrevSibling(element: PsiElement, version: Long): PsiElement? {
    // todo: optimize in the future
    return element.prevSibling
  }

  override fun getParent(element: PsiElement, version: Long): PsiElement? {
    return when (element) {
      // do not optimize CompositePsiElement! there are some custom overrides for getParent of this class, it is not under full control of the Platform.
      //is CompositePsiElement -> element.getTreeParentVersioned(version)?.psi
      is LeafPsiElement -> element.getTreeParentVersioned(version)?.psi
      else -> element.parent
    }
  }

  override fun getFirstChild(element: PsiElement, version: Long): PsiElement? {
    return when (element) {
      is CompositePsiElement -> element.getFirstChildNodeVersioned(version)?.psi
      else -> element.firstChild
    }
  }
}
