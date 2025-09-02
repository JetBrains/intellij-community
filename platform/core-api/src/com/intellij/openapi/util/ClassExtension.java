// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.util.KeyedLazyInstance;
import kotlinx.collections.immutable.PersistentList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static kotlinx.collections.immutable.ExtensionsKt.persistentListOf;

public class ClassExtension<T> extends KeyedExtensionCollector<T, Class<?>> {
  public ClassExtension(@NotNull String epName) {
    super(epName);
  }

  @Override
  protected final @NotNull String keyToString(@NotNull Class<?> key) {
    return key.getName();
  }

  @Override
  protected final @NotNull @Unmodifiable List<T> buildExtensions(@NotNull String key, @NotNull Class classKey) {
    Set<String> allSupers = new LinkedHashSet<>();
    collectSupers(classKey, allSupers);
    return buildExtensionsWithInheritance(allSupers);
  }

  private PersistentList<T> buildExtensionsWithInheritance(Set<String> supers) {
    List<KeyedLazyInstance<T>> extensions = getExtensions();
    synchronized (lock) {
      PersistentList<T> result = persistentListOf();
      for (String aSuper : supers) {
        result = result.addAll(buildExtensionsFromExplicitRegistration(key -> aSuper.equals(key)));
      }
      for (String aSuper : supers) {
        result = result.addAll(buildExtensionsFromExtensionPoint(bean -> aSuper.equals(bean.getKey()), extensions));
      }
      return result;
    }
  }

  private static void collectSupers(@NotNull Class<?> classKey, @NotNull Set<? super String> allSupers) {
    allSupers.add(classKey.getName());
    for (Class<?> anInterface : classKey.getInterfaces()) {
      collectSupers(anInterface, allSupers);
    }

    Class<?> superClass = classKey.getSuperclass();
    if (superClass != null) {
      collectSupers(superClass, allSupers);
    }
  }

  public final @Nullable T forClass(@NotNull Class<?> t) {
    return findSingle(t);
  }

  @Override
  protected final void invalidateCacheForExtension(@NotNull String key) {
    clearCache();
  }
}