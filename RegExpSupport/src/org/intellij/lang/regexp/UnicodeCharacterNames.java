// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ReflectionUtil;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * @author Bas Leijdekkers
 */
public final class UnicodeCharacterNames {
  public static void iterate(Consumer<? super String> consumer) {
    try {
      final Class<?> aClass = Class.forName("java.lang.CharacterName");
      final Method initNamePool = ReflectionUtil.getDeclaredMethod(aClass, "initNamePool");
      if (initNamePool != null) { // jdk 8
        byte[] namePool = (byte[])initNamePool.invoke(null); // initializes "lookup" field
        final int[][] lookup2d = ReflectionUtil.getStaticFieldValue(aClass, int[][].class, "lookup");
        if (lookup2d == null) {
          return;
        }
        for (int[] indexes : lookup2d) {
          if (indexes != null) {
            for (int index : indexes) {
              if (index != 0) {
                final String name = new String(namePool, index >>> 8, index & 0xff, StandardCharsets.US_ASCII);
                consumer.accept(name);
              }
            }
          }
        }
      }
      else {
        final Method instance = ReflectionUtil.getDeclaredMethod(aClass, "getInstance");
        final Field field1 = ReflectionUtil.getDeclaredField(aClass, "strPool");
        final Field field2 = ReflectionUtil.getDeclaredField(aClass, "lookup");
        if (instance != null && field1 != null && field2 != null) { // jdk 9
          final Object characterName = instance.invoke(null);
          byte[] namePool = (byte[])field1.get(characterName);
          final int[] lookup = (int[])field2.get(characterName);
          for (int index : lookup) {
            if (index != 0) {
              final String name = new String(namePool, index >>> 8, index & 0xff, StandardCharsets.US_ASCII);
              consumer.accept(name);
            }
          }
        }
      }
    }
    catch (ClassNotFoundException | InvocationTargetException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public static int getCodePoint(String name) {
    if (name == null) {
      return -1;
    }
    final Method method = ReflectionUtil.getMethod(Character.class, "codePointOf", String.class); // jdk 9 method
    if (method != null) {
      try {
        return (int)method.invoke(null, name);
      }
      catch (IllegalArgumentException e) {
        return -1;
      }
      catch (InvocationTargetException e) {
        if (e.getCause() instanceof IllegalArgumentException) {
          return -1;
        }
        throw new RuntimeException(e);
      }
      catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
    try {
      // jdk 8 fallback
      final Class<?> aClass = Class.forName("java.lang.CharacterName");
      final Method initNamePool = ReflectionUtil.getDeclaredMethod(aClass, "initNamePool");
      if (initNamePool == null) {
        return -1; // give up
      }
      byte[] namePool = (byte[])initNamePool.invoke(null);
      name = StringUtil.toUpperCase(name.trim());
      byte[] key = name.getBytes(StandardCharsets.ISO_8859_1);
      final int[][] lookup = ReflectionUtil.getField(aClass, null, int[][].class, "lookup");
      if (lookup == null) throw new RuntimeException();
      for (int i = 0; i < lookup.length; i++) {
        int[] indexes = lookup[i];
        if (indexes != null) {
          for (int j = 0; j < indexes.length; j++) {
            int index = indexes[j];
            if ((index & 0xFF) == key.length && matches(namePool, index >>> 8, key)) {
              return i << 8 | j;
            }
          }
        }
      }
      return getUnnamedUnicodeCharacterCodePoint(name);
    }
    catch (ClassNotFoundException | InvocationTargetException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  private static int getUnnamedUnicodeCharacterCodePoint(String name) {
    int index = name.lastIndexOf(' ');
    if (index != -1) {
      try {
        int c = Integer.parseInt(name.substring(index + 1), 16);
        if (Character.isValidCodePoint(c) && name.equals(Character.getName(c))) return c;
      }
      catch (NumberFormatException ignore) {
      }
    }
    return -1;
  }

  private static boolean matches(byte[] bytes, int offset, byte[] key) {
    if (offset < 0 || offset + key.length > bytes.length) {
      throw new IllegalArgumentException();
    }
    for (int i = 0; i < key.length; i++) {
      if (bytes[i + offset] != key[i]) {
        return false;
      }
    }
    return true;
  }
}
