// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.pico.CachingConstructorInjectionComponentAdapter;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.PicoContainer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

abstract class ThreadLocalAnnotatorMap<K, V> {
  @NotNull
  public abstract Collection<V> initialValue(@NotNull K key);

  private final ThreadLocal<THashMap<K, List<V>>> CACHE = ThreadLocal.withInitial(() -> new THashMap<>());

  @SuppressWarnings("unchecked")
  @NotNull
  private List<V> cloneTemplates(@NotNull Collection<? extends V> templates) {
    List<V> result = new ArrayList<>(templates.size());
    PicoContainer container = ApplicationManager.getApplication().getPicoContainer();
    for (V template : templates) {
      Class<? extends V> aClass = (Class<? extends V>)template.getClass();
      V clone;
      // todo in general CachingConstructorInjectionComponentAdapter should be not used at all, but for now disable it only for known cases
      if (Annotator.class.isAssignableFrom(aClass)) {
        clone = ReflectionUtil.newInstance(aClass);
      }
      else {
        clone = (V)new CachingConstructorInjectionComponentAdapter(aClass.getName(), aClass, null, true).getComponentInstance(container);
      }
      result.add(clone);
    }
    return result;
  }

  @NotNull
  public List<V> get(@NotNull K key) {
    THashMap<K, List<V>> map = CACHE.get();
    List<V> cached = map.get(key);
    if (cached == null) {
      Collection<V> templates = initialValue(key);
      cached = cloneTemplates(templates);
      map.put(key, cached);
    }
    return cached;
  }
}
