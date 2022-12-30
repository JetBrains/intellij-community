// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp;

import com.intellij.psi.PsiElement;
import org.intellij.lang.regexp.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;


public interface RegExpLanguageHost {

  EnumSet<RegExpGroup.Type> EMPTY_NAMED_GROUP_TYPES = EnumSet.noneOf(RegExpGroup.Type.class);
  String[][] EMPTY_COMPLETION_ITEMS_ARRAY = new String[0][];

  /**
   * @deprecated Use {@link #characterNeedsEscaping(char, boolean)} instead.
   */
  @Deprecated
  boolean characterNeedsEscaping(char c);

  /**
   * Returns whether the given character needs to be escaped to be treated as a literal.
   * @param c a character to be considered.
   * @param isInClass whether the character is within a RegExpClass (ie, within "[...]").
   */
  default boolean characterNeedsEscaping(char c, boolean isInClass) {
    return characterNeedsEscaping(c);
  }

  boolean supportsPerl5EmbeddedComments();
  boolean supportsPossessiveQuantifiers();
  default boolean isDuplicateGroupNamesAllowed(@NotNull RegExpGroup group) {
    return false;
  }

  /**
   * @return true, if this dialects support conditionals, i.e. the following construct: {@code (?(1)then|else)}
   */
  boolean supportsPythonConditionalRefs();

  /**
   * @param condition  a RegExpBackRef, RegExpNamedGroupRef or RegExpGroup instance.
   * @return true, if this type of conditional condition is supported
   */
  default boolean supportConditionalCondition(RegExpAtom condition) {
    return true;
  }

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
    return switch (boundary.getType()) {
      case UNICODE_EXTENDED_GRAPHEME, RESET_MATCH -> false;
      case LINE_START, LINE_END, WORD, NON_WORD, BEGIN, END, END_NO_LINE_TERM, PREVIOUS_MATCH -> true;
    };
  }

  default boolean supportsLiteralBackspace(RegExpChar aChar) {
    return true;
  }

  default boolean supportsInlineOptionFlag(char flag, PsiElement context) {
    return true;
  }

  boolean isValidCategory(@NotNull String category);

  default boolean isValidPropertyName(@NotNull String name) {
    return true;
  }

  default boolean isValidPropertyValue(@NotNull String propertyName, @NotNull String value){
    return true;
  }

  String[] @NotNull [] getAllKnownProperties();
  @Nullable
  String getPropertyDescription(@Nullable final String name);
  String[] @NotNull [] getKnownCharacterClasses();

  /**
   * @param number  the number element to extract the value from
   * @return the value, or null when the value is out of range
   */
  @Nullable
  default Number getQuantifierValue(@NotNull RegExpNumber number) {
    return Double.parseDouble(number.getUnescapedText());
  }

  default Lookbehind supportsLookbehind(@NotNull RegExpGroup lookbehindGroup) {
    return Lookbehind.FULL; // to not break existing implementations, although rarely actually supported.
  }

  default String[] @NotNull [] getAllPropertyValues(@NotNull String propertyName){
    return EMPTY_COMPLETION_ITEMS_ARRAY; 
  }

  default boolean supportsPropertySyntax(@NotNull PsiElement context) {
    return true;
  }

  default boolean belongsToConditionalExpression(@NotNull PsiElement element) {
    return false;
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
