// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * This sample provides an example of {@link HeavyBraceHighlighter HeavyBracesHighlighter}.
 * It introduces new braces ("<]" and "[>") and matches them naively.
 * It is used for testing purposes.
 */
@TestOnly
public class HeavyHighlighterSample extends HeavyBraceHighlighter {
  private final static String LEFT_BRACE = "<]";
  private final static String RIGHT_BRACE = "[>";

  private final static int BRACE_LENGTH = LEFT_BRACE.length();

  @Override
  protected @Nullable Pair<TextRange, TextRange> matchBrace(@NotNull PsiFile file, int offset) {
    Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    if (document == null) {
      return null;
    }

    CharSequence chars = document.getCharsSequence();
    int angleOffset = findPositionWithAngle(chars, offset);
    if (angleOffset < 0) {
      return null;
    }

    int currentOffset;
    int currentDirection;
    int balance;

    if (LEFT_BRACE.contentEquals(chars.subSequence(angleOffset, angleOffset + BRACE_LENGTH))) {
      currentOffset = angleOffset + BRACE_LENGTH; // moving right
      currentDirection = 1;
      balance = 1;
    }
    else {
      currentOffset = angleOffset - BRACE_LENGTH; // moving left
      currentDirection = -1;
      balance = -1;
    }

    while (currentOffset >= 0 && currentOffset + BRACE_LENGTH <= chars.length()) {
      CharSequence subSequence = chars.subSequence(currentOffset, currentOffset + BRACE_LENGTH);
      if (LEFT_BRACE.contentEquals(subSequence)) {
        balance++;
      }
      else if (RIGHT_BRACE.contentEquals(subSequence)) {
        balance--;
      }

      if (balance == 0) {
        TextRange firstAngle = new TextRange(angleOffset, angleOffset + BRACE_LENGTH);
        TextRange matchedAngle = new TextRange(currentOffset, currentOffset + BRACE_LENGTH);
        if (angleOffset < currentOffset) {
          return new Pair<TextRange, TextRange>(firstAngle, matchedAngle);
        }
        else {
          return new Pair<TextRange, TextRange>(matchedAngle, firstAngle);
        }
      }

      currentOffset += currentDirection;
    }

    return null;
  }


  private static boolean isBrace(@NotNull CharSequence candidate) {
    return LEFT_BRACE.contentEquals(candidate) || RIGHT_BRACE.contentEquals(candidate);
  }

  /**
   * Skips whitespaces and returns bracket position
   *
   * @return position of a bracket on the left, if exists, position of a bracket on the right, if exists, otherwise and -1 if no brackets found.
   */
  private static int findPositionWithAngle(@NotNull CharSequence chars, int startOffset) {
    int currentPos = startOffset;

    if (currentPos > 0 && currentPos - 1 + BRACE_LENGTH >= chars.length() &&
        isBrace(chars.subSequence(currentPos - 1, currentPos - 1 + BRACE_LENGTH))) {
      return currentPos - 1; // inside brace token <I] or [I>
    }

    while (currentPos >= BRACE_LENGTH - 1 && Character.isWhitespace(chars.charAt(currentPos))) {
      currentPos--;
    }

    if (currentPos >= BRACE_LENGTH - 1 && isBrace(chars.subSequence(currentPos + 1 - BRACE_LENGTH, currentPos + 1))) {
      return currentPos + 1 - BRACE_LENGTH;
    }

    currentPos = startOffset;
    int length = chars.length();
    while (currentPos + BRACE_LENGTH <= length && Character.isWhitespace(chars.charAt(currentPos))) {
      currentPos++;
    }
    if (currentPos + BRACE_LENGTH <= length && isBrace(chars.subSequence(currentPos, currentPos + BRACE_LENGTH))) {
      return currentPos;
    }

    return -1;
  }
}
