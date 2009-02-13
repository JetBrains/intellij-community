/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerUtil;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.CharTableImpl;
import com.intellij.psi.impl.source.CodeFragmentElement;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.*;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ASTFactory {
  public static final ASTFactory DEFAULT = new DefaultFactory();
  private static final CharTable WHITSPACES = new CharTableImpl();

  @Nullable
  public abstract CompositeElement createComposite(IElementType type);
  @Nullable
  public abstract LeafElement createLeaf(IElementType type, CharSequence text);

  @Deprecated
  @NotNull
  public static LeafElement leaf(IElementType type, CharSequence fileText, int start, int end, CharTable table) {
    return leaf(type, fileText);
  }

  @NotNull
  public static LeafElement leaf(IElementType type, CharSequence text) {
    if (type == TokenType.WHITE_SPACE) {
      return new PsiWhiteSpaceImpl(text);
    }

    if (type instanceof IChameleonElementType) {
      return new ChameleonElement(type, text);
    }

    if (type instanceof ILeafElementType) {
      return (LeafElement)((ILeafElementType)type).createLeafNode(text);
    }

    final Language lang = type.getLanguage();
    final ASTFactory factory = LanguageASTFactory.INSTANCE.forLanguage(lang);
    final LeafElement customLeaf = factory.createLeaf(type, text);
    return customLeaf != null ? customLeaf : DEFAULT.createLeaf(type, text);
  }

  public static LeafElement whitespace(CharSequence text) {
    PsiWhiteSpaceImpl w = new PsiWhiteSpaceImpl(WHITSPACES.intern(text));
    CodeEditUtil.setNodeGenerated(w, true);
    return w;
  }

  public static LeafElement leaf(IElementType type, CharSequence text, CharTable table) {
    return leaf(type, table.intern(text));
  }

  public static LeafElement leaf(final Lexer lexer, final CharTable charTable) {
    return leaf(lexer.getTokenType(), LexerUtil.internToken(lexer, charTable));
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
    public LeafElement createLeaf(IElementType type, CharSequence text) {
      final Language lang = type.getLanguage();
      final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
      if (parserDefinition != null) {
        if (parserDefinition.getCommentTokens().contains(type)) {
          return new PsiCommentImpl(type, text);
        }
      }
      return new LeafPsiElement(type, text);
    }
  }
}
