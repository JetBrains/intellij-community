// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.psiutils;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class JavaPsiBoxingUtils {

  private static final @NonNls Map<String, String> parseMethodsMap = new HashMap<>();

  static {
    parseMethodsMap.put(CommonClassNames.JAVA_LANG_INTEGER, "parseInt");
    parseMethodsMap.put(CommonClassNames.JAVA_LANG_LONG, "parseLong");
    parseMethodsMap.put(CommonClassNames.JAVA_LANG_FLOAT, "parseFloat");
    parseMethodsMap.put(CommonClassNames.JAVA_LANG_BOOLEAN, "parseBoolean");
    parseMethodsMap.put(CommonClassNames.JAVA_LANG_DOUBLE, "parseDouble");
    parseMethodsMap.put(CommonClassNames.JAVA_LANG_SHORT, "parseShort");
    parseMethodsMap.put(CommonClassNames.JAVA_LANG_BYTE, "parseByte");
  }

  /**
   * Get parse method name without qualifier for the specified boxed type,
   * or {@code null} if the parameter is not a boxed type.
   */
  @Nullable
  public static String getParseMethod(@Nullable PsiType type) {
    if (type == null) return null;
    final String typeText = type.getCanonicalText();
    return parseMethodsMap.get(typeText);
  }
}
