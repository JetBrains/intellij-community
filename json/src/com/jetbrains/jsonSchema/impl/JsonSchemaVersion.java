// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.JsonBundle;
import com.intellij.openapi.util.text.StringUtil;
import kotlin.NotImplementedError;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum JsonSchemaVersion {
  SCHEMA_4,
  SCHEMA_6,
  SCHEMA_7;

  private static final String ourSchemaV4Schema = "http://json-schema.org/draft-04/schema";
  private static final String ourSchemaV6Schema = "http://json-schema.org/draft-06/schema";
  private static final String ourSchemaV7Schema = "http://json-schema.org/draft-07/schema";
  private static final String ourSchemaOrgPrefix = "http://json-schema.org/";

  @Override
  public String toString() {
    return JsonBundle.message("schema.of.version", switch (this) {
      case SCHEMA_4 -> 4;
      case SCHEMA_6 -> 6;
      case SCHEMA_7 -> 7;
    });
  }


  @Nullable
  public static JsonSchemaVersion byId(@NotNull String id) {
    if (id.startsWith("https://")) {
      id = "http://" + id.substring("https://".length());
    }
    return switch (StringUtil.trimEnd(id, '#')) {
      case ourSchemaV4Schema -> SCHEMA_4;
      case ourSchemaV6Schema -> SCHEMA_6;
      case ourSchemaV7Schema -> SCHEMA_7;
      default -> id.startsWith(ourSchemaOrgPrefix) ? SCHEMA_7 : null;
    };
  }

  public static boolean isSchemaSchemaId(@Nullable String id) {
    return id != null && byId(id) != null;
  }
}
