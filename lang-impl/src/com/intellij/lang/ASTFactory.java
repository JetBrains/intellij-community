/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IChameleonElementType;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILeafElementType;
import com.intellij.util.CharTable;

public abstract class ASTFactory {
  public static final ASTFactory DEFAULT = new ASTFactory() {
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

  public static LeafElement leaf(IElementType type, CharSequence fileText, int start, int end, CharTable table) {
    if (type == TokenType.WHITE_SPACE) {
      return new PsiWhiteSpaceImpl(fileText, start, end, table);
    }
    
    if (type instanceof IChameleonElementType) {
      return new ChameleonElement(type, fileText, start, end, table);
    }

    if (type instanceof ILeafElementType) {
      return (LeafElement)((ILeafElementType)type).createLeafNode(fileText, start, end, table);
    }

    final Language lang = type.getLanguage();
    final ASTFactory factory = LanguageASTFactory.INSTANCE.forLanguage(lang);
    return factory.createLeaf(type, fileText, start, end, table);
  }

  public static CompositeElement composite(IElementType type) {
    if (type instanceof ICompositeElementType) {
      return (CompositeElement)((ICompositeElementType)type).createCompositeNode();
    }

    final ASTFactory factory = LanguageASTFactory.INSTANCE.forLanguage(type.getLanguage());
    return factory.createComposite(type);
  }
}