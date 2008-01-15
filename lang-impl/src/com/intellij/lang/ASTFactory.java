/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.CodeFragmentElement;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.*;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ASTFactory {
  public static final DefaultFactory DEFAULT = new DefaultFactory();

  @Nullable
  public abstract CompositeElement createComposite(IElementType type);
  @Nullable
  public abstract LeafElement createLeaf(IElementType type, CharSequence fileText, int start, int end, CharTable table);

  @NotNull
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
    final LeafElement customLeaf = factory.createLeaf(type, fileText, start, end, table);
    return customLeaf != null ? customLeaf : DEFAULT.createLeaf(type, fileText, start, end, table);
  }

  @NotNull
  public static CompositeElement composite(IElementType type) {
    if (type instanceof ICompositeElementType) {
      return (CompositeElement)((ICompositeElementType)type).createCompositeNode();
    }

    if (type == TokenType.CODE_FRAGMENT) {
      return new CodeFragmentElement();
    }

    final ASTFactory factory = LanguageASTFactory.INSTANCE.forLanguage(type.getLanguage());
    final CompositeElement customComposite = factory.createComposite(type);
    return customComposite != null ? customComposite : DEFAULT.createComposite(type);
  }

  private static class DefaultFactory extends ASTFactory {
    @Override
    @NotNull
    public CompositeElement createComposite(IElementType type) {
      if (type instanceof IFileElementType) {
        return new FileElement(type);
      }

      return new CompositeElement(type);
    }

    @Override
    @NotNull
    public LeafElement createLeaf(IElementType type, CharSequence fileText, int start, int end, CharTable table) {
      final Language lang = type.getLanguage();
      final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
      if (parserDefinition != null) {
        if (parserDefinition.getCommentTokens().contains(type)) {
          return new PsiCommentImpl(type, fileText, start, end, table);
        }
      }
      return new LeafPsiElement(type, fileText, start, end, table);
    }
  }
}