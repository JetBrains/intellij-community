// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.impl.VersionedStorageChangeInternal
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.PsiTreeChangeEventImpl

/**
 * We use [com.intellij.platform.backend.workspace.WorkspaceModelChangeListener] in **addition** to [com.intellij.openapi.roots.ModuleRootListener], because [com.intellij.openapi.roots.ModuleRootListener] may generate events
 * not sourced by the workspace model (see Javadoc for [com.intellij.openapi.roots.ModuleRootListener]).
 * If the same event should trigger both [com.intellij.platform.backend.workspace.WorkspaceModelChangeListener] event and [PsiVFSModuleRootListener], these listener invocations
 * will be nested and deduplicated inside [PsiVFSModuleRootListenerImpl], so eventually only one [com.intellij.psi.PsiTreeChangeEvent] will be published.
 *
 * With this listener, we mostly want to invalidate psi caches when workspace model changes.
 */
// @Suppress: Don't use flow instead of [WorkspaceModelChangeListener]. We need to invalidate caches in the same WA as the event.
@Suppress("UsagesOfObsoleteApi")
internal class PsiWsmListener(listenerProject: Project) : WorkspaceModelChangeListener {
  private val service = listenerProject.service<PsiVFSModuleRootListenerImpl>()

  init {
    if (!Registry.`is`("psi.vfs.listener.over.wsm", true)) {
      LOG.debug("PsiWsmListener is disabled by registry key")
      throw ExtensionNotApplicableException.create()
    }
  }

  private fun isNotEmptyChange(event: VersionedStorageChange): Boolean {
    return (event as? VersionedStorageChangeInternal)?.getAllChanges()?.firstOrNull() != null
  }

  override fun beforeChanged(event: VersionedStorageChange) {
    if (isNotEmptyChange(event)) {
      service.beforeRootsChange(false)
    }
  }

  override fun changed(event: VersionedStorageChange) {
    if (isNotEmptyChange(event)) {
      service.rootsChanged(false)
    }
  }
}

@Service(Service.Level.PROJECT)
internal class PsiVFSModuleRootListenerImpl(private val listenerProject: Project) {
  // accessed from within write action only
  private var depthCounter = 0

  fun beforeRootsChange(isCausedByFileTypesChange: Boolean) {
    LOG.trace { "beforeRootsChanged call" }
    if (isCausedByFileTypesChange) {
      return
    }

    LOG.trace { "Event is not caused by file types change" }
    runWriteActionWithExternalChange {
      depthCounter++
      LOG.trace { "depthCounter increased $depthCounter" }
      if (depthCounter > 1) {
        return@runWriteActionWithExternalChange
      }

      val psiManager = PsiManagerEx.getInstanceEx(listenerProject)
      val treeEvent = PsiTreeChangeEventImpl(psiManager)
      treeEvent.propertyName = PsiTreeChangeEvent.PROP_ROOTS
      psiManager.beforePropertyChange(treeEvent)
    }
  }

  fun rootsChanged(isCausedByFileTypesChange: Boolean) {
    LOG.trace { "rootsChanged call" }
    val psiManager = PsiManagerEx.getInstanceEx(listenerProject)
    val fileManager = psiManager.fileManager as FileManagerEx
    fileManager.dispatchPendingEvents()

    if (isCausedByFileTypesChange) {
      return
    }

    LOG.trace { "Event is not caused by file types change" }
    runWriteActionWithExternalChange {
      depthCounter--
      LOG.trace { "depthCounter decreased $depthCounter" }
      assert(depthCounter >= 0) { "unbalanced `beforeRootsChange`/`rootsChanged`: $depthCounter" }
      if (depthCounter > 0) {
        return@runWriteActionWithExternalChange
      }

      DebugUtil.performPsiModification<RuntimeException>(null) {
        fileManager.possiblyInvalidatePhysicalPsi()
      }

      val treeEvent = PsiTreeChangeEventImpl(psiManager)
      treeEvent.propertyName = PsiTreeChangeEvent.PROP_ROOTS
      psiManager.propertyChanged(treeEvent)
    }
  }
}

private val LOG = logger<PsiVFSModuleRootListener>()
