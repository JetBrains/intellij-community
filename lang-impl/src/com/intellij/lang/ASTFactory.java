/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;

public abstract class ASTFactory {
  public static ASTFactory DEFAULT = new ASTFactory() {
    @Override
    public CompositeElement createComposite(IElementType type) {
      return new CompositeElement(type);
    }

    @Override
    public LeafElement createLeaf(IElementType type, CharSequence fileText, int start, int end, CharTable table) {
      return new LeafPsiElement(type, fileText, start, end, table);
    }
  };

  public abstract CompositeElement createComposite(IElementType type);
  public abstract LeafElement createLeaf(IElementType type, CharSequence fileText, int start, int end, CharTable table);
}