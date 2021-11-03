// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import com.intellij.openapi.util.NlsSafe;
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

/**
 * <p>An expert-level extension point for exercising fine-grained control over abstract syntax tree (AST) element generation,
 * when PSI is not enough (see the <a href="https://plugins.jetbrains.com/docs/intellij/implementing-parser-and-psi.html">Implementing Parser and PSI</a>
 * section for description of PSI/AST relation).</p>
 *
 * <p>Registration is via {@code "com.intellij.lang.ast.factory"} extension point (see {@link LanguageASTFactory}).</p>
 */
public abstract class ASTFactory {
  private static final CharTable WHITESPACES = new CharTableImpl();

  // interface methods

  public @Nullable LazyParseableElement createLazy(@NotNull ILazyParseableElementType type, CharSequence text) {
    return null;
  }

  public @Nullable CompositeElement createComposite(@NotNull IElementType type) {
    return null;
  }

  public @Nullable LeafElement createLeaf(@NotNull IElementType type, @NotNull CharSequence text) {
    return null;
  }

  // factory methods

  public static @NotNull LazyParseableElement lazy(@NotNull ILazyParseableElementType type, CharSequence text) {
    ASTNode node = type.createNode(text);
    if (node != null) return (LazyParseableElement)node;

    if (type == TokenType.CODE_FRAGMENT) return new CodeFragmentElement(null);
    if (type == TokenType.DUMMY_HOLDER) return new DummyHolderElement(text);

    LazyParseableElement customLazy = factory(type).createLazy(type, text);
    return customLazy != null ? customLazy : DefaultFactoryHolder.DEFAULT.createLazy(type, text);
  }

  public static @NotNull CompositeElement composite(@NotNull IElementType type) {
    if (type instanceof ICompositeElementType) {
      return (CompositeElement)((ICompositeElementType)type).createCompositeNode();
    }

    CompositeElement customComposite = factory(type).createComposite(type);
    return customComposite != null ? customComposite : DefaultFactoryHolder.DEFAULT.createComposite(type);
  }

  public static @NotNull LeafElement leaf(@NotNull IElementType type, @NlsSafe @NotNull CharSequence text) {
    if (type == TokenType.WHITE_SPACE) {
      return new PsiWhiteSpaceImpl(text);
    }

    if (type instanceof ILeafElementType) {
      return (LeafElement)((ILeafElementType)type).createLeafNode(text);
    }

    LeafElement customLeaf = factory(type).createLeaf(type, text);
    return customLeaf != null ? customLeaf : DefaultFactoryHolder.DEFAULT.createLeaf(type, text);
  }

  private static ASTFactory factory(IElementType type) {
    return LanguageASTFactory.INSTANCE.forLanguage(type.getLanguage());
  }

  public static @NotNull LeafElement whitespace(CharSequence text) {
    PsiWhiteSpaceImpl w = new PsiWhiteSpaceImpl(WHITESPACES.intern(text));
    CodeEditUtil.setNodeGenerated(w, true);
    return w;
  }

  public static final class DefaultFactoryHolder {
    public static final DefaultASTFactoryImpl DEFAULT = new DefaultASTFactoryImpl();

    private DefaultFactoryHolder() { }
  }
}
