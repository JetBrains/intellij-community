// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.codeinsight;

import com.intellij.json.JsonBundle;
import com.intellij.json.JsonDialectUtil;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

public class StandardJsonLiteralChecker implements JsonLiteralChecker {
  public static final Pattern VALID_ESCAPE = Pattern.compile("\\\\([\"\\\\/bfnrt]|u[0-9a-fA-F]{4})");
  private static final Pattern VALID_NUMBER_LITERAL = Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?");
  public static final String INF = "Infinity";
  public static final String MINUS_INF = "-Infinity";
  public static final String NAN = "NaN";

  @Override
  public @Nullable String getErrorForNumericLiteral(String literalText) {
    if (!INF.equals(literalText) &&
        !MINUS_INF.equals(literalText) &&
        !NAN.equals(literalText) &&
        !VALID_NUMBER_LITERAL.matcher(literalText).matches()) {
      return JsonBundle.message("syntax.error.illegal.floating.point.literal");
    }
    return null;
  }

  @Override
  public @Nullable Pair<TextRange, String> getErrorForStringFragment(Pair<TextRange, String> fragment, JsonStringLiteral stringLiteral) {
    if (fragment.getSecond().chars().anyMatch(c -> c <= '\u001F')) { // fragments are cached, string values - aren't; go inside only if we encountered a potentially 'wrong' char
      final String text = stringLiteral.getText();
      if (new TextRange(0, text.length()).contains(fragment.first)) {
        final int startOffset = fragment.first.getStartOffset();
        final String part = text.substring(startOffset, fragment.first.getEndOffset());
        char[] array = part.toCharArray();
        for (int i = 0; i < array.length; i++) {
          char c = array[i];
          if (c <= '\u001F') {
            return Pair.create(new TextRange(startOffset + i, startOffset + i + 1),
                               JsonBundle
                                 .message("syntax.error.control.char.in.string", "\\u" + Integer.toHexString(c | 0x10000).substring(1)));
          }
        }
      }
    }
    final String error = getStringError(fragment.second);
    return error == null ? null : Pair.create(fragment.first, error);
  }

  public static @Nullable String getStringError(String fragmentText) {
    if (fragmentText.startsWith("\\") && fragmentText.length() > 1 && !VALID_ESCAPE.matcher(fragmentText).matches()) {
      if (fragmentText.startsWith("\\u")) {
        return JsonBundle.message("syntax.error.illegal.unicode.escape.sequence");
      }
      else {
        return JsonBundle.message("syntax.error.illegal.escape.sequence");
      }
    }
    return null;
  }

  @Override
  public boolean isApplicable(PsiElement element) {
    return JsonDialectUtil.isStandardJson(element);
  }
}
