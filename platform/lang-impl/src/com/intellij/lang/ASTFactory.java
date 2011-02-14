/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerUtil;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.CharTableImpl;
import com.intellij.psi.impl.source.CodeFragmentElement;
import com.intellij.psi.impl.source.DummyHolderElement;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.*;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/*
 * @author max
 */
public abstract class ASTFactory {
  public static final DefaultFactory DEFAULT = new DefaultFactory();

  private static final CharTable WHITESPACES = new CharTableImpl();

  // interface methods

  @Nullable
  public LazyParseableElement createLazy(final ILazyParseableElementType type, final CharSequence text) {
    return null;
  }

  @Nullable
  public CompositeElement createComposite(final IElementType type) {
    return null;
  }

  @Nullable
  public LeafElement createLeaf(final IElementType type, final CharSequence text) {
    return null;
  }

  // factory methods

  @NotNull
  public static LazyParseableElement lazy(@NotNull final ILazyParseableElementType type, final CharSequence text) {
    final ASTNode node = type.createNode(text);
    if (node != null) return (LazyParseableElement)node;

    if (type == TokenType.CODE_FRAGMENT) {
      return new CodeFragmentElement(null);
    }
    else if (type == TokenType.DUMMY_HOLDER) {
      return new DummyHolderElement(text);
    }

    final LazyParseableElement customLazy = factory(type).createLazy(type, text);
    return customLazy != null ? customLazy : DEFAULT.createLazy(type, text);
  }

  @NotNull
  public static CompositeElement composite(@NotNull final IElementType type) {
    if (type instanceof ICompositeElementType) {
      return (CompositeElement)((ICompositeElementType)type).createCompositeNode();
    }

    final CompositeElement customComposite = factory(type).createComposite(type);
    return customComposite != null ? customComposite : DEFAULT.createComposite(type);
  }

  @NotNull
  public static LeafElement leaf(@NotNull final IElementType type, final CharSequence text) {
    if (type == TokenType.WHITE_SPACE) {
      return new PsiWhiteSpaceImpl(text);
    }

    if (type instanceof ILeafElementType) {
      return (LeafElement)((ILeafElementType)type).createLeafNode(text);
    }

    final LeafElement customLeaf = factory(type).createLeaf(type, text);
    return customLeaf != null ? customLeaf : DEFAULT.createLeaf(type, text);
  }

  private static ASTFactory factory(final IElementType type) {
    return LanguageASTFactory.INSTANCE.forLanguage(type.getLanguage());
  }

  @NotNull
  public static LeafElement whitespace(final CharSequence text) {
    final PsiWhiteSpaceImpl w = new PsiWhiteSpaceImpl(WHITESPACES.intern(text));
    CodeEditUtil.setNodeGenerated(w, true);
    return w;
  }

  /**
   * @deprecated use {@link #leaf(com.intellij.psi.tree.IElementType, CharSequence)} (to remove in IDEA 11).
   */
  @NotNull
  public static LeafElement leaf(IElementType type, CharSequence fileText, int start, int end, CharTable table) {
    return leaf(type, table.intern(fileText, start, end));
  }

  /**
   * @deprecated use {@link #leaf(com.intellij.psi.tree.IElementType, CharSequence)} (to remove in IDEA 11).
   */
  @NotNull
  public static LeafElement leaf(IElementType type, CharSequence text, CharTable table) {
    return leaf(type, table.intern(text));
  }

  /**
   * @deprecated use {@link #leaf(com.intellij.psi.tree.IElementType, CharSequence)} (to remove in IDEA 11).
   */
  @NotNull
  public static LeafElement leaf(final Lexer lexer, final CharTable charTable) {
    return leaf(lexer.getTokenType(), LexerUtil.internToken(lexer, charTable));
  }

  // default implementation

  private static class DefaultFactory extends ASTFactory {
    @Override
    @NotNull
    public LazyParseableElement createLazy(final ILazyParseableElementType type, final CharSequence text) {
      if (type instanceof IFileElementType) {
        return new FileElement(type, text);
      }

      return new LazyParseableElement(type, text);
    }

    @Override
    @NotNull
    public CompositeElement createComposite(final IElementType type) {
      if (type instanceof IFileElementType) {
        return new FileElement(type, null);
      }

      return new CompositeElement(type);
    }

    @Override
    @NotNull
    public LeafElement createLeaf(final IElementType type, final CharSequence text) {
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
