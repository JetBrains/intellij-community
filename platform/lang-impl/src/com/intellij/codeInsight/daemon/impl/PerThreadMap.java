/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.util.UserDataHolder;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * User: cdr
 */
public abstract class PerThreadMap<T, KeyT extends UserDataHolder> {

  @NotNull
  public abstract Collection<T> initialValue(@NotNull KeyT key);

  private final ThreadLocal<Map<KeyT,List<T>>> CACHE = new ThreadLocal<Map<KeyT, List<T>>>(){
    @Override
    protected Map<KeyT, List<T>> initialValue() {
      return new THashMap<KeyT, List<T>>();
    }
  };

  private List<T> cloneTemplates(Collection<T> templates) {
    List<T> result = new ArrayList<T>(templates.size());
    for (T template : templates) {
      Class<? extends T> aClass = (Class<? extends T>)template.getClass();
      try {
        T clone = aClass.newInstance();
        result.add(clone);
      }
      catch (InstantiationException e) {
        throw new RuntimeException("Cannot instantiate annotator "+aClass+". There must be public no-args constructor", e);
      }
      catch (IllegalAccessException e) {
        throw new RuntimeException("Cannot access annotator "+aClass+". There must be public no-args constructor", e);
      }
    }
    return result;
  }

  @NotNull
  public List<T> get(@NotNull KeyT key) {
    Map<KeyT, List<T>> map = CACHE.get();
    List<T> cached = map.get(key);
    if (cached == null) {
      Collection<T> templates = initialValue(key);
      cached = cloneTemplates(templates);
      map.put(key, cached);
    }
    return cached;
  }
}
