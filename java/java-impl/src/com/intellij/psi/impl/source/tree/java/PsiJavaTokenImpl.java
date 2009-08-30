package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class PsiJavaTokenImpl extends LeafPsiElement implements PsiJavaToken{
  public PsiJavaTokenImpl(IElementType type, CharSequence text) {
    super(type, text);
  }

  public IElementType getTokenType() {
    return getElementType();
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitJavaToken(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString(){
    return "PsiJavaToken:" + getElementType().toString();
  }
}
