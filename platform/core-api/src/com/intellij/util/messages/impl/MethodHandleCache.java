// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class MethodHandleCache {
  private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
  private static final ClassValue<ConcurrentMap<Method, MethodHandle>> CACHE = new ConcurrentMapClassValue();

  static @NotNull MethodHandle compute(@NotNull Method method, Object @Nullable [] args) {
    // method name cannot be used as key because for one class maybe several methods with the same name and different set of parameters
    return CACHE.get(method.getDeclaringClass()).computeIfAbsent(method, method1 -> {
      method1.setAccessible(true);
      MethodHandle result;
      try {
        result = LOOKUP.unreflect(method1);
      }
      catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
      return args == null ? result : result.asSpreader(Object[].class, args.length);
    });
  }

  // as static to ensure that class doesn't reference anything else (otherwise maybe memory leak)
  private static final class ConcurrentMapClassValue extends ClassValue<ConcurrentMap<Method, MethodHandle>> {
    @Override
    protected ConcurrentMap<Method, MethodHandle> computeValue(@NotNull Class<?> type) {
      return new ConcurrentHashMap<>(8);
    }
  }
}
