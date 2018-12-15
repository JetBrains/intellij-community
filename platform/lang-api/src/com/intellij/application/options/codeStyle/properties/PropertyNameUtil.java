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
    addMapping("RIGHT_MARGIN", "max_line_length");
    addMapping("TAB_SIZE", "tab_width");
    addMapping("WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN", "wrap_on_typing");
  }

  private PropertyNameUtil() {
  }

  private static void addMapping(@NotNull String fieldName, @NotNull String propertyName) {
    FIELD_TO_PROPERTY_NAME_MAP.put(fieldName, propertyName);
  }

  static String getPropertyName(@NotNull String fieldName) {
    if (FIELD_TO_PROPERTY_NAME_MAP.containsKey(fieldName)) return FIELD_TO_PROPERTY_NAME_MAP.get(fieldName);
    StringBuilder nameBuilder = new StringBuilder();
    String[] chunks = preprocess(fieldName).split("_");
    for (String chunk : chunks) {
      if (nameBuilder.length() > 0) nameBuilder.append('_');
      appendNamePart(nameBuilder, chunk);
    }
    return nameBuilder.toString();
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
