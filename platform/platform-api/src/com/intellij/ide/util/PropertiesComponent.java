/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.util;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;

/**
 * @author max
 * @author Konstantin Bulenkov
 */
public abstract class PropertiesComponent {
  public abstract void unsetValue(String name);

  public abstract boolean isValueSet(String name);

  public abstract String getValue(@NonNls String name);

  public abstract void setValue(@NonNls String name, String value);

  public static PropertiesComponent getInstance(Project project) {
    return ServiceManager.getService(project, PropertiesComponent.class);
  }

  public static PropertiesComponent getInstance() {
    return ServiceManager.getService(PropertiesComponent.class);
  }

  public final boolean isTrueValue(@NonNls String name) {
    return Boolean.valueOf(getValue(name)).booleanValue();
  }

  public final boolean getBoolean(@NonNls String name, boolean defaultValue) {
    return isValueSet(name) ? isTrueValue(name) : defaultValue;
  }

  @NotNull
  public String getValue(@NonNls String name, @NotNull String defaultValue) {
    return isValueSet(name) ? getValue(name) : defaultValue;
  }

  public final long getOrInitLong(@NonNls String name, long defaultValue) {
    try {
      return Long.parseLong(getValue(name));
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  public String getOrInit(@NonNls String name, String defaultValue) {
    if (!isValueSet(name)) {
      setValue(name, defaultValue);
      return defaultValue;
    }
    return getValue(name);
  }

  public final void saveFields(@NotNull Object object) throws IllegalAccessException {
    for (Field field : object.getClass().getDeclaredFields()) {
      field.setAccessible(true);
      if (field.isAnnotationPresent(PropertyName.class)) {
        final String name = field.getAnnotation(PropertyName.class).value();
        setValue(name, String.valueOf(field.get(object)));
      }
    }
  }

  public final void loadFields(@NotNull Object object) throws IllegalAccessException {
    for (Field field : object.getClass().getDeclaredFields()) {
      field.setAccessible(true);
      if (field.isAnnotationPresent(PropertyName.class)) {
        final PropertyName annotation = field.getAnnotation(PropertyName.class);
        final String stringValue = getValue(annotation.value(), annotation.defaultValue());
        Object value = null;
        final Class<?> type = field.getType();

        if (type.equals(boolean.class))     {value = Boolean.valueOf(stringValue);}
        else if (type.equals(long.class))   {value = Long.parseLong(stringValue);}
        else if (type.equals(int.class))    {value = Integer.parseInt(stringValue);}
        else if (type.equals(short.class))  {value = Short.parseShort(stringValue);}
        else if (type.equals(byte.class))   {value = Byte.parseByte(stringValue);}
        else if (type.equals(double.class)) {value = Double.parseDouble(stringValue);}
        else if (type.equals(float.class))  {value = Float.parseFloat(stringValue);}
        else if (type.equals(String.class)) {value = stringValue;}

        if (value != null) {
          field.set(object, value);
        }
      }
    }
  }
}
