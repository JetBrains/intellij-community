// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.java19api;

import com.intellij.lang.java.lexer.JavaLexer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public final class NameConverterUtil {
  private static final Pattern NON_NAME = Pattern.compile("[^A-Za-z0-9]");
  private static final Pattern DOT_SEQUENCE = Pattern.compile("\\.{2,}");

  private NameConverterUtil() {
  }

  @NotNull
  public static String convertModuleName(@NotNull String name) { // "public" is for testing
    // All non-alphanumeric characters ([^A-Za-z0-9]) are replaced with a dot (".") ...
    name = NON_NAME.matcher(name).replaceAll(".");
    // ... all repeating dots are replaced with one dot ...
    name = DOT_SEQUENCE.matcher(name).replaceAll(".");
    // ... and all leading and trailing dots are removed.
    name = StringUtil.trimLeading(StringUtil.trimTrailing(name, '.'), '.');

    // sanitize keywords and leading digits
    String[] parts = name.split("\\.");
    StringBuilder builder = new StringBuilder();
    boolean first = true;
    for (String part : parts) {
      if (part.isEmpty()) {
        continue;
      }
      if (Character.isJavaIdentifierStart(part.charAt(0))) {
        if (!first) {
          builder.append('.');
        }
        builder.append(part);
        if (JavaLexer.isKeyword(part, LanguageLevel.JDK_1_9)) {
          builder.append('x');
        }
      }
      else { // it's a leading digit
        if (first) {
          builder.append("module");
        }
        builder.append(part);
      }
      first = false;
    }
    return builder.toString();
  }
}
