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
package com.intellij.util;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.AtomicFieldUpdater;
import com.intellij.util.containers.FList;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.misc.Unsafe;

import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DebugReflectionUtil {
  private static final Map<Class, Field[]> allFields = new THashMap<Class, Field[]>();
  private static final Field[] EMPTY_FIELD_ARRAY = new Field[0];
  private static final Method Unsafe_shouldBeInitialized = ReflectionUtil.getDeclaredMethod(Unsafe.class, "shouldBeInitialized", Class.class);

  @NotNull
  private static Field[] getAllFields(@NotNull Class aClass) {
    Field[] cached = allFields.get(aClass);
    if (cached == null) {
      try {
        Field[] declaredFields = aClass.getDeclaredFields();
        List<Field> fields = new ArrayList<Field>(declaredFields.length + 5);
        for (Field declaredField : declaredFields) {
          declaredField.setAccessible(true);
          Class<?> type = declaredField.getType();
          if (isTrivial(type)) continue; // unable to hold references, skip
          fields.add(declaredField);
        }
        Class superclass = aClass.getSuperclass();
        if (superclass != null) {
          for (Field sup : getAllFields(superclass)) {
            if (!fields.contains(sup)) {
              fields.add(sup);
            }
          }
        }
        cached = fields.isEmpty() ? EMPTY_FIELD_ARRAY : fields.toArray(new Field[fields.size()]);
      }
      catch (IncompatibleClassChangeError e) {
        //this exception may be thrown because there are two different versions of org.objectweb.asm.tree.ClassNode from different plugins
        //I don't see any sane way to fix it until we load all the plugins by the same classloader in tests
        cached = EMPTY_FIELD_ARRAY;
      }
      catch (SecurityException e) {
        cached = EMPTY_FIELD_ARRAY;
      }
      catch (NoClassDefFoundError e) {
        cached = EMPTY_FIELD_ARRAY;
      }

      allFields.put(aClass, cached);
    }
    return cached;
  }

  private static boolean isTrivial(@NotNull Class<?> type) {
    return type.isPrimitive() || type == String.class || type == Class.class || type.isArray() && isTrivial(type.getComponentType());
  }

  public static boolean processStronglyReferencedValues(@NotNull Object root, PairProcessor<Object, Field> processor) {
    Class rootClass = root.getClass();
    for (Field field : getAllFields(rootClass)) {
      String fieldName = field.getName();
      if (root instanceof Reference && "referent".equals(fieldName)) continue; // do not follow weak/soft refs
      Object value;
      try {
        value = field.get(root);
      }
      catch (IllegalArgumentException e) {
        throw new RuntimeException(e);
      }
      catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
      if (value == null) continue;
      if (!processor.process(value, field)) return false;
    }
    if (rootClass.isArray()) {
      try {
        //noinspection ConstantConditions
        for (Object o : (Object[])root) {
          if (o == null) continue;
          if (isTrivial(o.getClass())) continue;
          if (!processor.process(o, null)) return false;
        }
      }
      catch (ClassCastException ignored) {
      }
    }
    // check for objects leaking via static fields. process classes which already were initialized only
    if (root instanceof Class && isLoadedAlready((Class)root)) {
      try {
        for (Field field : getAllFields((Class)root)) {
          if ((field.getModifiers() & Modifier.STATIC) == 0) continue;
          Object value = field.get(null);
          if (value == null) continue;
          if (!processor.process(value, field)) return false;
        }
      }
      catch (IllegalAccessException ignored) {
      }
    }
    return true;
  }

  private static boolean isLoadedAlready(@NotNull Class root) {
    if (Unsafe_shouldBeInitialized == null) return false;
    boolean isLoadedAlready = false;
    try {
      isLoadedAlready = !(Boolean)Unsafe_shouldBeInitialized.invoke(AtomicFieldUpdater.getUnsafe(), root);
    }
    catch (Exception ignored) {
    }
    //AtomicFieldUpdater.getUnsafe().shouldBeInitialized((Class<?>)root);
    return isLoadedAlready;
  }

  public static class BackLink {
    @NotNull public final Object value;
    private final Field field;
    private final BackLink backLink;

    public BackLink(@NotNull Object value, @Nullable Field field, @Nullable BackLink backLink) {
      this.value = value;
      this.field = field;
      this.backLink = backLink;
    }

    @Override
    public String toString() {
      BackLink backLink = this;
      String result = "";
      while (backLink != null) {
        String valueStr;
        AccessToken token = ReadAction.start();
        try {
          valueStr = backLink.value instanceof FList
                     ? "FList (size="+((FList)backLink.value).size()+")" :
                     backLink.value instanceof Collection ? "Collection (size="+((Collection)backLink.value).size()+")" :
                     String.valueOf(backLink.value);
          valueStr = StringUtil.first(StringUtil.convertLineSeparators(valueStr, "\\n"), 200, true);
        }
        catch (Throwable e) {
          valueStr = "(" + e.getMessage() + " while computing .toString())";
        }
        finally {
          token.finish();
        }
        Field field = backLink.field;
        String fieldName = field == null ? "?" : field.getDeclaringClass().getName()+"."+field.getName();
        result += "via '" + fieldName + "'; Value: '" + valueStr + "' of " + backLink.value.getClass() + "\n";
        backLink = backLink.backLink;
      }
      return result;
    }
  }
}
