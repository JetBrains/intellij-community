/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.util;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

public class NonCodeUsageInfo extends MoveRenameUsageInfo{
  public final String newText;

  private NonCodeUsageInfo(PsiElement element, int startOffset, int endOffset, PsiElement referencedElement, String newText){
    super(element, null, startOffset, endOffset, referencedElement, true);
    this.newText = newText;
  }

  public static NonCodeUsageInfo create(PsiFile file,
                                        int startOffset,
                                        int endOffset,
                                        PsiElement referencedElement,
                                        String newText) {
    PsiElement element = file.findElementAt(startOffset);
    TextRange range;
    while(true){
      range = element.getTextRange();
      if (range.getEndOffset() < endOffset){
        element = element.getParent();
      }
      else{
        break;
      }
    }

    int elementStart = range.getStartOffset();
    startOffset -= elementStart;
    endOffset -= elementStart;
    return new NonCodeUsageInfo(element, startOffset, endOffset, referencedElement, newText);
  }
}
