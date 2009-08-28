/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;

/**
 * @author peter
 */
public class FileReferenceCharFilter extends CharFilter{
  public Result acceptChar(char c, int prefixLength, Lookup lookup) {
    final PsiFile file = lookup.getPsiFile();
    if (file == null) {
      return null;
    }

    final LookupElement item = lookup.getCurrentItem();
    if (item != null && item.getObject() instanceof PsiFileSystemItem) {
      final PsiReference reference = file.findReferenceAt(lookup.getEditor().getCaretModel().getOffset());
      if (reference instanceof FileReference) return Result.HIDE_LOOKUP;

      if (reference instanceof PsiMultiReference) {
        for (PsiReference psiReference : ((PsiMultiReference)reference).getReferences()) {
          if (psiReference instanceof FileReference) return Result.HIDE_LOOKUP;
        }
      }

    }


    return null;
  }
}
