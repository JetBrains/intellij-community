// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.UserDataHolderEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * This class provides a generic graph infrastructure with ability to store particular data. The main purpose is to
 * allow easy extensible data domain construction.
 * <p/>
 * Example: we might want to describe project model like 'project' which has multiple 'module' children where every
 * 'module' has a collection of child 'content root' and dependencies nodes etc. When that is done, plugins can easily
 * enhance any project. For example, particular framework can add facet settings as one more 'project' node's child.
 * <p/>
 * Not thread-safe.
 */
public class DataNode<T> implements UserDataHolderEx {
  private static final Logger LOG = Logger.getInstance(DataNode.class);

  @SuppressWarnings("NullableProblems") @NotNull
  private Key<T> key;

  @NotNull
  private final transient UserDataHolderBase userData = new UserDataHolderBase();

  @Nullable
  private T data;

  private boolean ignored;

  private transient volatile boolean ready;

  @Nullable
  private DataNode<?> parent;

  @NotNull
  private final List<DataNode<?>> children = new ArrayList<>();

  @Nullable
  private transient List<DataNode<?>> childrenView;

  public DataNode(@NotNull Key<T> key, @NotNull T data, @Nullable DataNode<?> parent) {
    this.key = key;
    this.data = data;
    this.parent = parent;
  }

  public boolean isReady() {
    return ready;
  }

  // deserialization, data decoded on demand
  @SuppressWarnings("unused")
  private DataNode() {
  }

  @Nullable
  public DataNode<?> getParent() {
    return parent;
  }

  @NotNull
  public <T> DataNode<T> createChild(@NotNull Key<T> key, @NotNull T data) {
    DataNode<T> result = new DataNode<>(key, data, this);
    children.add(result);
    return result;
  }

  @NotNull
  public Key<T> getKey() {
    return key;
  }

  @NotNull
  public T getData() {
    return data;
  }

  public boolean isIgnored() {
    return ignored;
  }

  public void setIgnored(boolean ignored) {
    this.ignored = ignored;
  }

  /**
   * Allows to replace or modify data. If function returns null, data is left unchanged
   * @param visitor visitor. Must accept argument of type T and return value of type T
   */
  public void visitData(@Nullable Function visitor) {
    if (visitor == null) {
      return;
    }
    @SuppressWarnings("unchecked")
    T newData = (T) visitor.apply(getData());
    if (newData != null) {
      data = newData;
    }
  }

