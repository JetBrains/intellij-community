// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.smartPointers

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Segment
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Tracks a [PsiDirectory].
 */
internal class DirElementInfo(directory: PsiDirectory) : SmartPointerElementInfo {
  override val virtualFile: VirtualFile = directory.getVirtualFile()

  private val myProject: Project = directory.getProject()

  override fun restoreElement(manager: SmartPointerManagerEx): PsiElement? =
    SelfElementInfo.restoreDirectoryFromVirtual(this.virtualFile, myProject)

  override fun restoreFile(manager: SmartPointerManagerEx): PsiFile? = null

  override fun elementHashCode(): Int = virtualFile.hashCode()

  override fun pointsToTheSameElementAs(other: SmartPointerElementInfo, manager: SmartPointerManagerEx): Boolean =
    other is DirElementInfo && Comparing.equal(this.virtualFile, other.virtualFile)

  override fun getRange(manager: SmartPointerManagerEx): Segment? = null

  override fun getPsiRange(manager: SmartPointerManagerEx): Segment? = null

  override fun toString(): String = "dir{$virtualFile}"
}
