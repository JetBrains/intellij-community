/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolder;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.PicoContainer;
import org.picocontainer.defaults.ConstructorInjectionComponentAdapter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * User: cdr
 */
abstract class ThreadLocalAnnotatorMap<T, KeyT extends UserDataHolder> {
  private volatile int version;
  @NotNull
  public abstract Collection<T> initialValue(@NotNull KeyT key);

  // pair(version, map)
  private final ThreadLocal<Pair<Integer, Map<KeyT,List<T>>>> CACHE = new ThreadLocal<Pair<Integer, Map<KeyT,List<T>>>>(){
    @Override
    protected Pair<Integer, Map<KeyT,List<T>>> initialValue() {
      return Pair.<Integer, Map<KeyT,List<T>>>create(version, new THashMap<KeyT, List<T>>());
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
    Pair<Integer, Map<KeyT, List<T>>> pair = CACHE.get();
    Integer mapVersion = pair.getFirst();
    if (version != mapVersion) {
      CACHE.remove();
      pair = CACHE.get();
    }
    Map<KeyT, List<T>> map = pair.getSecond();
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
