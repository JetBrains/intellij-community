// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.serialization.ObjectSerializer;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Proxy;
import java.util.*;
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
 *
 * {@link #serializeData} must be called before serialization.
 */
public class DataNode<T> implements UserDataHolderEx {
  private static final Logger LOG = Logger.getInstance(DataNode.class);

  @SuppressWarnings("NullableProblems") @NotNull
  private Key<T> key;

  @NotNull
  private final transient UserDataHolderBase userData = new UserDataHolderBase();

  private transient T data;

  // Key data type class cannot be used because can specify interface class and not actual data class
  private String dataClassName;
  private byte[] rawData;

  private boolean ignored;

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

  // deserialization, serializer can create object without default constructor, but in this case fields (userData) will be not initialized to default values
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
    if (data == null) {
      deserializeData(Arrays.asList(getClass().getClassLoader(), Thread.currentThread().getContextClassLoader()));
    }
    return data;
  }

  public boolean isIgnored() {
    return ignored;
  }

  public void setIgnored(boolean ignored) {
    this.ignored = ignored;
  }

  /**
   * This class is a generic holder for any kind of project data. That project data might originate from different locations, e.g.
   * core ide plugins, non-core ide plugins, third-party plugins etc. That means that when a service from a core plugin needs to
   * unmarshall {@link DataNode} object, its content should not be unmarshalled as well because its class might be unavailable here.
   * <p/>
   * That's why the content is delivered as a raw byte array and this method allows to build actual java object from it using
   * the right class loader.
   * <p/>
   * This method is a no-op if the content is already built.
   *
   * @param classLoaders  class loaders which are assumed to be able to build object of the target content class
   */
  public void deserializeData(@NotNull Collection<? extends ClassLoader> classLoaders) {
    if (data != null) {
      return;
    }
    if (rawData == null) {
      throw new IllegalStateException(String.format("Data node of key '%s' does not contain raw or prepared data", key));
    }
    if (rawData.length == 0) {
      return;
    }
    if (dataClassName == null) {
      throw new IllegalStateException(String.format("Data node of key '%s' does not contain data class name", key));
    }

    try {
      MultiLoaderWrapper classLoader = new MultiLoaderWrapper(getClass().getClassLoader(), classLoaders);
      //noinspection unchecked
      data = ObjectSerializer.getInstance().read((Class<T>)classLoader.findClass(dataClassName), rawData, SerializationKt.createDataNodeReadConfiguration(classLoader));
      assert data != null;
      clearRawData();
    }
    catch (Exception e) {
      throw new IllegalStateException("Can't deserialize target data of key '" + key + "'. " +
                                      "Given class loaders: " + classLoaders, e);
    }
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
      clearRawData();
      dataClassName = null;
    }
  }

  private void clearRawData() {
    rawData = null;
    dataClassName = null;
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

  public void serializeData() {
    if (rawData != null) {
      return;
    }

    if (data == null) {
      dataClassName = null;
      rawData = ArrayUtil.EMPTY_BYTE_ARRAY;
    }
    else {
      LOG.assertTrue(!(data instanceof Proxy));
      dataClassName = data.getClass().getName();
      rawData = ObjectSerializer.getInstance().writeAsBytes(data, SerializationKt.getDataNodeWriteConfiguration());
    }
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
    clearRawData();
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

  @NotNull
  public static <T> DataNode<T> nodeCopy(@NotNull DataNode<T> dataNode) {
    DataNode<T> copy = new DataNode<>(dataNode.key, dataNode.data, null);
    copy.dataClassName = dataNode.dataClassName;
    copy.rawData = dataNode.rawData;
    copy.ignored = dataNode.ignored;
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
}