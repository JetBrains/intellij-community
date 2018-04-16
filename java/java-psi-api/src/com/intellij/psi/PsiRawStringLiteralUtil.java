// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.util.text.StringUtil;

public class PsiRawStringLiteralUtil {
  /**
   * Check if <code>text</code> contains <code>tics</code>, and 
   * if yes, returns the number of additional tics required around text to have a valid raw string literal
   * empty string otherwise
   */
  public static String getAdditionalTics(String text, String tics) {
    int quotesLength = tics.length();
    int textLength = text.length();
    int idx = quotesLength;
    int maxQuotesNumber = -1;
    boolean hasToReplace = false;
    while ((idx = text.indexOf(tics, idx)) > 0 && idx < textLength) {
      int additionalQuotesLength = getTicsSequence(text, textLength, idx + quotesLength);
      if (additionalQuotesLength == 0) {
        hasToReplace = true;
      }
      maxQuotesNumber = Math.max(maxQuotesNumber, additionalQuotesLength);
      idx += additionalQuotesLength + quotesLength;
    }

    return hasToReplace ? StringUtil.repeat("`", maxQuotesNumber + 1) : "";
  }

  /**
   * Return number of leading tics in the <code>text</code>
   */
  public static int getLeadingTicsSequence(String text) {
    return getTicsSequence(text, text.length(), 0);
  }

  /**
   * Return number of trailing tics in the <code>text</code>
   */
  public static int getTrailingTicsSequence(String text) {
    int length = text.length();
    while (length > 0 && text.charAt(length - 1) == '`') length--;
    return text.length() - length;
  }

  private static int getTicsSequence(String literalText, int length, int startIndex) {
    int quotesLength = startIndex;
    while (quotesLength < length && literalText.charAt(quotesLength) == '`') quotesLength++;
    return quotesLength - startIndex;
  }
}
