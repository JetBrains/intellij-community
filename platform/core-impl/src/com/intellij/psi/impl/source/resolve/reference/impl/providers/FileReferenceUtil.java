// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  public static @Nullable PsiFile findFile(@Nullable PsiElement element) {
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
  public static @Nullable PsiFile findFile(PsiReference...references) {
    for (int i = references.length - 1; i >= 0; i--) {
      PsiReference ref = references[i];
      if (ref instanceof FileReferenceOwner && !(ref instanceof PsiFileReference)) {
        ref = ((FileReferenceOwner)ref).getLastFileReference();
      }
      if (ref instanceof PsiFileReference) {
        PsiElement file = references[i].resolve();
        if(file instanceof PsiFile ) return (PsiFile)file;
      }
    }
    return null;
  }

  public static @Nullable PsiFileReference findFileReference(@NotNull PsiElement element) {
    PsiReference[] references = element.getReferences();
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
