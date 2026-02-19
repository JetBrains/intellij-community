// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.cacheBuilder;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * A single word instance extracted by {@link WordsScanner}.
 *
 * @author max
 */

public final class WordOccurrence {
  private Kind myKind;
  private CharSequence myText;
  private int myStart;
  private int myEnd;

  /**
   * Creates a new occurrence with the specified text fragment and kind.
   * @param text initial charsequence.
   * @param start start offset in initial char sequence.
   * @param end end offset in initial char sequence.
   * @param kind The type of text where the word was encountered (code, comments or literals).
   */

  public WordOccurrence(final CharSequence text, int start, int end, final @Nullable Kind kind) {
    myKind = kind;
    myText = text;
    myStart = start;
    myEnd = end;
  }

  /**
   * Updates occurrence with the specified text fragment and kind.
   * @param text initial charsequence.
   * @param start start offset in initial char sequence.
   * @param end end offset in initial char sequence.
   * @param kind The type of text where the word was encountered (code, comments or literals).
   */
  public void init(final CharSequence text, int start, int end, final @Nullable Kind kind) {
    myKind = kind;
    myText = text;
    myStart = start;
    myEnd = end;
  }

  /**
   * Returns the type of text where the word was encountered (code, comments or literals).
   * @return the kind of the occurrence.
   */

  public @Nullable Kind getKind() {
    return myKind;
  }

  /**
   * Returns the char sequence of the occurred word.
   * @return the text of the word.
   */
  public CharSequence getBaseText() {
    return myText;
  }

  public int getStart() {
    return myStart;
  }

  public int getEnd() {
    return myEnd;
  }

  /**
   * Defines possible locations where words can be encountered.
   */
  public static final class Kind {
    /** Kind for words encountered in code (keywords and identifiers). */
    public static final Kind CODE = new Kind("CODE");
    /** Kind for words encountered in comments. */
    public static final Kind COMMENTS = new Kind("COMMENTS");
    /** Kind for words encountered in literals (particularly string literals). */
    public static final Kind LITERALS = new Kind("LITERALS");
    /** Kind for words encountered in languages different from the main language of the file. */
    public static final Kind FOREIGN_LANGUAGE = new Kind("FOREIGN_LANGUAGE");

    private final String myName;

    private Kind(@NonNls String name) {
      myName = name;
    }

    @Override
    public @NonNls String toString() {
      return "WordOccurrence.Kind(" + myName + ")";
    }

    @TestOnly
    public @NotNull String getName() {
      return myName;
    }
  }
}
