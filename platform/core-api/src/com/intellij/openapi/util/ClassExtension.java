// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.openapi.util;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ClassExtension<T> extends KeyedExtensionCollector<T, Class> {
  public ClassExtension(@NonNls final String epName) {
    super(epName);
  }

  @NotNull
  @Override
  protected String keyToString(@NotNull final Class key) {
    return key.getName();
  }

  @NotNull
  @Override
  protected List<T> buildExtensions(@NotNull final String key, @NotNull final Class classKey) {
    final Set<String> allSupers = new LinkedHashSet<>();
    collectSupers(classKey, allSupers);
    return buildExtensionsWithInheritance(allSupers);
  }

  private List<T> buildExtensionsWithInheritance(Set<String> supers) {
    synchronized (lock) {
      List<T> result = null;
      for (String aSuper : supers) {
        result = buildExtensionsFromExplicitRegistration(result, key -> aSuper.equals(key));
      }
      for (String aSuper : supers) {
        result = buildExtensionsFromExtensionPoint(result, bean -> aSuper.equals(bean.getKey()));
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

  @Nullable
  public T forClass(@NotNull Class t) {
    final List<T> ts = forKey(t);
    return ts.isEmpty() ? null : ts.get(0);
  }
}