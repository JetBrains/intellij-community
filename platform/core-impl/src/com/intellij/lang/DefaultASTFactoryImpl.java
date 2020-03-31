// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class DefaultASTFactoryImpl extends ASTFactory implements DefaultASTFactory {
  @Override
  @NotNull
  public LazyParseableElement createLazy(@NotNull final ILazyParseableElementType type, final CharSequence text) {
    if (type instanceof IFileElementType) {
      return new FileElement(type, text);
    }

    return new LazyParseableElement(type, text);
  }

  @Override
  @NotNull
  public CompositeElement createComposite(@NotNull final IElementType type) {
    if (type instanceof IFileElementType) {
      return new FileElement(type, null);
    }

    return new CompositeElement(type);
  }

  @Override
  @NotNull
  public LeafElement createLeaf(@NotNull final IElementType type, @NotNull final CharSequence text) {
    final Language lang = type.getLanguage();
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
    if (parserDefinition != null) {
      if (parserDefinition.getCommentTokens().contains(type)) {
        return createComment(type, text);
      }
    }

    return new LeafPsiElement(type, text);
  }

  @NotNull
  @Override
  public LeafElement createComment(@NotNull IElementType type, @NotNull CharSequence text) {
    return new PsiCommentImpl(type, text);
  }
}
