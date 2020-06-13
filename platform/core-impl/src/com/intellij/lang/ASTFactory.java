// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.CharTableImpl;
import com.intellij.psi.impl.source.CodeFragmentElement;
import com.intellij.psi.impl.source.DummyHolderElement;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.LazyParseableElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.psi.tree.ILeafElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ASTFactory {
  private static final CharTable WHITESPACES = new CharTableImpl();

  // interface methods

  @Nullable
  public LazyParseableElement createLazy(@NotNull ILazyParseableElementType type, final CharSequence text) {
    return null;
  }

  @Nullable
  public CompositeElement createComposite(@NotNull IElementType type) {
    return null;
  }

  @Nullable
  public LeafElement createLeaf(@NotNull IElementType type, @NotNull CharSequence text) {
    return null;
  }

  // factory methods

  @NotNull
  public static LazyParseableElement lazy(@NotNull final ILazyParseableElementType type, CharSequence text) {
    final ASTNode node = type.createNode(text);
    if (node != null) return (LazyParseableElement)node;

    if (type == TokenType.CODE_FRAGMENT) {
      return new CodeFragmentElement(null);
    }
    if (type == TokenType.DUMMY_HOLDER) {
      return new DummyHolderElement(text);
    }

    final LazyParseableElement customLazy = factory(type).createLazy(type, text);
    return customLazy != null ? customLazy : DefaultFactoryHolder.DEFAULT.createLazy(type, text);
  }

  @NotNull
  public static CompositeElement composite(@NotNull final IElementType type) {
    if (type instanceof ICompositeElementType) {
      return (CompositeElement)((ICompositeElementType)type).createCompositeNode();
    }

    final CompositeElement customComposite = factory(type).createComposite(type);
    return customComposite != null ? customComposite : DefaultFactoryHolder.DEFAULT.createComposite(type);
  }

  @NotNull
  public static LeafElement leaf(@NotNull final IElementType type, @NotNull CharSequence text) {
    if (type == TokenType.WHITE_SPACE) {
      return new PsiWhiteSpaceImpl(text);
    }

    if (type instanceof ILeafElementType) {
      return (LeafElement)((ILeafElementType)type).createLeafNode(text);
    }

    final LeafElement customLeaf = factory(type).createLeaf(type, text);
    return customLeaf != null ? customLeaf : DefaultFactoryHolder.DEFAULT.createLeaf(type, text);
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

  public static final class DefaultFactoryHolder {
    public static final DefaultASTFactoryImpl DEFAULT = new DefaultASTFactoryImpl();

    private DefaultFactoryHolder() {
    }
  }
}
