// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.psi.codeStyle.CommonCodeStyleSettings.BraceStyleConstant;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.WrapConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

class FieldAccessorFactory {

  private final Field myField;

  FieldAccessorFactory(Field field) {
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
        WrapConstant wrapAnnotation = myField.getAnnotation(WrapConstant.class);
        if (wrapAnnotation != null) {
          return ValueType.WRAP;
        }
        BraceStyleConstant braceAnnotation = myField.getAnnotation(BraceStyleConstant.class);
        if (braceAnnotation != null) {
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
  CodeStyleFieldAccessor<?,?> createAccessor(@NotNull Object codeStyleObject) {
    if (mayHaveAccessor()) {
      return switch (getValueType()) {
        case BOOLEAN -> {
          if ("USE_TAB_CHARACTER".equals(myField.getName())) {
            yield new TabCharPropertyAccessor(codeStyleObject, myField);
          }
          yield new BooleanAccessor(codeStyleObject, myField);
        }
        case INT -> {
          if ("WRAP_ON_TYPING".equals(myField.getName())) {
            yield new WrapOnTypingAccessor(codeStyleObject, myField);
          }
          yield new IntegerAccessor(codeStyleObject, myField);
        }
        case STRING -> {
          CommaSeparatedValues annotation = myField.getAnnotation(CommaSeparatedValues.class);
          if (annotation != null) {
            yield new CommaSeparatedValuesAccessor(codeStyleObject, myField);
          }
          yield new StringAccessor(codeStyleObject, myField);
        }
        case WRAP -> new WrappingAccessor(codeStyleObject, myField);
        case BRACE_STYLE -> new BraceStyleAccessor(codeStyleObject, myField);
        case FORCE_BRACES -> new ForceBracesAccessor(codeStyleObject, myField);
        case ENUM -> new EnumPropertyAccessor(codeStyleObject, myField);
        case OTHER -> null;
      };
    }
    return null;
  }

  private boolean mayHaveAccessor() {
    final int modifiers = myField.getModifiers();
    return !Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers) &&
           myField.getType().getCanonicalName() != null &&
           myField.getAnnotation(Deprecated.class) == null;
  }

  private static class TabCharPropertyAccessor extends CodeStyleFieldAccessor<Boolean,String> {

    TabCharPropertyAccessor(@NotNull Object object, @NotNull Field field) {
      super(object, field);
    }

    @Override
    protected @Nullable String parseString(@NotNull String string) {
      return string;
    }

    @Override
    protected @Nullable String valueToString(@NotNull String value) {
      return value;
    }

    @Override
    protected @Nullable Boolean fromExternal(@NotNull String str) {
      return "tab".equalsIgnoreCase(str);
    }

    @Override
    protected @NotNull String toExternal(@NotNull Boolean value) {
      return value ? "tab" : "space";
    }

    @Override
    public String getPropertyName() {
      return "indent_style";
    }
  }
}
