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
package com.intellij.patterns;

import org.jetbrains.annotations.NotNull;
import com.intellij.util.ProcessingContext;

/**
 * @author peter
 */
public class CharPattern extends ObjectPattern<Character, CharPattern> {
  protected CharPattern() {
    super(Character.class);
    
  }

  public CharPattern javaIdentifierPart() {
    return with(new PatternCondition<Character>("javaIdentifierPart") {
      public boolean accepts(@NotNull final Character character, final ProcessingContext context) {
        return Character.isJavaIdentifierPart(character.charValue());
      }
    });
  }

  public CharPattern javaIdentifierStart() {
    return with(new PatternCondition<Character>("javaIdentifierStart") {
      public boolean accepts(@NotNull final Character character, final ProcessingContext context) {
        return Character.isJavaIdentifierStart(character.charValue());
      }
    });
  }

  public CharPattern whitespace() {
    return with(new PatternCondition<Character>("whitespace") {
      public boolean accepts(@NotNull final Character character, final ProcessingContext context) {
        return Character.isWhitespace(character.charValue());
      }
    });
  }

  public CharPattern letterOrDigit() {
    return with(new PatternCondition<Character>("letterOrDigit") {
      public boolean accepts(@NotNull final Character character, final ProcessingContext context) {
        return Character.isLetterOrDigit(character.charValue());
      }
    });
  }

}
