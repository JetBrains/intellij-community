package com.intellij.psi.impl.source.tree;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.TokenType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

public class PsiWhiteSpaceImpl extends LeafPsiElement implements PsiWhiteSpace {
  public PsiWhiteSpaceImpl(CharSequence buffer, int startOffset, int endOffset, CharTable table) {
    super(TokenType.WHITE_SPACE, buffer, startOffset, endOffset, table);
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
    if (master == null) master = getParent();
    return master.getLanguage();
  }
}
