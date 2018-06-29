package com.jetbrains.jsonSchema.impl;

import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Irina.Chernushina on 7/15/2015.
 */
public enum JsonSchemaType {
  _string, _number, _integer, _object, _array, _boolean, _null, _any, _string_number;

  public String getName() {
    return name().substring(1);
  }

  public String getDefaultValue() {
    switch (this) {
      case _string:
        return "\"\"";
      case _number:
      case _integer:
      case _string_number:
        return "0";
      case _object:
        return "{}";
      case _array:
        return "[]";
      case _boolean:
        return "false";
      case _null:
        return "null";
      case _any:
      default:
        return "";
    }
  }

  public boolean isSimple() {
    switch (this) {
      case _string:
      case _number:
      case _integer:
      case _boolean:
      case _null:
        return true;
      case _object:
      case _array:
      case _any:
      default:
        return false;
    }
  }

  @Nullable
  static JsonSchemaType getType(@NotNull final JsonValueAdapter value) {
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

  public String getDescription() {
    if (this == _any) return "*";
    return getName();
  }
}
