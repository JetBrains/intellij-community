package com.jetbrains.jsonSchema.impl;

import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Irina.Chernushina on 7/15/2015.
 */
public enum JsonSchemaType {
  _string, _number, _integer, _object, _array, _boolean, _null, _any;

  public String getName() {
    return name().substring(1);
  }

  @Nullable
  static JsonSchemaType getType(@NotNull final JsonValueAdapter value) {
    if (value.isNull()) return _null;
    if (value.isBooleanLiteral()) return _boolean;
    if (value.isStringLiteral()) return _string;
    if (value.isArray()) return _array;
    if (value.isObject()) return _object;
    if (value.isNumberLiteral()) {
      return isInteger(value.getDelegate().getText()) ? _integer : _number;
    }
    return null;
  }

  private static boolean isInteger(@NotNull String text) {
    try {
      //noinspection ResultOfMethodCallIgnored
      Integer.parseInt(text);
      return true;
    }
    catch (NumberFormatException e) {
      return false;
    }
  }
}
