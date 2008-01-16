/*
 * @author max
 */
package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTFactory;
import com.intellij.psi.PlainTextTokenTypes;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.Nullable;

public class PlainTextASTFactory extends ASTFactory {
  @Nullable
  public CompositeElement createComposite(final IElementType type) {
    return null;
  }

  @Nullable
  public LeafElement createLeaf(final IElementType type, final CharSequence fileText, final int start, final int end, final CharTable table) {
    if (type == PlainTextTokenTypes.PLAIN_TEXT) {
      return new PsiPlainTextImpl(fileText, start, end, table);
    }
    return null;
  }
}