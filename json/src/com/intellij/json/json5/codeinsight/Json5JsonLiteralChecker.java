// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.json5.codeinsight;

import com.intellij.json.JsonDialectUtil;
import com.intellij.json.codeinsight.JsonLiteralChecker;
import com.intellij.json.codeinsight.StandardJsonLiteralChecker;
import com.intellij.json.json5.Json5Language;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

public class Json5JsonLiteralChecker implements JsonLiteralChecker {
  private static final Pattern VALID_HEX_ESCAPE = Pattern.compile("\\\\(x[0-9a-fA-F]{2})");
  private static final Pattern INVALID_NUMERIC_ESCAPE = Pattern.compile("\\\\[1-9]");
  @Nullable
  @Override
  public String getErrorForNumericLiteral(String literalText) {
    return null;
  }

  @Nullable
  @Override
  public String getErrorForStringFragment(String fragmentText) {
    if (fragmentText.startsWith("\\") && fragmentText.length() > 1 && fragmentText.endsWith("\n")) {
      if (StringUtil.isEmptyOrSpaces(fragmentText.substring(1, fragmentText.length() - 1))) {
        return null;
      }
    }

    if (fragmentText.startsWith("\\x") && VALID_HEX_ESCAPE.matcher(fragmentText).matches()) {
      return null;
    }

    if (!StandardJsonLiteralChecker.VALID_ESCAPE.matcher(fragmentText).matches() && !INVALID_NUMERIC_ESCAPE.matcher(fragmentText).matches()) {
      return null;
    }

    return StandardJsonLiteralChecker.getStringError(fragmentText);
  }

  @Override
  public boolean isApplicable(PsiElement element) {
    return JsonDialectUtil.getLanguage(element) == Json5Language.INSTANCE;
  }
}
