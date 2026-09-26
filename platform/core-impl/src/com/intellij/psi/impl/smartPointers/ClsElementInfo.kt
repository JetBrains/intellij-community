// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.smartPointers

import com.intellij.openapi.util.Segment
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiAnchor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Tracks a compiled (`.class`) PSI element via a [PsiAnchor.StubIndexReference].
 * Has no text range since bytecode elements have no source offsets.
 */
internal class ClsElementInfo(
  private val myStubIndexReference: PsiAnchor.StubIndexReference,
) : SmartPointerElementInfo {
  override fun restoreElement(manager: SmartPointerManagerEx): PsiElement? = myStubIndexReference.retrieve()

  override fun elementHashCode(): Int = myStubIndexReference.hashCode()

  override fun pointsToTheSameElementAs(other: SmartPointerElementInfo, manager: SmartPointerManagerEx): Boolean =
    other is ClsElementInfo && myStubIndexReference == other.myStubIndexReference

  override val virtualFile: VirtualFile
    get() = myStubIndexReference.virtualFile

  override fun getRange(manager: SmartPointerManagerEx): Segment? = null

  override fun getPsiRange(manager: SmartPointerManagerEx): Segment? = null

  override fun restoreFile(manager: SmartPointerManagerEx): PsiFile? = myStubIndexReference.getFile()

  override fun toString(): String = myStubIndexReference.toString()
}
