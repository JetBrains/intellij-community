/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.extractor.values;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class ClassSerializer {
  @NotNull
  private String myInstanceName;
  @NotNull
  private final Object myInstance;

  public ClassSerializer(@NotNull String instanceName, @NotNull Object o) {
    myInstanceName = instanceName;
    myInstance = o;
  }

  @Nullable
  public Object read(@NotNull String name) {
    try {
      final Field field = getPreparedField(myInstance.getClass().getField(name));
      if (field == null) return null;
      return field.get(myInstance);
    }
    catch (NoSuchFieldException e) {
      e.printStackTrace();
    }
    catch (IllegalAccessException e) {
      e.printStackTrace();
    }
    return null;
  }

  @Nullable
  @Contract("_, _, false -> null")
  public Object write(@NotNull String name, @NotNull Object value, boolean retPrevValue) {
    try {
      final Field field = getPreparedField(myInstance.getClass().getField(name));
      if (field != null) {
        Object ret = retPrevValue ? field.get(myInstance) : null;
        field.set(myInstance, value);
        return ret;
      }
    }
    catch (NoSuchFieldException e) {
      e.printStackTrace();
    }
    catch (IllegalAccessException e) {
      e.printStackTrace();
    }
    return null;
  }

  @Nullable
  public static Field getPreparedField(Field field) {
    field.setAccessible(true);
    Class<?> type = field.getType();
    if ((field.getModifiers() & Modifier.STATIC) != 0) {
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

  @NotNull
  public String getInstanceName() {
    return myInstanceName;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof ClassSerializer) {
      ClassSerializer other = (ClassSerializer) o;
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
