package com.intellij.psi.impl.source.javadoc;

import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class PsiDocTokenImpl extends LeafPsiElement implements PsiDocToken{
  public PsiDocTokenImpl(IElementType type, CharSequence text) {
    super(type, text);
  }

  public IElementType getTokenType() {
    return getElementType();
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitDocToken(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString(){
    return "PsiDocToken:" + getTokenType().toString();
  }
}
