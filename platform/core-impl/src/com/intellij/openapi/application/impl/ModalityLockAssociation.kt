// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.CleanupAction
import com.intellij.openapi.application.ModalityState
import com.intellij.util.containers.CollectionFactory
import org.jetbrains.annotations.ApiStatus
import java.util.Collections
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Handles correspondence between [ModalityState] and [com.intellij.platform.locking.impl.NestedLocksThreadingSupport.ComputationState]
 */
@ApiStatus.Internal
object ModalityLockAssociation {

  private val modalityToLockContext: MutableMap<ModalityState, CoroutineContext> =
    Collections.synchronizedMap(CollectionFactory.createWeakIdentityMap(4, 0.75f))

  @JvmStatic
  fun associate(modality: ModalityState, lockContext: CoroutineContext): AccessToken {
    if (lockContext == EmptyCoroutineContext) {
      return AccessToken.EMPTY_ACCESS_TOKEN
    }
    modalityToLockContext[modality] = lockContext
    return object : AccessToken() {
      override fun finish() {
        modalityToLockContext.remove(modality, lockContext)
      }
    }
  }

  @JvmStatic
  fun isCurrentLockConsistentWithModality(modality: ModalityState): Boolean {
    val associatedLockContext = modalityToLockContext[modality] ?: return true
    val threadingSupport = ApplicationManager.getApplication()?.threadingSupport ?: return true
    val lockElement = threadingSupport.getLockContextElement()
    return associatedLockContext.fold(false) { acc, element -> acc || element == lockElement }
  }
}