  /**
   * Allows to retrieve data stored for the given key at the current node or any of its parents.
   *
   * @param key  target data's key
   * @param <D>  target data type
   * @return data stored for the current key and available via the current node (if any)
   */
  @SuppressWarnings("unchecked")
  @Nullable
  public <T> T getData(@NotNull Key<T> key) {
    if (this.key.equals(key)) {
      return (T)data;
    }
    for (DataNode<?> p = parent; p != null; p = p.parent) {
      if (p.key.equals(key)) {
        return (T)p.data;
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  @Nullable
  public <T> DataNode<T> getDataNode(@NotNull Key<T> key) {
    if (this.key.equals(key)) {
      return (DataNode<T>)this;
    }
    for (DataNode<?> p = parent; p != null; p = p.parent) {
      if (p.key.equals(key)) {
        return (DataNode<T>)p;
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  @Nullable
  public <P> DataNode<P> getParent(@NotNull Class<P> dataClass) {
    if (dataClass.isInstance(data)) {
      return (DataNode<P>)this;
    }
    for (DataNode<?> p = parent; p != null; p = p.parent) {
      if (dataClass.isInstance(p.data)) {
        return (DataNode<P>)p;
      }
    }
    return null;
  }

  public void addChild(@NotNull DataNode<?> child) {
    child.parent = this;
    children.add(child);
  }

  @NotNull
  public Collection<DataNode<?>> getChildren() {
    List<DataNode<?>> result = childrenView;
    if (result == null) {
      result = Collections.unmodifiableList(children);
      childrenView = result;
    }
    return result;
  }

  @Override
  public int hashCode() {
    // We can't use myChildren.hashCode() because it iterates whole subtree. This should not produce many collisions because 'getData()'
    // usually refers to different objects
    return 31 * key.hashCode() + getData().hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DataNode node = (DataNode)o;

    if (!children.equals(node.children)) return false;
    if (!getData().equals(node.getData())) return false;
    if (!key.equals(node.key)) return false;

    return true;
  }

  @Override
  public String toString() {
    String dataDescription;
    try {
      dataDescription = getData().toString();
    }
    catch (Exception e) {
      dataDescription = "failed to load";
      LOG.debug(e);
    }
    return String.format("%s: %s", key, dataDescription);
  }

  public void clear(boolean removeFromGraph) {
    if (removeFromGraph && parent != null) {
      for (Iterator<DataNode<?>> iterator = parent.children.iterator(); iterator.hasNext(); ) {
        DataNode<?> dataNode = iterator.next();
        if (System.identityHashCode(dataNode) == System.identityHashCode(this)) {
          iterator.remove();
          break;
        }
      }
    }
    parent = null;
    children.clear();
  }

  @NotNull
  public DataNode<T> graphCopy() {
    return copy(this, null);
  }

  @NotNull
  public DataNode<T> nodeCopy() {
    return nodeCopy(this);
  }

  @Nullable
  @Override
  public <U> U getUserData(@NotNull com.intellij.openapi.util.Key<U> key) {
    return userData.getUserData(key);
  }

  @Override
  public <U> void putUserData(@NotNull com.intellij.openapi.util.Key<U> key, U value) {
    userData.putUserData(key, value);
  }

  public <U> void removeUserData(@NotNull com.intellij.openapi.util.Key<U> key) {
    userData.putUserData(key, null);
  }

  @NotNull
  @Override
  public <D> D putUserDataIfAbsent(@NotNull com.intellij.openapi.util.Key<D> key, @NotNull D value) {
    return userData.putUserDataIfAbsent(key, value);
  }

  @Override
  public <D> boolean replace(@NotNull com.intellij.openapi.util.Key<D> key, @Nullable D oldValue, @Nullable D newValue) {
    return userData.replace(key, oldValue, newValue);
  }

  public <T> void putCopyableUserData(@NotNull com.intellij.openapi.util.Key<T> key, T value) {
    userData.putCopyableUserData(key, value);
  }

  public <T> T getCopyableUserData(@NotNull com.intellij.openapi.util.Key<T> key) {
    return userData.getCopyableUserData(key);
  }

  public boolean validateData() {
    if (data == null) {
      ready = false;
      clear(true);
    }
    else {
      ready = true;
    }
    return ready;
  }

  @NotNull
  public static <T> DataNode<T> nodeCopy(@NotNull DataNode<T> dataNode) {
    DataNode<T> copy = new DataNode<>();
    copy.key = dataNode.key;
    copy.data = dataNode.data;
    copy.ignored = dataNode.ignored;
    copy.ready = dataNode.ready;
    dataNode.userData.copyCopyableDataTo(copy.userData);
    return copy;
  }

  @NotNull
  private static <T> DataNode<T> copy(@NotNull DataNode<T> dataNode, @Nullable DataNode<?> newParent) {
    DataNode<T> copy = nodeCopy(dataNode);
    copy.parent = newParent;
    for (DataNode<?> child : dataNode.children) {
      copy.addChild(copy(child, copy));
    }
    return copy;
  }

  public final void visit(@NotNull Consumer<? super DataNode<?>> consumer) {
    ArrayDeque<List<DataNode<?>>> toProcess = new ArrayDeque<>();
    toProcess.add(Collections.singletonList(this));
    List<DataNode<?>> nodes;
    while ((nodes = toProcess.pollFirst()) != null) {
      nodes.forEach(consumer);
      for (DataNode<?> node : nodes) {
        toProcess.add(node.children);
      }
    }
  }
}