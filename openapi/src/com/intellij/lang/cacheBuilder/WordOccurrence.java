/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.lang.cacheBuilder;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

/**
 * A single word instance extracted by {@link WordsScanner}.
 *
 * @author max
 */

public class WordOccurrence {
  private Kind myKind;
  private CharSequence myText;

  /**
   * Creates a new occurrence with the specified text and kind.
   * @param text The text of the word.
   * @param kind The type of text where the word was encountered (code, comments or literals).
   */

  public WordOccurrence(final CharSequence text, @Nullable final Kind kind) {
    myKind = kind;
    myText = text;
  }

  /**
   * Returns the type of text where the word was encountered (code, comments or literals).
   * @return the kind of the occurrence.
   */

  @Nullable
  public Kind getKind() {
    return myKind;
  }

  /**
   * Returns the text of the occurred word.
   * @return the text of the word.
   */
  public CharSequence getText() {
    return myText;
  }

  /**
   * Defines possible locations where words can be encountered.
   */
  public static class Kind {
    /** Kind for words encountered in code (keywords and identifiers). */
    public static final Kind CODE = new Kind("CODE");
    /** Kind for words encountered in comments. */
    public static final Kind COMMENTS = new Kind("COMMENTS");
    /** Kind for words encountered in literals (particularly string literals). */
    public static final Kind LITERALS = new Kind("LITERALS");
    /** Kind for words encountered in languages different from the main language of the file. */
    public static final Kind FOREIGN_LANGUAGE = new Kind("FOREIGN_LANGUAGE");

    private String myName;

    private Kind(@NonNls String name) {
      myName = name;
    }

    @NonNls public String toString() {
      return "WordOccurrence.Kind(" + myName + ")";
    }
  }
}
