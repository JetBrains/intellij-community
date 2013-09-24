/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

  /**
   * Set value or unset if equals to default value
   */
  public abstract void setValue(@NotNull String name, @NotNull String value, @NotNull String defaultValue);

  public abstract String[] getValues(@NonNls String name);

  public abstract void setValues(@NonNls String name, String[] values);

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

  public final int getOrInitInt(@NonNls String name, int defaultValue) {
    try {
      return Integer.parseInt(getValue(name));
    } catch (NumberFormatException e) {
      return defaultValue;
    }
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

  public final boolean saveFields(@NotNull Object object) {
    try {
      for (Field field : object.getClass().getDeclaredFields()) {
        field.setAccessible(true);
        PropertyName annotation = field.getAnnotation(PropertyName.class);
        if (annotation != null) {
          final String name = annotation.value();
          setValue(name, String.valueOf(field.get(object)));
        }
      }
      return true;
    }
    catch (IllegalAccessException e) {
      return false;
    }
  }

  public final boolean loadFields(@NotNull Object object) {
    try {
      for (Field field : object.getClass().getDeclaredFields()) {
        field.setAccessible(true);
        final PropertyName annotation = field.getAnnotation(PropertyName.class);
        if (annotation != null) {
          final Class<?> type = field.getType();

          String defaultValue = annotation.defaultValue();
          if (PropertyName.NOT_SET.equals(defaultValue)) {
            if (type.equals(boolean.class))     {defaultValue = String.valueOf(field.getBoolean(object));}
            else if (type.equals(long.class))   {defaultValue = String.valueOf(field.getLong(object));}
            else if (type.equals(int.class))    {defaultValue = String.valueOf(field.getInt(object));}
            else if (type.equals(short.class))  {defaultValue = String.valueOf(field.getShort(object));}
            else if (type.equals(byte.class))   {defaultValue = String.valueOf(field.getByte(object));}
            else if (type.equals(double.class)) {defaultValue = String.valueOf(field.getDouble(object));}
            else if (type.equals(float.class))  {defaultValue = String.valueOf(field.getFloat(object));}
            else if (type.equals(String.class)) {defaultValue = String.valueOf(field.get(object));}

          }
          final String stringValue = getValue(annotation.value(), defaultValue);
          Object value = null;

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
      return true;
    }
    catch (IllegalAccessException e) {
      return false;
    }
  }

  public float getFloat(String name, float defaultValue) {
    if (isValueSet(name)) {
      try {
        return Float.parseFloat(getValue(name));
      }
      catch (NumberFormatException ignore) {}
    }
    return defaultValue;
  }
}
