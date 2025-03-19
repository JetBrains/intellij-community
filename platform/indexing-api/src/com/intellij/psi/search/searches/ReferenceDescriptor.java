// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search.searches;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.Function;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

@Internal
public final class ReferenceDescriptor {
  public static final @NotNull Function<PsiReference, ReferenceDescriptor> MAPPER = psiReference -> {
    final PsiElement element = psiReference.getElement();
    final PsiFile file1 = element.getContainingFile();
    TextRange textRange = element.getTextRange();
    int startOffset = textRange != null ? textRange.getStartOffset() : 0;
    return new ReferenceDescriptor(file1.getViewProvider().getVirtualFile(), startOffset + psiReference.getRangeInElement().getStartOffset());
  };
  private final VirtualFile file;
  private final int offset;

  private ReferenceDescriptor(@NotNull VirtualFile file, int offset) {
    this.file = file;
    this.offset = offset;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ReferenceDescriptor that)) return false;

    if (offset != that.offset) return false;
    return file.equals(that.file);
  }

  @Override
  public int hashCode() {
    return 31 * file.hashCode() + offset;
  }
}
