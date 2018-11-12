// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

class PropertyNameUtil {
  /**
   * Rules for property names which differ from field names for better readability.
   */
  private final static Map<String, String> FIELD_TO_PROPERTY_NAME_MAP = ContainerUtilRt.newHashMap();

  static {
    addMapping("SPACE_AFTER_SEMICOLON", "space_after_for_semicolon");
    addMapping("SPACE_BEFORE_SEMICOLON", "space_before_for_semicolon");
    addMapping("LPAREN", "left_paren");
    addMapping("RPAREN", "right_paren");
    addMapping("JD", "javadoc");
    addMapping("RBRACE", "right_brace");
    addMapping("LBRACE", "left_brace");
    addMapping("INSTANCEOF", "instance_of");
    addMapping("DOWHILE", "do_while");
    addMapping("PARM", "param");
  }

  private PropertyNameUtil() {
  }

  private static void addMapping(@NotNull String fieldName, @NotNull String propertyName) {
    FIELD_TO_PROPERTY_NAME_MAP.put(fieldName, propertyName);
  }

  static String getPropertyName(@NotNull String fieldName) {
    if (FIELD_TO_PROPERTY_NAME_MAP.containsKey(fieldName)) return FIELD_TO_PROPERTY_NAME_MAP.get(fieldName);
    StringBuilder nameBuilder = new StringBuilder();
    String[] chunks = fieldName.split("_");
    for (String chunk : chunks) {
      if (nameBuilder.length() > 0) nameBuilder.append('_');
      appendNamePart(nameBuilder, chunk);
    }
    return nameBuilder.toString();
  }

  private static void appendNamePart(@NotNull StringBuilder nameBuilder, @NotNull String chunk) {
    if (chunk.length() > 0) {
      if (FIELD_TO_PROPERTY_NAME_MAP.containsKey(chunk)) {
        nameBuilder.append(FIELD_TO_PROPERTY_NAME_MAP.get(chunk));
      }
      else {
        //noinspection StringToUpperCaseOrToLowerCaseWithoutLocale
        nameBuilder.append(chunk.toLowerCase());
      }
    }
  }
}
