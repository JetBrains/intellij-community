/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.components.ServiceManager;
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
 * @author max
 */
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
  
  public static class DefaultFactoryHolder {
    public static final ASTFactory DEFAULT = (ASTFactory)ServiceManager.getService(DefaultASTFactory.class);

    private DefaultFactoryHolder() {
    }
  }
}
