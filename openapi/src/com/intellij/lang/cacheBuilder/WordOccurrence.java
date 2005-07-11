/*
 * Copyright (c) 2000-05 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.lang.cacheBuilder;

import org.jetbrains.annotations.Nullable;

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

    private String myName;

    private Kind(String name) {
      myName = name;
    }

    public String toString() {
      return "WordOccurrence.Kind(" + myName + ")";
    }
  }
}
