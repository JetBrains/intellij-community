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
  public static final DefaultFactory DEFAULT = new DefaultFactory();
  private static final CharTable WHITSPACES = new CharTableImpl();

  @Nullable
  public abstract CompositeElement createComposite(IElementType type);

  @Nullable
  public LazyParseableElement createLazy(ILazyParseableElementType type, CharSequence text) {
    if (type instanceof IFileElementType) {
      return new FileElement(type, text);
    }
    
    return new LazyParseableElement(type, text);
  }

  @Nullable
  public abstract LeafElement createLeaf(IElementType type, CharSequence text);

  @NotNull
  public static LazyParseableElement lazy(ILazyParseableElementType type, CharSequence text) {
    ASTNode node = type.createNode(text);
    if (node != null) return (LazyParseableElement)node;

    if (type == TokenType.CODE_FRAGMENT) {
      return new CodeFragmentElement(null);
    }

    LazyParseableElement psi = factory(type).createLazy(type, text);
    return psi != null ? psi : DEFAULT.createLazy(type, text);
  }

  @Deprecated
  @NotNull
  public static LeafElement leaf(IElementType type, CharSequence fileText, int start, int end, CharTable table) {
    return leaf(type, table.intern(fileText, start, end));
  }

  @NotNull
  public static LeafElement leaf(IElementType type, CharSequence text) {
    if (type == TokenType.WHITE_SPACE) {
      return new PsiWhiteSpaceImpl(text);
    }

    if (type instanceof ILeafElementType) {
      return (LeafElement)((ILeafElementType)type).createLeafNode(text);
    }

    final LeafElement customLeaf = factory(type).createLeaf(type, text);
    return customLeaf != null ? customLeaf : DEFAULT.createLeaf(type, text);
  }

  private static ASTFactory factory(IElementType type) {
    return LanguageASTFactory.INSTANCE.forLanguage(type.getLanguage());
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
      return new CodeFragmentElement(null);
    }

    final CompositeElement customComposite = factory(type).createComposite(type);
    return customComposite != null ? customComposite : DEFAULT.createComposite(type);
  }

  private static class DefaultFactory extends ASTFactory {
    @Override
    @NotNull
    public CompositeElement createComposite(IElementType type) {
      if (type instanceof IFileElementType) {
        return new FileElement(type, null);
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
