package com.intellij.codeInsight.hint;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;

public class ClassDeclarationRangeHandler implements DeclarationRangeHandler {
  @NotNull
  public TextRange getDeclarationRange(@NotNull final PsiElement container) {
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
