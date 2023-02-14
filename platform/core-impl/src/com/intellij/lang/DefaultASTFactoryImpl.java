// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import org.jetbrains.annotations.NotNull;

public class DefaultASTFactoryImpl extends ASTFactory implements DefaultASTFactory {
  @Override
  public @NotNull LazyParseableElement createLazy(@NotNull ILazyParseableElementType type, CharSequence text) {
    if (type instanceof IFileElementType) {
      return new FileElement(type, text);
    }

    return new LazyParseableElement(type, text);
  }

  @Override
  public @NotNull CompositeElement createComposite(@NotNull IElementType type) {
    if (type instanceof IFileElementType) {
      return new FileElement(type, null);
    }

    return new CompositeElement(type);
  }

  @Override
  public @NotNull LeafElement createLeaf(@NotNull IElementType type, @NotNull CharSequence text) {
    ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(type.getLanguage());
    if (parserDefinition != null && parserDefinition.getCommentTokens().contains(type)) {
      return createComment(type, text);
    }

    return new LeafPsiElement(type, text);
  }

  @Override
  public @NotNull LeafElement createComment(@NotNull IElementType type, @NotNull CharSequence text) {
    return new PsiCommentImpl(type, text);
  }
}
