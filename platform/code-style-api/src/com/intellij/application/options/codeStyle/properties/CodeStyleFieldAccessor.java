// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.configurationStore.Property;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

@ApiStatus.Experimental
public abstract class CodeStyleFieldAccessor<T,V> extends CodeStylePropertyAccessor<V> {
  private final Object myObject;
  private final Field myField;

  public CodeStyleFieldAccessor(@NotNull Object object, @NotNull Field field) {
    myObject = object;
    myField = field;
  }

  @Override
  public boolean set(@NotNull V extVal) {
    try {
      T value = fromExternal(extVal);
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

  @Override
  @Nullable
  public V get() {
    try {
      //noinspection unchecked
      T value = (T)myField.get(myObject);
      return value != null && !isEmpty(value) ? toExternal(value) : null;
    }
    catch (IllegalAccessException e) {
      // Ignore and return null
    }
    return null;
  }

  public Object getDataObject() {
    return myObject;
  }

  protected boolean isEmpty(@NotNull T value) {
    return false;
  }

  @Nullable
  protected abstract T fromExternal(@NotNull V extVal);

  @NotNull
  protected abstract V toExternal(@NotNull T value);

  @Override
  public String getPropertyName() {
    Property descriptor = myField.getAnnotation(Property.class);
    if (descriptor != null) {
      String externalName = descriptor.externalName();
      if (!StringUtil.isEmpty(externalName)) return externalName;
    }
    return PropertyNameUtil.getPropertyName(myField.getName());
  }

}
