/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.intellij.lang.regexp;

import com.intellij.psi.PsiElement;
import org.intellij.lang.regexp.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface RegExpLanguageHost {
  boolean characterNeedsEscaping(char c);
  boolean supportsPerl5EmbeddedComments();
  boolean supportsPossessiveQuantifiers();
  boolean supportsPythonConditionalRefs();
  boolean supportsNamedGroupSyntax(RegExpGroup group);
  boolean supportsNamedGroupRefSyntax(RegExpNamedGroupRef ref);
  boolean supportsExtendedHexCharacter(RegExpChar regExpChar);

  default boolean supportsSimpleClass(RegExpSimpleClass simpleClass) {
    return true;
  }

  default boolean supportsBoundary(RegExpBoundary boundary) {
    switch (boundary.getType()) {
      case UNICODE_EXTENDED_GRAPHEME:
        return false;
      case LINE_START:
      case LINE_END:
      case WORD:
      case NON_WORD:
      case BEGIN:
      case END:
      case END_NO_LINE_TERM:
      case PREVIOUS_MATCH:
      default:
        return true;
    }
  }

  default boolean supportsLiteralBackspace(RegExpChar aChar) {
    return true;
  }

  default boolean supportsInlineOptionFlag(char flag, PsiElement context) {
    return true;
  }

  boolean isValidCategory(@NotNull String category);
  @NotNull
  String[][] getAllKnownProperties();
  @Nullable
  String getPropertyDescription(@Nullable final String name);
  @NotNull
  String[][] getKnownCharacterClasses();
}
