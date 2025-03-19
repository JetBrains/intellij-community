// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.model.Pointer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A pointer to a PSI element which can survive PSI reparse.
 *
 * @see SmartPointerManager#createSmartPsiElementPointer(PsiElement)
 */
public interface SmartPsiElementPointer<E extends PsiElement> extends Pointer<E> {
  /**
   * Returns the PSI element corresponding to the one from which the smart pointer was created in the
   * current state of the PSI file.
   *
   * @return the PSI element, or null if the PSI reparse has completely invalidated the pointer (for example,
   * the element referenced by the pointer has been deleted).
   */
  @Nullable
  E getElement();

  @Override
  default @Nullable E dereference() {
    return getElement();
  }

  @Nullable
  PsiFile getContainingFile();

  @NotNull
  Project getProject();

  VirtualFile getVirtualFile();

  /**
   * @return the range in the document. For committed document, it's the same as {@link #getPsiRange()}, for non-committed documents
   * the ranges may be changed (like in {@link com.intellij.openapi.editor.RangeMarker}) or even invalidated. In the latter case, returns null.
   * Returns null for invalid pointers.
   */
  @Nullable
  Segment getRange();

  /**
   * @return the range in the committed PSI file. May be different from {@link #getRange()} result when the document has been changed since commit.
   * Returns null for invalid pointers.
   */
  @Nullable
  Segment getPsiRange();
}
