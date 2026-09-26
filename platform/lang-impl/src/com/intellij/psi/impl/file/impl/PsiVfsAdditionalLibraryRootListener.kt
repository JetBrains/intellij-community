// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.ExternalChangeActionUtil
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.PsiTreeChangeEventImpl
import org.jetbrains.annotations.Nls

internal class PsiVfsAdditionalLibraryRootListener(project: Project) : AdditionalLibraryRootsListener {
  private val psiManager = PsiManagerEx.getInstanceEx(project)
  private val fileManager = psiManager.fileManager as FileManagerEx

  override fun libraryRootsChanged(
    presentableLibraryName: @Nls String?,
    oldRoots: Collection<VirtualFile>,
    newRoots: Collection<VirtualFile>,
    libraryNameForDebug: String,
  ) {
    ApplicationManager.getApplication().runWriteAction(
      ExternalChangeActionUtil.externalChangeAction {
        var treeEvent = PsiTreeChangeEventImpl(psiManager)
        treeEvent.propertyName = PsiTreeChangeEvent.PROP_ROOTS
        psiManager.beforePropertyChange(treeEvent)
        DebugUtil.performPsiModification<RuntimeException>(null) {
          fileManager.possiblyInvalidatePhysicalPsi()
        }

        treeEvent = PsiTreeChangeEventImpl(psiManager)
        treeEvent.propertyName = PsiTreeChangeEvent.PROP_ROOTS
        psiManager.propertyChanged(treeEvent)
      }
    )
  }
}