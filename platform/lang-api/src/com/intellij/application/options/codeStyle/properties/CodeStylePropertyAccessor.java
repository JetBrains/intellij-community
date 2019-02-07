// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.configurationStore.Property;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

@ApiStatus.Experimental
public abstract class CodeStylePropertyAccessor<T> {
  private final Object myObject;
  private final Field myField;

  public CodeStylePropertyAccessor(@NotNull Object object, @NotNull Field field) {
    myObject = object;
    myField = field;
  }

  public boolean set(@NotNull String str) {
    try {
      T value = parseString(str);
      if (value != null) {
        myField.set(myObject, value);
        return true;
      }
    }
    catch (IllegalAccessException e) {
      // Ignore and skip
    }
    return false;
  }

  @Nullable
  public String get() {
    try {
      //noinspection unchecked
      T value = (T)myField.get(myObject);
      return value != null && !isEmpty(value) ? asString(value) : null;
    }
    catch (IllegalAccessException e) {
      // Ignore and return null
    }
    return null;
  }

  @NotNull
  public Class getObjectClass() {
    return myObject.getClass();
  }

  protected boolean isEmpty(@NotNull T value) {
    return false;
  }

  @Nullable
  protected abstract T parseString(@NotNull String str);

  @NotNull
  protected abstract String asString(@NotNull T value);

  public String getPropertyName() {
    Property descriptor = myField.getAnnotation(Property.class);
    if (descriptor != null) {
      String externalName = descriptor.externalName();
      if (!StringUtil.isEmpty(externalName)) return externalName;
    }
    return PropertyNameUtil.getPropertyName(myField.getName());
  }

  public boolean isGenericProperty() {
    return myObject instanceof CommonCodeStyleSettings || myObject instanceof CommonCodeStyleSettings.IndentOptions;
  }
}
