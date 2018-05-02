// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.TIntHashSet;

public class PsiRawStringLiteralUtil {
  /**
   * Check if <code>text</code> contains <code>tics</code>, and 
   * if yes, returns the number of additional tics required around text to have a valid raw string literal
   * empty string otherwise
   */
  public static String getAdditionalTics(String text, String tics) {
    int quotesLength = tics.length();
    int textLength = text.length();
    int idx = 0;
    int maxQuotesNumber = -1;
    boolean hasToReplace = false;
    while ((idx = text.indexOf(tics, idx)) >= 0 && idx < textLength) {
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
  public static int getLeadingTicsSequence(CharSequence text) {
    return getTicsSequence(text, text.length(), 0);
  }

  /**
   * Return number of trailing tics in the <code>text</code>
   */
  public static int getTrailingTicsSequence(CharSequence text) {
    int length = text.length();
    while (length > 0 && text.charAt(length - 1) == '`') length--;
    return text.length() - length;
  }

  private static int getTicsSequence(CharSequence literalText, int length, int startIndex) {
    int quotesLength = startIndex;
    while (quotesLength < length && literalText.charAt(quotesLength) == '`') quotesLength++;
    return quotesLength - startIndex;
  }

  /**
   * For given raw string literal text (with backticks) returns minimal number of backticks required for string content
   * @return number less than current number of backticks,
   *         -1 otherwise
   */
  public static int getReducedNumberOfBackticks(String text) {
    int leadingTicsSequence = getLeadingTicsSequence(text);
    int trailingTicsSequence = getTrailingTicsSequence(text);
    if (leadingTicsSequence == trailingTicsSequence && leadingTicsSequence > 1) {
      int length = text.length() - trailingTicsSequence;
      int idx = leadingTicsSequence;
      TIntHashSet usedTicSequences = new TIntHashSet();
      usedTicSequences.add(leadingTicsSequence);
      while (idx < length) {
        idx = text.indexOf("`", idx);
        if (idx < 0) break;
        int ticsSequence = getTicsSequence(text, length, idx);
        usedTicSequences.add(ticsSequence);
        idx += ticsSequence;
      }

      for (int i = 1; i < leadingTicsSequence; i++) {
        if (!usedTicSequences.contains(i)) {
          return i;
        }
      }
    }
    return -1;
  }
  
}
