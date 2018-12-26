// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

class PropertyAccessorFactory {

  private final Field myField;

  PropertyAccessorFactory(Field field) {
    myField = field;
  }

  private enum ValueType {
    BOOLEAN,
    INT,
    STRING,
    WRAP,
    BRACE_STYLE,
    FORCE_BRACES,
    ENUM,
    OTHER
  }

  private ValueType getValueType() {
    final String fieldName = myField.getName();
    Class<?> fieldType = myField.getType();
    String name = fieldType.getName();
    if (fieldType.isPrimitive()) {
      if ("int".equals(name)) {
        if (fieldName.endsWith("_WRAP")) {
          return ValueType.WRAP;
        }
        else if (fieldName.endsWith("BRACE_STYLE")) {
          return ValueType.BRACE_STYLE;
        }
        else if (fieldName.endsWith("_BRACE_FORCE")) {
          return ValueType.FORCE_BRACES;
        }
        return ValueType.INT;
      }
      else if ("boolean".equals(name)) {
        return ValueType.BOOLEAN;
      }
    }
    else if ("java.lang.String".equals(name)) {
      return ValueType.STRING;
    }
    else if (fieldType.isEnum()) {
      return ValueType.ENUM;
    }
    return ValueType.OTHER;
  }

  @Nullable
  CodeStylePropertyAccessor createAccessor(@NotNull Object codeStyleObject) {
    if (mayHaveAccessor()) {
      switch (getValueType()) {
        case BOOLEAN:
          if ("USE_TAB_CHARACTER".equals(myField.getName())) {
            return new TabCharPropertyAccessor(codeStyleObject, myField);
          }
          return new BooleanAccessor(codeStyleObject, myField);
        case INT:
          return new IntegerAccessor(codeStyleObject, myField);
        case STRING:
          return new StringAccessor(codeStyleObject, myField);
        case WRAP:
          return new WrappingAccessor(codeStyleObject, myField);
        case BRACE_STYLE:
          return new BraceStyleAccessor(codeStyleObject, myField);
        case FORCE_BRACES:
          return new ForceBracesAccessor(codeStyleObject, myField);
        case ENUM:
          return new EnumPropertyAccessor(codeStyleObject, myField);
        case OTHER:
          break;
      }
    }
    return null;
  }

  private boolean mayHaveAccessor() {
    return myField.getType().getCanonicalName() != null &&
           myField.getAnnotation(Deprecated.class) == null;
  }

  private static class TabCharPropertyAccessor extends BooleanAccessor {

    TabCharPropertyAccessor(@NotNull Object object, @NotNull Field field) {
      super(object, field);
    }

    @Nullable
    @Override
    protected Boolean parseString(@NotNull String str) {
      return "tab".equalsIgnoreCase(str);
    }

    @NotNull
    @Override
    protected String asString(@NotNull Boolean value) {
      return value ? "tab" : "space";
    }

    @Override
    public String getPropertyName() {
      return "indent_style";
    }
  }
}
