package com.intellij.psi.impl.source.tree;

import com.intellij.psi.PlainTextTokenTypes;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiPlainText;
import org.jetbrains.annotations.NotNull;

public class PsiPlainTextImpl extends OwnBufferLeafPsiElement implements PsiPlainText {
  protected PsiPlainTextImpl(CharSequence text) {
    super(PlainTextTokenTypes.PLAIN_TEXT, text);
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    visitor.visitPlainText(this);
  }

  public String toString(){
    return "PsiPlainText";
  }
}
