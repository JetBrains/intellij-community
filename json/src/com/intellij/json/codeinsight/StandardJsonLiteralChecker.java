// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.codeinsight;

import com.intellij.json.JsonBundle;
import com.intellij.json.JsonDialectUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

public class StandardJsonLiteralChecker implements JsonLiteralChecker {
  public static final Pattern VALID_ESCAPE = Pattern.compile("\\\\([\"\\\\/bfnrt]|u[0-9a-fA-F]{4})");
  private static final Pattern VALID_NUMBER_LITERAL = Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?");
  public static final String INF = "Infinity";
  public static final String MINUS_INF = "-Infinity";
  public static final String NAN = "NaN";

  @Nullable
  @Override
  public String getErrorForNumericLiteral(String literalText) {
    if (!INF.equals(literalText) &&
        !MINUS_INF.equals(literalText) &&
        !NAN.equals(literalText) &&
        !VALID_NUMBER_LITERAL.matcher(literalText).matches()) {
      return JsonBundle.message("syntax.error.illegal.floating.point.literal");
    }
    return null;
  }

  @Nullable
  @Override
  public String getErrorForStringFragment(String fragmentText) {
    return getStringError(fragmentText);
  }

  @Nullable
  public static String getStringError(String fragmentText) {
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
