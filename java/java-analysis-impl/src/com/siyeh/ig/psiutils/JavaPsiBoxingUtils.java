// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.psiutils;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Map;

public final class JavaPsiBoxingUtils {

  private static final @NonNls @Unmodifiable Map<String, String> parseMethodsMap;

  static {
    parseMethodsMap = Map.ofEntries(Map.entry(CommonClassNames.JAVA_LANG_INTEGER, "parseInt"),
    Map.entry(CommonClassNames.JAVA_LANG_LONG, "parseLong"),
    Map.entry(CommonClassNames.JAVA_LANG_FLOAT, "parseFloat"),
    Map.entry(CommonClassNames.JAVA_LANG_BOOLEAN, "parseBoolean"),
    Map.entry(CommonClassNames.JAVA_LANG_DOUBLE, "parseDouble"),
    Map.entry(CommonClassNames.JAVA_LANG_SHORT, "parseShort"),
    Map.entry(CommonClassNames.JAVA_LANG_BYTE, "parseByte"));
  }

  /**
   * Get parse method name without qualifier for the specified boxed type,
   * or {@code null} if the parameter is not a boxed type.
   */
  public static @Nullable String getParseMethod(@Nullable PsiType type) {
    if (type == null) return null;
    final String typeText = type.getCanonicalText();
    return parseMethodsMap.get(typeText);
  }
}
