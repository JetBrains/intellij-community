// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.JsonBundle;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum JsonSchemaVersion {
  SCHEMA_4,
  SCHEMA_6,
  SCHEMA_7,
  SCHEMA_2019_09,
  SCHEMA_2020_12;

  private static final String ourSchemaV4Schema = "http://json-schema.org/draft-04/schema";
  private static final String ourSchemaV6Schema = "http://json-schema.org/draft-06/schema";
  private static final String ourSchemaV7Schema = "http://json-schema.org/draft-07/schema";
  private static final String ourSchemaV201909Schema = "http://json-schema.org/draft/2019-09/schema";
  private static final String ourSchemaV202012Schema = "http://json-schema.org/draft/2020-12/schema";
  private static final String ourSchemaOrgPrefix = "http://json-schema.org/";

  @Override
  public String toString() {
    return JsonBundle.message("schema.of.version", switch (this) {
      case SCHEMA_4 -> 4;
      case SCHEMA_6 -> 6;
      case SCHEMA_7 -> 7;
      case SCHEMA_2019_09 -> 201909;
      case SCHEMA_2020_12 -> 202012;
    });
  }


  public static @Nullable JsonSchemaVersion byId(@NotNull String id) {
    if (id.startsWith("https://")) {
      id = "http://" + id.substring("https://".length());
    }
    return switch (StringUtil.trimEnd(id, '#')) {
      case ourSchemaV4Schema -> SCHEMA_4;
      case ourSchemaV6Schema -> SCHEMA_6;
      case ourSchemaV7Schema -> SCHEMA_7;
      case ourSchemaV201909Schema -> SCHEMA_2019_09;
      case ourSchemaV202012Schema -> SCHEMA_2020_12;
      default -> id.startsWith(ourSchemaOrgPrefix) ? SCHEMA_7 : null;
    };
  }

  public static boolean isSchemaSchemaId(@Nullable String id) {
    return id != null && byId(id) != null;
  }
}
