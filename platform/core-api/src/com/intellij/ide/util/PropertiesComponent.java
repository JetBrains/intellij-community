// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtilRt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;

/**
 * Allows simple persistence of application/project-level values.
 * <p/>
 * Roaming is disabled for PropertiesComponent, so, use it only and only for temporary non-roamable properties.
 * <p/>
 * See <a href="http://www.jetbrains.org/intellij/sdk/docs/basics/persisting_state_of_components.html">Using PropertiesComponent for Simple non-roamable Persistence</a>.
 *
 * @author Konstantin Bulenkov
 */
public abstract class PropertiesComponent {
  public abstract void unsetValue(@NonNls @NotNull String name);

  public abstract boolean isValueSet(@NonNls @NotNull String name);

  public abstract @Nullable @NonNls String getValue(@NonNls @NotNull String name);

  /**
   * Consider to use {@link #setValue(String, String, String)} to avoid write defaults.
   */
  public abstract void setValue(@NonNls @NotNull String name, @NonNls @Nullable String value);

  /**
   * Set value or unset if equals to default value
   */
  public abstract void setValue(@NonNls @NotNull String name, @NonNls @Nullable String value, @Nullable String defaultValue);

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

  /**
   * @deprecated Use {@link #getList(String)}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.3")
  @Deprecated
  public abstract String @Nullable [] getValues(@NonNls @NotNull String name);

  /**
   * @deprecated Use {@link #getList(String)}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.3")
  @Deprecated
  public abstract void setValues(@NonNls @NotNull String name, String[] values);

  public abstract @Nullable List<String> getList(@NonNls @NotNull String name);

  /**
   * The passed collection will be copied.
   */
  public abstract void setList(@NonNls @NotNull String name, @Nullable Collection<String> values);

  /**
   * Returns the project-level instance.
   */
  public static PropertiesComponent getInstance(@NotNull Project project) {
    return project.getService(PropertiesComponent.class);
  }

  /**
   * Returns the application-level instance.
   */
  public static PropertiesComponent getInstance() {
    return ApplicationManager.getApplication().getService(PropertiesComponent.class);
  }

  public final boolean isTrueValue(@NonNls String name) {
    return Boolean.parseBoolean(getValue(name));
  }

  public final boolean getBoolean(@NonNls @NotNull String name, boolean defaultValue) {
    String value = getValue(name);
    return value == null ? defaultValue : Boolean.parseBoolean(value);
  }

  public final boolean getBoolean(@NonNls @NotNull String name) {
    return getBoolean(name, false);
  }

  public @NotNull @NlsSafe String getValue(@NonNls @NotNull String name, @NotNull String defaultValue) {
    String value = getValue(name);
    return value == null ? defaultValue : value;
  }

  /**
   * @deprecated Use {@link #getInt(String, int)}
   * Init was never performed and in any case is not recommended.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
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
   * @deprecated Use {@link #getLong(String, long)}
   * Init was never performed and in any case is not recommended.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public final long getOrInitLong(@NonNls @NotNull String name, long defaultValue) {
    return getLong(name, defaultValue);
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
            if (type.equals(boolean.class)) {
              defaultValue = String.valueOf(field.getBoolean(object));
            }
            else if (type.equals(long.class)) {
              defaultValue = String.valueOf(field.getLong(object));
            }
            else if (type.equals(int.class)) {
              defaultValue = String.valueOf(field.getInt(object));
            }
            else if (type.equals(short.class)) {
              defaultValue = String.valueOf(field.getShort(object));
            }
            else if (type.equals(byte.class)) {
              defaultValue = String.valueOf(field.getByte(object));
            }
            else if (type.equals(double.class)) {
              defaultValue = String.valueOf(field.getDouble(object));
            }
            else if (type.equals(float.class)) {
              defaultValue = String.valueOf(field.getFloat(object));
            }
            else if (type.equals(String.class)) {
              defaultValue = String.valueOf(field.get(object));
            }
          }
          final String stringValue = getValue(annotation.value(), defaultValue);
          Object value = null;

          if (type.equals(boolean.class)) {
            value = Boolean.valueOf(stringValue);
          }
          else if (type.equals(long.class)) {
            value = Long.parseLong(stringValue);
          }
          else if (type.equals(int.class)) {
            value = Integer.parseInt(stringValue);
          }
          else if (type.equals(short.class)) {
            value = Short.parseShort(stringValue);
          }
          else if (type.equals(byte.class)) {
            value = Byte.parseByte(stringValue);
          }
          else if (type.equals(double.class)) {
            value = Double.parseDouble(stringValue);
          }
          else if (type.equals(float.class)) {
            value = Float.parseFloat(stringValue);
          }
          else if (type.equals(String.class)) {
            value = stringValue;
          }

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
        final String value = getValue(name);
        if (value != null) return Float.parseFloat(value);
      }
      catch (NumberFormatException ignore) {
      }
    }
    return defaultValue;
  }
}
