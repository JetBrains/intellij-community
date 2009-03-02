/*
 * @author max
 */
package com.intellij.psi.impl.source.tree;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class ForeignLeafPsiElement extends LeafPsiElement {
  public ForeignLeafPsiElement(IElementType type, CharSequence text) {
    super(type, text);
  }

  public LeafElement findLeafElementAt(int offset) {
    return null;
  }

  @Override
  public boolean textMatches(@NotNull CharSequence seq) {
    return false;
  }

  @Override
  public boolean textMatches(@NotNull PsiElement element) {
    return false;
  }

  @Override
  public int getTextLength() {
    return 0;
  }

  @Override
  public int getNotCachedLength() {
    return 0;
  }

  @Override
  public int getStartOffset() {
    return 0;
  }

  @Override
  public String toString() {
    return "ForeignLeaf(" + getElementType() + ": " + getText() + ")";
  }
}
