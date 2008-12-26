/*
 * @author max
 */
package com.intellij.psi.impl.source.tree;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

public class ForeignLeafPsiElement extends LeafPsiElement {
  public ForeignLeafPsiElement(IElementType type, CharSequence text, CharTable table) {
    this(type, text, 0, text.length(), table);
  }

  public ForeignLeafPsiElement(IElementType type, CharSequence buffer, int startOffset, int endOffset, CharTable table) {
    super(type, buffer, startOffset, endOffset, table);
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
  public int getStartOffset() {
    return 0;
  }
}