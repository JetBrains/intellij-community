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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.pico.ConstructorInjectionComponentAdapter;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.PicoContainer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * User: cdr
 */
abstract class ThreadLocalAnnotatorMap<KeyT, T> {
  private volatile int version;
  @NotNull
  public abstract Collection<T> initialValue(@NotNull KeyT key);

  private static class VersionedMap<KeyT, T> extends THashMap<KeyT, List<T>> {
    private final int version;

    private VersionedMap(int version) {
      this.version = version;
    }
  }

  private final ThreadLocal<VersionedMap<KeyT, T>> CACHE = new ThreadLocal<VersionedMap<KeyT, T>>(){
    @Override
    protected VersionedMap<KeyT, T> initialValue() {
      return new VersionedMap<KeyT, T>(version);
    }
  };

  @SuppressWarnings("unchecked")
  @NotNull
  private List<T> cloneTemplates(@NotNull Collection<T> templates) {
    List<T> result = new ArrayList<T>(templates.size());
    PicoContainer container = ApplicationManager.getApplication().getPicoContainer();
    for (T template : templates) {
      Class<? extends T> aClass = (Class<? extends T>)template.getClass();
      T clone = (T)new ConstructorInjectionComponentAdapter(aClass.getName(), aClass).getComponentInstance(container);
      result.add(clone);
    }
    return result;
  }

  @NotNull
  public List<T> get(@NotNull KeyT key) {
    VersionedMap<KeyT, T> map = CACHE.get();
    if (version != map.version) {
      CACHE.remove();
      map = CACHE.get();
    }
    List<T> cached = map.get(key);
    if (cached == null) {
      Collection<T> templates = initialValue(key);
      cached = cloneTemplates(templates);
      map.put(key, cached);
    }
    return cached;
  }

  public void clear() {
    version++; //we don't care about atomicity here
  }
}
