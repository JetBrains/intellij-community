// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.smartPointers

import com.intellij.openapi.util.Segment
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilCore

/**
 * Holds a direct (hard) reference to a [PsiElement]. No range tracking or survival across
 * reparse — used for non-physical PSI that cannot be restored by position (e.g., light elements).
 */
internal class HardElementInfo(
  private val myElement: PsiElement,
) : SmartPointerElementInfo {
  override fun restoreElement(manager: SmartPointerManagerEx): PsiElement = myElement

  override fun restoreFile(manager: SmartPointerManagerEx): PsiFile? =
    if (myElement.isValid()) myElement.containingFile else null

  override fun elementHashCode(): Int = myElement.hashCode()

  override fun pointsToTheSameElementAs(other: SmartPointerElementInfo, manager: SmartPointerManagerEx): Boolean =
    other is HardElementInfo && myElement == other.myElement

  override val virtualFile: VirtualFile?
    get() = PsiUtilCore.getVirtualFile(myElement)

  override fun getRange(manager: SmartPointerManagerEx): Segment? = myElement.textRange

  override fun getPsiRange(manager: SmartPointerManagerEx): Segment? = getRange(manager)

  override fun toString(): String = "hard{$myElement of ${myElement.javaClass}}"
}
