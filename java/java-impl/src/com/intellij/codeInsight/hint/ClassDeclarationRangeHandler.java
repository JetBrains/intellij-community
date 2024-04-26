// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public final class ClassDeclarationRangeHandler implements DeclarationRangeHandler {
  @Override
  public @NotNull TextRange getDeclarationRange(final @NotNull PsiElement container) {
    PsiClass aClass = (PsiClass)container;
    if (aClass instanceof PsiAnonymousClass){
      PsiConstructorCall call = (PsiConstructorCall)aClass.getParent();
      int startOffset = call.getTextRange().getStartOffset();
      int endOffset = call.getArgumentList().getTextRange().getEndOffset();
      return new TextRange(startOffset, endOffset);
    }
    else{
      final PsiModifierList modifierList = aClass.getModifierList();
      int startOffset = modifierList == null ? aClass.getTextRange().getStartOffset() : modifierList.getTextRange().getStartOffset();
      final PsiReferenceList implementsList = aClass.getImplementsList();
      int endOffset = implementsList == null ? aClass.getTextRange().getEndOffset() : implementsList.getTextRange().getEndOffset();
      return new TextRange(startOffset, endOffset);
    }
  }
}
