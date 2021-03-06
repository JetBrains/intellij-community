// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public final class FileReferenceUtil {
  private FileReferenceUtil() {
  }

  /**
   * Returns a PsiFile element referenced to
   *
   * @param element some PsiElement
   * @return a PsiFile element referenced to
   * @see FileReference
   * @see com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
   */
  @Nullable
  public static PsiFile findFile(@Nullable PsiElement element) {
    return element == null ? null : findFile(element.getReferences());
  }

  /**
   * Iterates all references starting from the end and looking for FileReference,
   * when returns {@code resolve()} on it.
   *
   * @param references references, typically from PsiElement.getReferences()
   * @return PsiFile if the last FileReference resolves into a real file.
   * @see com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
   * @see PsiElement#getReferences()
   */
  @Nullable
  public static PsiFile findFile(PsiReference...references) {
    for (int i = references.length - 1; i >= 0; i--) {
      PsiReference ref = references[i];
      if (ref instanceof FileReferenceOwner && !(ref instanceof PsiFileReference)) {
        ref = ((FileReferenceOwner)ref).getLastFileReference();
      }
      if (ref instanceof PsiFileReference) {
        final PsiElement file = references[i].resolve();
        return file instanceof PsiFile ? (PsiFile)file : null;
      }
    }
    return null;
  }

  @Nullable
  public static PsiFileReference findFileReference(@NotNull PsiElement element) {
    final PsiReference[] references = element.getReferences();
    for (int i = references.length - 1; i >= 0; i--) {
      PsiReference ref = references[i];
      if (ref instanceof FileReferenceOwner && !(ref instanceof PsiFileReference)) {
        ref = ((FileReferenceOwner)ref).getLastFileReference();
      }
      if (ref instanceof PsiFileReference) {
        return (PsiFileReference)references[i];
      }
    }
    return null;
  }
}
