// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.patterns;

import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class CharPattern extends ObjectPattern<Character, CharPattern> {

  private final CharPattern myJavaIdentifierPartPattern = with(new PatternCondition<Character>("javaIdentifierPart") {
    @Override
    public boolean accepts(@NotNull final Character character, final ProcessingContext context) {
      return Character.isJavaIdentifierPart(character.charValue());
    }
  });
  private final CharPattern myJavaIdentifierStartPattern = with(new PatternCondition<Character>("javaIdentifierStart") {
    @Override
    public boolean accepts(@NotNull final Character character, final ProcessingContext context) {
      return Character.isJavaIdentifierStart(character.charValue());
    }
  });
  private final CharPattern myWhitespacePattern = with(new PatternCondition<Character>("whitespace") {
    @Override
    public boolean accepts(@NotNull final Character character, final ProcessingContext context) {
      return Character.isWhitespace(character.charValue());
    }
  });
  private final CharPattern myLetterOrDigitPattern = with(new PatternCondition<Character>("letterOrDigit") {
    @Override
    public boolean accepts(@NotNull final Character character, final ProcessingContext context) {
      return Character.isLetterOrDigit(character.charValue());
    }
  });

  protected CharPattern() {
    super(Character.class);
  }

  public CharPattern javaIdentifierPart() {
    return myJavaIdentifierPartPattern;
  }

  public CharPattern javaIdentifierStart() {
    return myJavaIdentifierStartPattern;
  }

  public CharPattern whitespace() {
    return myWhitespacePattern;
  }

  public CharPattern letterOrDigit() {
    return myLetterOrDigitPattern;
  }
}
