
package com.intellij.psi.impl.source.tree;

import com.intellij.lang.Language;
import com.intellij.psi.*;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
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

  @NotNull
  public Language getLanguage() {
    PsiElement master = this;
    while (true) {
      master = master.getNextSibling();
      if (master == null || master instanceof OuterLanguageElement) return getParent().getLanguage();
      if (master instanceof PsiWhiteSpace || master instanceof PsiErrorElement) continue;
      return master.getLanguage();
    }
  }
}