package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.impl.source.CharTableImpl;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;

public class PsiKeywordImpl extends LeafPsiElement implements PsiKeyword, PsiJavaToken {
  public PsiKeywordImpl(IElementType type, CharSequence text) {
    super(type, text);
  }

  public IElementType getTokenType(){
    return getElementType();
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitKeyword(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString(){
    return "PsiKeyword:" + getText();
  }

  static {
    for(Field field: PsiKeyword.class.getFields()) {
      CharTableImpl.staticIntern(field.getName().toLowerCase());
    }
  }
}
