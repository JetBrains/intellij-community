// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.cache;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

public final class CacheUtil {

  public static boolean isInComments(final IElementType tokenType) {
    final Language language = tokenType.getLanguage();

    for (CommentTokenSetProvider provider : CommentTokenSetProvider.EXTENSION.allForLanguage(language)) {
      if (provider.isInComments(tokenType)) {
        return true;
      }
    }

    boolean inComments = false;
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language);

    if (parserDefinition != null) {
      final TokenSet commentTokens = parserDefinition.getCommentTokens();

      if (commentTokens.contains(tokenType)) {
        inComments = true;
      }
    }
    return inComments;
  }
}
