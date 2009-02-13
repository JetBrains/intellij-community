package com.intellij.psi.impl.source.tree;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.TokenType;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import org.jetbrains.annotations.NotNull;

public class PsiWhiteSpaceImpl extends LeafPsiElement implements PsiWhiteSpace {
  public PsiWhiteSpaceImpl(CharSequence text) {
    super(TokenType.WHITE_SPACE, text);
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    visitor.visitWhiteSpace(this);
  }

  public String toString(){
    return "PsiWhiteSpace";
  }

  @NotNull
  public Language getLanguage() {
    PsiElement master = getNextSibling();
    if (master == null || master instanceof OuterLanguageElement) master = getParent();
    return master.getLanguage();
  }
}
