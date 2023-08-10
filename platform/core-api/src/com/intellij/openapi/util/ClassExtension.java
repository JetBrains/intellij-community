// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ClassExtension<T> extends KeyedExtensionCollector<T, Class> {
  public ClassExtension(@NotNull String epName) {
    super(epName);
  }

  @Override
  protected @NotNull String keyToString(@NotNull Class key) {
    return key.getName();
  }

  @Override
  protected @NotNull List<T> buildExtensions(@NotNull String key, @NotNull Class classKey) {
    final Set<String> allSupers = new LinkedHashSet<>();
    collectSupers(classKey, allSupers);
    return buildExtensionsWithInheritance(allSupers);
  }

  private List<T> buildExtensionsWithInheritance(Set<String> supers) {
    List<KeyedLazyInstance<T>> extensions = getExtensions();
    synchronized (myLock) {
      List<T> result = null;
      for (String aSuper : supers) {
        result = buildExtensionsFromExplicitRegistration(result, key -> aSuper.equals(key));
      }
      for (String aSuper : supers) {
        result = buildExtensionsFromExtensionPoint(result, bean -> aSuper.equals(bean.getKey()), extensions);
      }
      return ContainerUtil.notNullize(result);
    }
  }

  private static void collectSupers(@NotNull Class classKey, @NotNull Set<? super String> allSupers) {
    allSupers.add(classKey.getName());
    final Class[] interfaces = classKey.getInterfaces();
    for (final Class anInterface : interfaces) {
      collectSupers(anInterface, allSupers);
    }

    final Class superClass = classKey.getSuperclass();
    if (superClass != null) {
      collectSupers(superClass, allSupers);
    }
  }

  public @Nullable T forClass(@NotNull Class t) {
    final List<T> ts = forKey(t);
    return ts.isEmpty() ? null : ts.get(0);
  }

  @Override
  protected void invalidateCacheForExtension(String key) {
    clearCache();
  }
}