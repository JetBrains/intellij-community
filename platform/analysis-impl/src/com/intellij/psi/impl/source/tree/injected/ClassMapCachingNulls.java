// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

final class ClassMapCachingNulls<T> {
  private final Map<Class<?>, T[]> myBackingMap;
  private final T[] myEmptyArray;
  private final List<? extends T> myOrderingArray;
  private final Map<Class<?>, T[]> myMap = new ConcurrentHashMap<>();

  ClassMapCachingNulls(@NotNull Map<Class<?>, T[]> backingMap, T[] emptyArray, @NotNull List<? extends T> orderingArray) {
    myBackingMap = backingMap;
    myEmptyArray = emptyArray;
    myOrderingArray = orderingArray;
  }

  T @Nullable [] get(@NotNull Class<?> aClass) {
    T[] value = myMap.get(aClass);
    if (value != null) {
      if (value == myEmptyArray) {
        return null;
      }
      else {
        assert value.length != 0;
        return value;
      }
    }
    return cache(aClass, getFromBackingMap(aClass));
  }

  private T[] cache(@NotNull Class<?> aClass, @Nullable @Unmodifiable List<T> result) {
    T[] value;
    if (result == null) {
      myMap.put(aClass, myEmptyArray);
      value = null;
    }
    else {
      assert !result.isEmpty();
      value = result.toArray(myEmptyArray);
      myMap.put(aClass, value);
    }
    return value;
  }

  @Unmodifiable
  private @Nullable List<T> getFromBackingMap(@NotNull Class<?> aClass) {
    T[] value = myBackingMap.get(aClass);
    Set<T> result = null;
    if (value != null) {
      assert value.length != 0;
      result = new HashSet<>(value.length);
      Collections.addAll(result, value);
    }

    Class<?> superClass = aClass.getSuperclass();
    if (superClass != null) {
      result = addFromUpper(result, superClass);
    }
    for (Class<?> superInterface : aClass.getInterfaces()) {
      result = addFromUpper(result, superInterface);
    }

    return result == null ? null : ContainerUtil.findAll(myOrderingArray, result::contains);
  }

  private @Nullable Set<T> addFromUpper(@Nullable Set<T> value, @NotNull Class<?> superclass) {
    T[] fromUpper = get(superclass);
    if (fromUpper != null) {
      assert fromUpper.length != 0;
      if (value == null) {
        value = new HashSet<>(fromUpper.length);
      }
      Collections.addAll(value, fromUpper);
      assert !value.isEmpty();
    }
    return value;
  }

  @NotNull Map<Class<?>, T[]> getBackingMap() {
    return myBackingMap;
  }
}