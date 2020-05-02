// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.util.text.StringUtilRt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

/**
 * Roaming is disabled for PropertiesComponent, so, use it only and only for temporary non-roamable properties.
 *
 * See http://www.jetbrains.org/intellij/sdk/docs/basics/persisting_state_of_components.html "Using PropertiesComponent for Simple non-roamable Persistence"
 *
 * @author max
 * @author Konstantin Bulenkov
 */
public abstract class PropertiesComponent extends SimpleModificationTracker {
  public abstract void unsetValue(@NonNls @NotNull String name);

  public abstract boolean isValueSet(@NonNls @NotNull String name);

  @Nullable
  public abstract String getValue(@NonNls @NotNull String name);

  /**
   * Consider to use {@link #setValue(String, String, String)} to avoid write defaults.
   */
  public abstract void setValue(@NonNls @NotNull String name, @Nullable String value);

  /**
   * Set value or unset if equals to default value
   */
  public abstract void setValue(@NonNls @NotNull String name, @Nullable String value, @Nullable String defaultValue);

  /**
   * Set value or unset if equals to default value
   */
  public abstract void setValue(@NonNls @NotNull String name, float value, float defaultValue);

  /**
   * Set value or unset if equals to default value
   */
  public abstract void setValue(@NonNls @NotNull String name, int value, int defaultValue);

  /**
   * Set value or unset if equals to false
   */
  public final void setValue(@NonNls @NotNull String name, boolean value) {
    setValue(name, value, false);
  }

  /**
   * Set value or unset if equals to default
   */
  public abstract void setValue(@NonNls @NotNull String name, boolean value, boolean defaultValue);

  public abstract String @Nullable [] getValues(@NonNls @NotNull String name);

  public abstract void setValues(@NonNls @NotNull String name, String[] values);

  public static PropertiesComponent getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, PropertiesComponent.class);
  }

  public static PropertiesComponent getInstance() {
    return ServiceManager.getService(PropertiesComponent.class);
  }

  public final boolean isTrueValue(@NonNls String name) {
    return Boolean.parseBoolean(getValue(name));
  }

  public final boolean getBoolean(@NonNls @NotNull String name, boolean defaultValue) {
    return isValueSet(name) ? isTrueValue(name) : defaultValue;
  }

  public final boolean getBoolean(@NonNls @NotNull String name) {
    return getBoolean(name, false);
  }

  @NotNull
  public String getValue(@NonNls @NotNull String name, @NotNull String defaultValue) {
    String value = getValue(name);
    return value == null ? defaultValue : value;
  }

  /**
   * @deprecated Use {@link #getInt(String, int)}
   * Init was never performed and in any case is not recommended.
   */
  @Deprecated
  public final int getOrInitInt(@NonNls @NotNull String name, int defaultValue) {
    return getInt(name, defaultValue);
  }

  public int getInt(@NonNls @NotNull String name, int defaultValue) {
    return StringUtilRt.parseInt(getValue(name), defaultValue);
  }

  public long getLong(@NonNls @NotNull String name, long defaultValue) {
    return StringUtilRt.parseLong(getValue(name), defaultValue);
  }

  /**
   * @deprecated Use {@link #getLong(String, int)}
   * Init was never performed and in any case is not recommended.
   */
  @Deprecated
  public final long getOrInitLong(@NonNls @NotNull String name, long defaultValue) {
    return getLong(name, defaultValue);
  }

  /**
   * @deprecated Use {@link #getValue(String, String)}
   */
  @Deprecated
  public String getOrInit(@NonNls @NotNull String name, String defaultValue) {
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

  public float getFloat(@NonNls @NotNull String name, float defaultValue) {
    if (isValueSet(name)) {
      try {
        return Float.parseFloat(getValue(name));
      }
      catch (NumberFormatException ignore) {
      }
    }
    return defaultValue;
  }
}
