// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;

public enum JsonSchemaType {
  _string, _number, _integer, _object, _array, _boolean, _null, _any, _string_number;

  public String getName() {
    return name().substring(1);
  }

  public String getDefaultValue() {
    return switch (this) {
      case _string -> "\"\"";
      case _number, _integer, _string_number -> "0";
      case _object -> "{}";
      case _array -> "[]";
      case _boolean -> "false";
      case _null -> "null";
      case _any -> "";
    };
  }

  public boolean isSimple() {
    return switch (this) {
      case _string, _number, _integer, _boolean, _null -> true;
      case _object, _array, _any, _string_number -> false;
    };
  }

  static @Nullable JsonSchemaType getType(final @NotNull JsonValueAdapter value) {
    if (value.isNull()) return _null;
    if (value.isBooleanLiteral()) return _boolean;
    if (value.isStringLiteral()) {
      return value.isNumberLiteral() ? _string_number : _string;
    }
    if (value.isArray()) return _array;
    if (value.isObject()) return _object;
    if (value.isNumberLiteral()) {
      return isInteger(value.getDelegate().getText()) ? _integer : _number;
    }
    return null;
  }

  public static boolean isInteger(@NotNull String text) {
    return getIntegerValue(text) != null;
  }

  public static @Nullable Number getIntegerValue(@NotNull String text) {
    try {
      return Integer.parseInt(text);
    }
    catch (NumberFormatException e) {
      try {
        return BigInteger.valueOf(Long.parseLong(text));
      }
      catch (NumberFormatException e2) {
        return null;
      }
    }
  }

  public String getDescription() {
    if (this == _any) return "*";
    return getName();
  }
}
