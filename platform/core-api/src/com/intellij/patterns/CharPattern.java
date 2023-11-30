// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns;

import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * Provides patterns for {@link Character} and contains conditions for digits, letters, whitespace and Java identifier parts.
 * <p>
 * Please see the <a href="https://plugins.jetbrains.com/docs/intellij/element-patterns.html">IntelliJ Platform Docs</a>
 * for a high-level overview.
 *
 * @see StandardPatterns#character()
 */
public class CharPattern extends ObjectPattern<Character, CharPattern> {
  private static final CharPattern ourJavaIdentifierStartCharacter = StandardPatterns.character().javaIdentifierStart();
  private static final CharPattern ourJavaIdentifierPartCharacter = StandardPatterns.character().javaIdentifierPart();
  private static final CharPattern ourWhitespaceCharacter = StandardPatterns.character().whitespace();
  private static final CharPattern ourLetterOrDigitCharacter = StandardPatterns.character().letterOrDigit();

  protected CharPattern() {
    super(Character.class);
  }

  public CharPattern javaIdentifierPart() {
    return with(new PatternCondition<Character>("javaIdentifierPart") {
      @Override
      public boolean accepts(final @NotNull Character character, final ProcessingContext context) {
        return Character.isJavaIdentifierPart(character.charValue());
      }
    });
  }

  public CharPattern javaIdentifierStart() {
    return with(new PatternCondition<Character>("javaIdentifierStart") {
      @Override
      public boolean accepts(final @NotNull Character character, final ProcessingContext context) {
        return Character.isJavaIdentifierStart(character.charValue());
      }
    });
  }

  public CharPattern whitespace() {
    return with(new PatternCondition<Character>("whitespace") {
      @Override
      public boolean accepts(final @NotNull Character character, final ProcessingContext context) {
        return Character.isWhitespace(character.charValue());
      }
    });
  }

  public CharPattern letterOrDigit() {
    return with(new PatternCondition<Character>("letterOrDigit") {
      @Override
      public boolean accepts(final @NotNull Character character, final ProcessingContext context) {
        return Character.isLetterOrDigit(character.charValue());
      }
    });
  }

  public static CharPattern javaIdentifierStartCharacter() {
    return ourJavaIdentifierStartCharacter;
  }

  public static CharPattern javaIdentifierPartCharacter() {
    return ourJavaIdentifierPartCharacter;
  }

  public static CharPattern letterOrDigitCharacter() {
    return ourLetterOrDigitCharacter;
  }

  public static CharPattern whitespaceCharacter() {
    return ourWhitespaceCharacter;
  }
}
