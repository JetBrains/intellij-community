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
package com.intellij.openapi.externalSystem.model;

import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * This is a base class for representing hierarchical data at external system domain. It holds information about
 * {@link #getData() target data} as well as {@link #getCompositeNestedData(Key) references to other data holders}.
 * <p/>
 * For example, consider a 'project' data holder - it holds common project information like 'location' as well as composite
 * nested data like 'modules', 'libraries' etc.
 * <p/>
 * The main goal of such an approach is that it allows flexible extending of the base intellij platform logic, i.e. when
 * a new non-standard project structure entity needs to be supported (e.g. particular facet), corresponding key and a data
 * class might be defined at a plugin and then they can be registered during external project construction and delivered
 * to the ide for further processing.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 4/12/13 11:53 AM
 */
public class DataHolder<T> {

  @NotNull private final Map<Key<?>, Object> myNestedData = ContainerUtilRt.newHashMap();
  @NotNull private final Key<T> myKey;
  @NotNull private final T      myData;

  @Nullable private final DataHolder<?> myParent;

  public DataHolder(@NotNull Key<T> key, @NotNull T data, @Nullable DataHolder<?> parent) {
    myKey = key;
    myData = data;
    myParent = parent;
  }

  @NotNull
  public <T> DataHolder<T> createSingleChild(@NotNull Key<T> key, @NotNull T data) {
    DataHolder<T> result = new DataHolder<T>(key, data, this);
    register(key, result);
    return result;
  }

  @NotNull
  public <T> DataHolder<T> createChildAtList(@NotNull Key<T> key, @NotNull T data) {
    DataHolder<T> result = new DataHolder<T>(key, data, this);
    registerInList(key, result);
    return result;
  }

  @NotNull
  public <T> DataHolder<T> createChildAtSet(@NotNull Key<T> key, @NotNull T data) {
    DataHolder<T> result = new DataHolder<T>(key, data, this);
    registerInSet(key, result);
    return result;
  }


  @NotNull
  public Key<T> getKey() {
    return myKey;
  }

  @NotNull
  public T getData() {
    return myData;
  }
  
  @SuppressWarnings("unchecked")
  @Nullable
  public <T> T getData(@NotNull Key<T> key) {
    if (myKey.equals(key)) {
      return (T)myData;
    }
    Object result = myNestedData.get(key);
    if (result != null) {
      return (T)result;
    }
    for (DataHolder<?> p = myParent; p != null; p = p.myParent) {
      if (p.myKey.equals(key)) {
        return (T)p.myData;
      }
    }
    return null;
  }

  @NotNull
  public Set<Key<?>> getNestedDataKeys() {
    return myNestedData.keySet();
  }

  @SuppressWarnings("unchecked")
  @Nullable
  public <T> Collection<DataHolder<T>> getCompositeNestedData(@NotNull Key<T> key) {
    return (Collection<DataHolder<T>>)myNestedData.get(key);
  }

  /**
   * Registers given data for the given key within the current container.
   * <p/>
   * The trick here is that the data for the same key might be composite at one data holder and non-composite at another.
   * E.g. a 'project' holder might have more than one module, i.e. composite data for a 'module' key. But 'content root' holder which
   * has a reference to its module references a single data. That's why we have this method which assumes that target data is
   * a non-composite as well as {@link #registerInList(Key, DataHolder)} and {@link #registerInSet(Key, DataHolder)}.
   *
   * @param key   target key
   * @param data  data to store for the given key at the current object
   * @param <T>   target data type
   */
  @SuppressWarnings("unchecked")
  public <T> void register(@NotNull Key<T> key, DataHolder<T> data) {
    myNestedData.put(key, data);
  }

  /**
   * @see #register(Key, DataHolder)
   */
  public <T> void registerInList(@NotNull Key<T> key, DataHolder<T> data) {
    Collection<DataHolder<T>> datas = getCompositeNestedData(key);
    if (datas == null) {
      myNestedData.put(key, datas = ContainerUtilRt.newArrayList(data));
    }
    datas.add(data);
  }

  /**
   * @see #register(Key, DataHolder)
   */
  public <T> void registerInSet(@NotNull Key<T> key, DataHolder<T> data) {
    Collection<DataHolder<T>> datas = getCompositeNestedData(key);
    if (datas == null) {
      myNestedData.put(key, datas = ContainerUtilRt.newHashSet(data));
    }
    datas.add(data);
  }

  public void clear(@NotNull Key<?> key) {
    myNestedData.remove(key);
  }
}
