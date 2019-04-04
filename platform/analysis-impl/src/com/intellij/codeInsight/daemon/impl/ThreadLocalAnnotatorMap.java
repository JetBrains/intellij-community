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
  private volatile int version;
  @NotNull
  public abstract Collection<V> initialValue(@NotNull K key);

  private static class VersionedMap<K, V> extends THashMap<K, List<V>> {
    private final int version;

    private VersionedMap(int version) {
      this.version = version;
    }
  }

  private final ThreadLocal<VersionedMap<K, V>> CACHE = new ThreadLocal<VersionedMap<K, V>>(){
    @Override
    protected VersionedMap<K, V> initialValue() {
      return new VersionedMap<>(version);
    }
  };

  @SuppressWarnings("unchecked")
  @NotNull
  private List<V> cloneTemplates(@NotNull Collection<V> templates) {
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
    VersionedMap<K, V> map = CACHE.get();
    if (version != map.version) {
      CACHE.remove();
      map = CACHE.get();
    }
    List<V> cached = map.get(key);
    if (cached == null) {
      Collection<V> templates = initialValue(key);
      cached = cloneTemplates(templates);
      map.put(key, cached);
    }
    return cached;
  }

  public void clear() {
    version++; //we don't care about atomicity here
  }
}
