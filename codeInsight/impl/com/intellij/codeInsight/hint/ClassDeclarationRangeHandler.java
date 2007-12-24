package com.intellij.codeInsight.hint;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiConstructorCall;

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
      int startOffset = aClass.getModifierList().getTextRange().getStartOffset();
      int endOffset = aClass.getImplementsList().getTextRange().getEndOffset();
      return new TextRange(startOffset, endOffset);
    }
  }
}
