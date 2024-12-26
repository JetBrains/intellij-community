// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.LazyParseableElement;
import org.jetbrains.annotations.NotNull;

public final class HighlightingPsiUtil {
  public static boolean hasReferenceInside(@NotNull PsiElement psiElement) {
    boolean[] result = new boolean[1];
    psiElement.accept(new PsiRecursiveElementWalkingVisitor(){
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (element instanceof PsiReference) { // reference was deleted/appeared, has to rehighlight all
          result[0] = true;
          stopWalking();
        }
        if (element instanceof PsiNameIdentifierOwner) {  // PsiMember, e.g. PsiClass or PsiMethod, was modified - no need to drill into because we have to rehighlight all anyway
          result[0] = true;
          stopWalking();
        }
        ASTNode node;
        if (element instanceof LazyParseableElement && !((LazyParseableElement)element).isParsed()
            || (node = element.getNode()) instanceof LazyParseableElement && !((LazyParseableElement)node).isParsed()) {
          // do not expand chameleons unnecessarily
          return;
        }
        super.visitElement(element);
      }
    });
    return result[0];
  }
}
