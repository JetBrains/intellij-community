// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.smartPointers

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.Segment
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

/**
 * Abstract base that stores metadata for a [com.intellij.psi.SmartPsiElementPointer].
 * Each inheritor captures a different restoration strategy depending on the kind of PSI element
 * (file, directory, regular element, compiled element, injected element, or a hard reference).
 */
@ApiStatus.Internal
sealed interface SmartPointerElementInfo {
  val documentToSynchronize: Document?
    get() = null

  fun fastenBelt(manager: SmartPointerManagerEx) {
  }

  fun restoreElement(manager: SmartPointerManagerEx): PsiElement?

  fun restoreFile(manager: SmartPointerManagerEx): PsiFile?

  fun elementHashCode(): Int // must be immutable

  fun pointsToTheSameElementAs(other: SmartPointerElementInfo, manager: SmartPointerManagerEx): Boolean

  val virtualFile: VirtualFile?

  fun getRange(manager: SmartPointerManagerEx): Segment?

  fun getPsiRange(manager: SmartPointerManagerEx): Segment?

  fun cleanup() {
  }
}
