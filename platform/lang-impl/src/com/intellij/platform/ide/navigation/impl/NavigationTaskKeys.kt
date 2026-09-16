// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.navigation.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.ide.projectView.impl.nodes.BasePsiNode
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.impl.SourceNavigationRequest
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLock

/**
 * Identifies navigation requests during preparation so a newer submission can cancel an older preparation of the same target.
 *
 * @param offset the offset the request was created with, see [SourceNavigationRequest.initialOffset]
 * @param fileStamp the file modification stamp when the request was created
 * @param documentStamp the document modification stamp when the request was created, or `null` if no document was loaded
 */
internal data class NavigationPreparationKey(
  val clientId: ClientId,
  val file: VirtualFile,
  val offset: Int?,
  val fileStamp: Long,
  val documentStamp: Long?,
  val elementRange: TextRange?,
  val options: NavigationOptions,
)

/**
 * NB: [target] compares PSI, so equality requires read access
 */
@Suppress("EqualsOrHashCode")
internal data class NavigatablePreparationKey(
  val clientId: ClientId,
  val type: Class<*>,
  val target: SmartPsiElementPointer<*>,
  val options: NavigationOptions,
) {
  override fun equals(other: Any?): Boolean {
    ThreadingAssertions.assertReadAccess()
    if (this === other) return true
    if (other !is NavigatablePreparationKey) return false
    return clientId == other.clientId && type == other.type && target == other.target && options == other.options
  }
}

@RequiresReadLock
internal fun SourceNavigationRequest.preparationKey(options: NavigationOptions): NavigationPreparationKey? {
  ThreadingAssertions.assertReadAccess()
  if (!file.isValid || offsetMarker?.isValid == false || elementRangeMarker?.isValid == false) {
    return null
  }
  return NavigationPreparationKey(
    clientId = ClientId.current,
    file = file,
    offset = initialOffset,
    fileStamp = initialFileStamp,
    documentStamp = initialDocumentStamp,
    elementRange = elementRangeMarker?.textRange,
    options = options,
  )
}

@RequiresReadLock
internal fun Navigatable.preparationKey(options: NavigationOptions): NavigatablePreparationKey? {
  ThreadingAssertions.assertReadAccess()
  val element = when (this) {
    is PsiElement -> this
    is BasePsiNode<*> -> value
    else -> null
  }?.takeIf { it.isValid } ?: return null

  return NavigatablePreparationKey(
    clientId = ClientId.current,
    type = javaClass,
    target = SmartPointerManager.createPointer(element),
    options = options,
  )
}

/**
 * Registers the preparation key of a resolved single-target request.
 * A batch, a non-source request, and a request without a stable target identity get no key and are never deduplicated.
 *
 * @return `false` when a newer navigation already prepares the same target and this one must stop
 */
internal suspend fun TwoPhaseOverflowExecutor.Preparation.registerTargetKey(
  requests: Collection<NavigationRequest>,
  options: NavigationOptions,
): Boolean {
  val request = requests.singleOrNull() as? SourceNavigationRequest ?: return true
  return readAction {
    val key = request.preparationKey(options) ?: return@readAction true
    addKey(key)
  }
}

internal suspend fun TwoPhaseOverflowExecutor.Preparation.registerTargetKey(
  navigatable: Navigatable?,
  options: NavigationOptions,
): Boolean {
  navigatable ?: return true
  return readAction {
    val key = navigatable.preparationKey(options) ?: return@readAction true
    addKey(key)
  }
}
