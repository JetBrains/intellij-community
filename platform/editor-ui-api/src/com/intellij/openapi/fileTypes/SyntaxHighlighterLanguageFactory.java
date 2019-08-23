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

/*
 * @author max
 */
package com.intellij.openapi.fileTypes;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class SyntaxHighlighterLanguageFactory extends LanguageExtension<SyntaxHighlighterFactory> {
  SyntaxHighlighterLanguageFactory() {
    super("com.intellij.lang.syntaxHighlighterFactory");
  }

  @NotNull
  @Override
  protected List<SyntaxHighlighterFactory> buildExtensions(@NotNull String stringKey, @NotNull Language key) {
    List<SyntaxHighlighterFactory> fromEP = super.buildExtensions(stringKey, key);
    if (fromEP.isEmpty()) {
      SyntaxHighlighter highlighter = LanguageSyntaxHighlighters.INSTANCE.forLanguage(key);
      if (highlighter != null) {
        SyntaxHighlighterFactory defaultFactory = new SingleLazyInstanceSyntaxHighlighterFactory() {
          @NotNull
          @Override
          protected SyntaxHighlighter createHighlighter() {
            return highlighter;
          }
        };
        return Collections.singletonList(defaultFactory);
      }
    }
    return fromEP;
  }
}
