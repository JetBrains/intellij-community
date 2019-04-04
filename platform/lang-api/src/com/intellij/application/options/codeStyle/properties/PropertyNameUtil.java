// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

class PropertyNameUtil {

  private PropertyNameUtil() {
  }

  static String getPropertyName(@NotNull String fieldName) {
    return preprocess(fieldName).toLowerCase(Locale.ENGLISH);
  }

  @NotNull
  private static String preprocess(@NotNull String fieldName) {
    if (fieldName.startsWith("SPACE_WITHIN")) {
      return fieldName.replace("SPACE_WITHIN", "SPACES_WITHIN");
    }
    else if (fieldName.startsWith("SPACE_AROUND")) {
      return fieldName.replace("SPACE_AROUND", "SPACES_AROUND");
    }
    return fieldName;
  }
}
