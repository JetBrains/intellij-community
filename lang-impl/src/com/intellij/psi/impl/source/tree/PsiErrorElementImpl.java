
package com.intellij.psi.impl.source.tree;

import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.TokenType;
import org.jetbrains.annotations.NotNull;

public class PsiErrorElementImpl extends CompositePsiElement implements PsiErrorElement{
  private String myErrorDescription;

  public PsiErrorElementImpl() {
    super(TokenType.ERROR_ELEMENT);
  }

  public void setErrorDescription(String errorDescription) {
    myErrorDescription = errorDescription;
  }

  public String getErrorDescription() {
    return myErrorDescription;
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    visitor.visitErrorElement(this);
  }

  public String toString(){
    return "PsiErrorElement:" + getErrorDescription();
  }
}