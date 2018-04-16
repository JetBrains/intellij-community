// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import kotlin.NotImplementedError;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum JsonSchemaVersion {
  SCHEMA_4,
  SCHEMA_6;

  private static final String ourSchemaV4Schema = "http://json-schema.org/draft-04/schema#";
  private static final String ourSchemaV4SchemaTrim = "http://json-schema.org/draft-04/schema";
  private static final String ourSchemaV6Schema = "http://json-schema.org/draft-06/schema#";
  private static final String ourSchemaV6SchemaTrim = "http://json-schema.org/draft-06/schema";

  @Override
  public String toString() {
    switch (this) {
      case SCHEMA_4:
        return "JSON Schema Version 4";
      case SCHEMA_6:
        return "JSON Schema Version 6";
    }

    throw new NotImplementedError("Unknown version: " + this);
  }


  @Nullable
  public static JsonSchemaVersion byId(@NotNull String id) {
    switch (id) {
      case ourSchemaV4Schema:
      case ourSchemaV4SchemaTrim:
        return SCHEMA_4;
      case ourSchemaV6Schema:
      case ourSchemaV6SchemaTrim:
        return SCHEMA_6;
    }

    return null;
  }

  public static boolean isSchemaSchemaId(@Nullable String id) {
    return ourSchemaV4Schema.equals(id) || ourSchemaV6Schema.equals(id);
  }
}
