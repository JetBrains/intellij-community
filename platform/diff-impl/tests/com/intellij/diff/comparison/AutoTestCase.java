/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.comparison;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.text.CharSequenceSubSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AutoTestCase extends UsefulTestCase {
  protected final Random myRng = new Random();
  private long myLastSeed = -1;
  private boolean myGotSeedException = false;

  @NotNull
  protected String generateText(int maxLength, int charCount, @NotNull Map<Integer, Character> chars) {
    int length = myRng.nextInt(maxLength + 1);
    StringBuilder builder = new StringBuilder(length);

    for (int i = 0; i < length; i++) {
      int rnd = myRng.nextInt(charCount);
      if (chars.containsKey(rnd)) {
        builder.append(chars.get(rnd));
      }
      else {
        builder.append((char)(rnd + 97));
      }
    }

    return builder.toString();
  }

  protected void rememberSeed() {
    myLastSeed = getCurrentSeed();
  }

  protected long getLastSeed() {
    return myLastSeed;
  }

  protected long getCurrentSeed() {
    if (myGotSeedException) return -1;
    try {
      Field seedField = myRng.getClass().getDeclaredField("seed");
      seedField.setAccessible(true);
      AtomicLong seedFieldValue = (AtomicLong)seedField.get(myRng);
      return seedFieldValue.get() ^ 0x5DEECE66DL;
    }
    catch (Exception e) {
      myGotSeedException = true;
      System.err.println("Can't get random seed: " + e.getMessage());
      return -1;
    }
  }

  @NotNull
  public static String textToReadableFormat(@Nullable Document text) {
    if (text == null) return "null";
    return "'" + text.getCharsSequence().toString().replace('\n', '*').replace('\t', '+') + "'";
  }

  public static void assertEqualsCharSequences(@NotNull CharSequence chunk1, @NotNull CharSequence chunk2,
                                               boolean ignoreSpaces, boolean skipLastNewline) {
    if (ignoreSpaces) {
      assertTrue(StringUtil.equalsIgnoreWhitespaces(chunk1, chunk2));
    }
    else {
      if (skipLastNewline) {
        CharSequence chunk12 = StringUtil.endsWithChar(chunk1, '\n') ? trimLastChar(chunk1) : null;
        CharSequence chunk22 = StringUtil.endsWithChar(chunk2, '\n') ? trimLastChar(chunk2) : null;

        if (StringUtil.equals(chunk1, chunk2)) return;
        if (StringUtil.equals(chunk12, chunk2)) return;
        if (StringUtil.equals(chunk1, chunk22)) return;

        assertTrue(false);
      }
      else {
        assertTrue(StringUtil.equals(chunk1, chunk2));
      }
    }
  }

  @NotNull
  private static CharSequence trimLastChar(@NotNull CharSequence text) {
    return new CharSequenceSubSequence(text, 0, text.length() - 1);
  }
}
