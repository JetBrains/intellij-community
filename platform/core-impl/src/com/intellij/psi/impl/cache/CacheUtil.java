/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.psi.impl.cache;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

public class CacheUtil {

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
