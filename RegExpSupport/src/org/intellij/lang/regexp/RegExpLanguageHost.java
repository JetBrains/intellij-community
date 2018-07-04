/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import java.util.EnumSet;

/**
 * @author yole
 */
public interface RegExpLanguageHost {

  EnumSet<RegExpGroup.Type> EMPTY_NAMED_GROUP_TYPES = EnumSet.noneOf(RegExpGroup.Type.class);

  boolean characterNeedsEscaping(char c);
  boolean supportsPerl5EmbeddedComments();
  boolean supportsPossessiveQuantifiers();
  boolean supportsPythonConditionalRefs();
  boolean supportsNamedGroupSyntax(RegExpGroup group);
  boolean supportsNamedGroupRefSyntax(RegExpNamedGroupRef ref);
  @NotNull
  default EnumSet<RegExpGroup.Type> getSupportedNamedGroupTypes(RegExpElement context) {
    return EMPTY_NAMED_GROUP_TYPES;
  }
  boolean supportsExtendedHexCharacter(RegExpChar regExpChar);

  default boolean isValidGroupName(String name, @NotNull RegExpGroup group) {
    for (int i = 0, length = name.length(); i < length; i++) {
      final char c = name.charAt(i);
      if (!AsciiUtil.isLetterOrDigit(c) && c != '_') {
        return false;
      }
    }
    return true;
  }

  default boolean supportsSimpleClass(RegExpSimpleClass simpleClass) {
    return true;
  }

  default boolean supportsNamedCharacters(RegExpNamedCharacter namedCharacter) {
    return false;
  }

  default boolean isValidNamedCharacter(RegExpNamedCharacter namedCharacter) {
    return supportsNamedCharacters(namedCharacter);
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

  /**
   * @param number  the number element to extract the value from
   * @return the value, or null when the value is out of range
   */
  @Nullable
  default Number getQuantifierValue(@NotNull RegExpNumber number) {
    return Double.parseDouble(number.getText());
  }

  default Lookbehind supportsLookbehind(@NotNull RegExpGroup lookbehindGroup) {
    return Lookbehind.FULL; // to not break existing implementations, although rarely actually supported.
  }

  enum Lookbehind {
    /** Lookbehind not supported. */
    NOT_SUPPORTED,

    /**
     * Alternation inside lookbehind (a|b|c) branches must have same length,
     * finite repetition with identical min, max values (a{3} or a{3,3}) allowed.
     */
    FIXED_LENGTH_ALTERNATION,

    /** Alternation (a|bc|def) branches inside look behind may have different length */
    VARIABLE_LENGTH_ALTERNATION,

    /** Finite repetition inside lookbehind with different minimum, maximum values allowed */
    FINITE_REPETITION,

    /** Full regex syntax inside lookbehind, i.e. star (*) and plus (*) repetition and backreferences, allowed. */
    FULL
  }
}
