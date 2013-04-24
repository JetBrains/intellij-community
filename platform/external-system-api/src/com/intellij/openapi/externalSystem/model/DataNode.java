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

import java.io.Serializable;
import java.util.*;

/**
 * This class provides a generic graph infrastructure with ability to store particular data. The main purpose is to 
 * allow easy extensible data domain construction.
 * <p/>
 * Example: we might want to describe project model like 'project' which has multiple 'module' children where every
 * 'module' has a collection of child 'content root' and dependencies nodes etc. When that is done, plugins can easily
 * enhance any project. For example, particular framework can add facet settings as one more 'project' node's child.
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since 4/12/13 11:53 AM
 */
public class DataNode<T> implements Serializable {

  private static final long serialVersionUID = 1L;

  @NotNull private final List<DataNode<?>> myChildren = ContainerUtilRt.newArrayList();

  @NotNull private final Key<T> myKey;
  @NotNull private final T      myData;

  @Nullable private final DataNode<?> myParent;

  public DataNode(@NotNull Key<T> key, @NotNull T data, @Nullable DataNode<?> parent) {
    myKey = key;
    myData = data;
    myParent = parent;
  }

  @NotNull
  public <T> DataNode<T> createChild(@NotNull Key<T> key, @NotNull T data) {
    DataNode<T> result = new DataNode<T>(key, data, this);
    myChildren.add(result);
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

  /**
   * Allows to retrieve data stored for the given key at the current node or any of its parents.
   *
   * @param key  target data's key
   * @param <T>  target data type
   * @return data stored for the current key and available via the current node (if any)
   */
  @SuppressWarnings("unchecked")
  @Nullable
  public <T> T getData(@NotNull Key<T> key) {
    if (myKey.equals(key)) {
      return (T)myData;
    }
    for (DataNode<?> p = myParent; p != null; p = p.myParent) {
      if (p.myKey.equals(key)) {
        return (T)p.myData;
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  @Nullable
  public <T> DataNode<T> getDataNode(@NotNull Key<T> key) {
    if (myKey.equals(key)) {
      return (DataNode<T>)this;
    }
    for (DataNode<?> p = myParent; p != null; p = p.myParent) {
      if (p.myKey.equals(key)) {
        return (DataNode<T>)p;
      }
    }
    return null;
  }

  public void addChild(@NotNull DataNode<?> child) {
    myChildren.add(child);
  }

  @NotNull
  public Collection<DataNode<?>> getChildren() {
    return myChildren;
  }

  @Override
  public int hashCode() {
    int result = myChildren.hashCode();
    result = 31 * result + myKey.hashCode();
    result = 31 * result + myData.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DataNode node = (DataNode)o;

    if (!myChildren.equals(node.myChildren)) return false;
    if (!myData.equals(node.myData)) return false;
    if (!myKey.equals(node.myKey)) return false;

    return true;
  }

  @Override
  public String toString() {
    return String.format("%s: %s", myKey, myData);
  }
}
