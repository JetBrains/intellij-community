// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class FileReferenceCharFilter extends CharFilter{
  @Override
  public Result acceptChar(char c, int prefixLength, Lookup lookup) {
    final LookupElement item = lookup.getCurrentItem();
    if ('.' == c && item != null && item.getObject() instanceof PsiFileSystemItem) {
      PsiReference referenceAtCaret = lookup.getPsiFile().findReferenceAt(lookup.getLookupStart());
      if (referenceAtCaret != null && FileReference.findFileReference(referenceAtCaret) != null) {
        return Result.ADD_TO_PREFIX;
      }
    }

    return null;
  }
}
