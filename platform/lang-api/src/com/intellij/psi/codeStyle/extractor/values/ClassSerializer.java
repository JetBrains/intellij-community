// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.extractor.values;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class ClassSerializer {
  private final @NotNull String myInstanceName;
  private final @NotNull Object myInstance;

  public ClassSerializer(@NotNull String instanceName, @NotNull Object o) {
    myInstanceName = instanceName;
    myInstance = o;
  }

  public @Nullable Object read(@NotNull String name) {
    try {
      final Field field = getPreparedField(myInstance.getClass().getField(name));
      if (field == null) return null;
      return field.get(myInstance);
    }
    catch (NoSuchFieldException | IllegalAccessException e) {
      e.printStackTrace();
    }
    return null;
  }

  @Contract("_, _, false -> null")
  public @Nullable Object write(@NotNull String name, @NotNull Object value, boolean retPrevValue) {
    try {
      final Field field = getPreparedField(myInstance.getClass().getField(name));
      if (field != null) {
        Object ret = retPrevValue ? field.get(myInstance) : null;
        field.set(myInstance, value);
        return ret;
      }
    }
    catch (NoSuchFieldException | IllegalAccessException e) {
      e.printStackTrace();
    }
    return null;
  }

  public static @Nullable Field getPreparedField(Field field) {
    field.setAccessible(true);
    Class<?> type = field.getType();
    if ((field.getModifiers() & (Modifier.STATIC | Modifier.FINAL)) != 0) {
      return null;
    }
    if (type != int.class && type != boolean.class) {
      return null;
    }
    if (field.getName().startsWith("my")) {
      return null;
    }
    return field;
  }

  public @NotNull String getInstanceName() {
    return myInstanceName;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof ClassSerializer other) {
      return other.myInstance.equals(myInstance);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return myInstance.hashCode();
  }
}
