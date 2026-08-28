// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.mvcc

import com.intellij.lang.ASTNode
import com.intellij.openapi.application.WriteIntentReadAction
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

  class OpaquePsiVersionImpl(val actualVersion: Long) : PsiVersioningService.OpaquePsiVersion

  override fun doForkTimeline(): PsiVersioningService.OpaquePsiVersion {
    require(getCurrentVersion() % InternalPsiVersioning.MAIN_TIMELINE_DELTA == 0L) {
      "It is not allowed to fork an already forked timeline. Ensure that you don't have `executeWithTimeline` in your stacktrace"
    }
    val forkedVersion = capturePublishedFork()
    return OpaquePsiVersionImpl(forkedVersion)
  }

  fun capturePublishedFork(): Long {
    while (true) {
      val currentVersion = getCurrentVersion() // atomic read #1
      val forkedTimelineVersion = currentVersion + InternalPsiVersioning.FORKED_TIMELINE_DELTA
      InternalPsiVersioning.PsiVersionRegistry.instance.rememberFrozenVersionUnsafe(forkedTimelineVersion)
      InternalPsiVersioning.PsiVersionRegistry.instance.rememberFrozenVersionUnsafe(currentVersion)
      val currentVersion2 = getCurrentVersion() // atomic read #2
      if (currentVersion == currentVersion2) {
        // nothing has changed while we were modifying frozen version maps
        // so everything is now correctly published, we can safely return data
        return forkedTimelineVersion
      } else {
        // current version has advanced while we were publishing data
        // now we need to roll back the side effects and try again
        InternalPsiVersioning.PsiVersionRegistry.instance.forgetFrozenVersionUnsafe(forkedTimelineVersion)
        InternalPsiVersioning.PsiVersionRegistry.instance.forgetFrozenVersionUnsafe(currentVersion)
      }
    }
  }

  override fun doForgetForkedTimeline(opaqueVersion: PsiVersioningService.OpaquePsiVersion) {
    require(opaqueVersion is OpaquePsiVersionImpl) {
      "$opaqueVersion must be created by ${PsiVersioningServiceImpl::class.simpleName}"
    }
    InternalPsiVersioning.PsiVersionRegistry.instance.forgetFrozenVersionUnsafe(opaqueVersion.actualVersion)
    InternalPsiVersioning.PsiVersionRegistry.instance.forgetFrozenVersionUnsafe(opaqueVersion.actualVersion - InternalPsiVersioning.FORKED_TIMELINE_DELTA)
  }

  override fun <T> doExecuteWithTimeline(
    opaqueVersion: PsiVersioningService.OpaquePsiVersion,
    action: () -> T,
  ): T {
    require(opaqueVersion is OpaquePsiVersionImpl) {
      "$opaqueVersion must be created by ${PsiVersioningServiceImpl::class.simpleName}"
    }
    return if (allowUsingFrozenPsi) {
      InternalPsiVersioning.exclusivePsiModificationScopeWithExplicitVersion(opaqueVersion.actualVersion, action)
    } else {
      WriteIntentReadAction.compute(action)
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

  override fun isInsideVersioningButNotLocks(): Boolean = InternalPsiVersioning.isInsideVersioningButNotLocks()
}
