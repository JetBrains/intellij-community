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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
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
public class DataNode<T> implements Serializable, UserDataHolderEx {

  private static final long serialVersionUID = 1L;
  private static final Logger LOG = Logger.getInstance(DataNode.class);
  private static final boolean AT_LEAST_JAVA_9 = SystemInfo.isJavaVersionAtLeast("9");

  @NotNull private final List<DataNode<?>> myChildren = ContainerUtilRt.newArrayList();
  @NotNull private transient List<DataNode<?>> myChildrenView = Collections.unmodifiableList(myChildren);
  @NotNull private transient UserDataHolderBase myUserData = new UserDataHolderBase();

  @NotNull private final Key<T> myKey;
  private transient T myData;
  private byte[] myRawData;
  private boolean myIgnored;

  @Nullable private DataNode<?> myParent;

  public DataNode(@NotNull Key<T> key, @NotNull T data, @Nullable DataNode<?> parent) {
    myKey = key;
    myData = data;
    myParent = parent;
  }

  private DataNode(@NotNull Key<T> key) {
    myKey = key;
  }

  @Nullable
  public DataNode<?> getParent() {
    return myParent;
  }

  @NotNull
  public <T> DataNode<T> createChild(@NotNull Key<T> key, @NotNull T data) {
    DataNode<T> result = new DataNode<>(key, data, this);
    myChildren.add(result);
    return result;
  }

  @NotNull
  public Key<T> getKey() {
    return myKey;
  }

  @NotNull
  public T getData() {
    if (myData == null) {
      prepareData(getClass().getClassLoader(), Thread.currentThread().getContextClassLoader());
    }
    return myData;
  }

  public boolean isIgnored() {
    return myIgnored;
  }

  public void setIgnored(boolean ignored) {
    myIgnored = ignored;
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
   * @param loaders  class loaders which are assumed to be able to build object of the target content class
   */
  @SuppressWarnings({"unchecked", "IOResourceOpenedButNotSafelyClosed"})
  public void prepareData(@NotNull final ClassLoader ... loaders) {
    if (myData != null) {
      return;
    }

    try {
      myData = getSerializer().readData(myRawData, loaders);
      assert myData != null;
      myRawData = null;
    } catch (IOException|ClassNotFoundException e) {
      throw new IllegalStateException(
            String.format("Can't deserialize target data of key '%s'. Given class loaders: %s", myKey, Arrays.toString(loaders)),
            e
          );
    }
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

  @SuppressWarnings("unchecked")
  @Nullable
  public <P> DataNode<P> getParent(@NotNull Class<P> dataClass) {
    if (dataClass.isInstance(myData)) {
      return (DataNode<P>)this;
    }
    for (DataNode<?> p = myParent; p != null; p = p.myParent) {
      if (dataClass.isInstance(p.myData)) {
        return (DataNode<P>)p;
      }
    }
    return null;
  }

  public void addChild(@NotNull DataNode<?> child) {
    child.myParent = this;
    myChildren.add(child);
  }

  @NotNull
  public Collection<DataNode<?>> getChildren() {
    return myChildrenView;
  }

  private void writeObject(ObjectOutputStream out) throws IOException {
    try {
      myRawData = getDataBytes();
    }
    catch (IOException e) {
      LOG.warn("Unable to serialize the data node - " + toString());
      throw e;
    }
    out.defaultWriteObject();
  }

  private void readObject(ObjectInputStream in)
    throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    myChildrenView = Collections.unmodifiableList(myChildren);
    myUserData = new UserDataHolderBase();
  }

  public void checkIsSerializable() throws IOException {
    if (myRawData != null) return;
    ObjectOutputStream oOut = new ObjectOutputStream(NoopOutputStream.getInstance());
    try {
      oOut.writeObject(myData);
    }
    finally {
      oOut.close();
    }
  }

  public byte[] getDataBytes() throws IOException {
    if (myRawData != null) return myRawData;
    return getSerializer().getBytes(myData);
  }

  @Override
  public int hashCode() {
    // We can't use myChildren.hashCode() because it iterates whole subtree. This should not produce many collisions because 'getData()'
    // usually refers to different objects
    return 31 * myKey.hashCode() + getData().hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DataNode node = (DataNode)o;

    if (!myChildren.equals(node.myChildren)) return false;
    if (!getData().equals(node.getData())) return false;
    if (!myKey.equals(node.myKey)) return false;

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
    return String.format("%s: %s", myKey, dataDescription);
  }

  public void clear(boolean removeFromGraph) {
    if (removeFromGraph && myParent != null) {
      for (Iterator<DataNode<?>> iterator = myParent.myChildren.iterator(); iterator.hasNext(); ) {
        DataNode<?> dataNode = iterator.next();
        if (System.identityHashCode(dataNode) == System.identityHashCode(this)) {
          iterator.remove();
          break;
        }
      }
    }
    myParent = null;
    myRawData = null;
    myChildren.clear();
  }

  private DataNodeSerializer<T> getSerializer() {
    switch (Registry.stringValue("ext.project.data.serializer")) {
      case "auto":
        if (AT_LEAST_JAVA_9) {
          return JDKSerializer.getInstance();
        } else {
          return FSTSerializer.getInstance();
        }
      case "jdk":
        return JDKSerializer.getInstance();
      case "fst":
        return FSTSerializer.getInstance();
    }
    return JDKSerializer.getInstance();
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
    return (U)myUserData.getUserData(key);
  }

  @Override
  public <U> void putUserData(@NotNull com.intellij.openapi.util.Key<U> key, U value) {
    myUserData.putUserData(key, value);
  }

  public <U> void removeUserData(@NotNull com.intellij.openapi.util.Key<U> key) {
    myUserData.putUserData(key, null);
  }

  @NotNull
  @Override
  public <T> T putUserDataIfAbsent(@NotNull com.intellij.openapi.util.Key<T> key, @NotNull T value) {
    return myUserData.putUserDataIfAbsent(key, value);
  }

  @Override
  public <T> boolean replace(@NotNull com.intellij.openapi.util.Key<T> key, @Nullable T oldValue, @Nullable T newValue) {
    return myUserData.replace(key, oldValue, newValue);
  }

  public <T> void putCopyableUserData(@NotNull com.intellij.openapi.util.Key<T> key, T value) {
    myUserData.putCopyableUserData(key, value);
  }

  public boolean isUserDataEmpty() {
    return myUserData.isUserDataEmpty();
  }

  public <T> T getCopyableUserData(@NotNull com.intellij.openapi.util.Key<T> key) {
    return myUserData.getCopyableUserData(key);
  }

  @NotNull
  public static <T> DataNode<T> nodeCopy(@NotNull DataNode<T> dataNode) {
    DataNode<T> copy = new DataNode<>(dataNode.myKey);
    copy.myData = dataNode.myData;
    copy.myRawData = dataNode.myRawData;
    copy.myIgnored = dataNode.myIgnored;
    dataNode.myUserData.copyCopyableDataTo(copy.myUserData);
    return copy;
  }

  @NotNull
  private static <T> DataNode<T> copy(@NotNull DataNode<T> dataNode, @Nullable DataNode<?> newParent) {
    DataNode<T> copy = nodeCopy(dataNode);
    copy.myParent = newParent;
    for (DataNode<?> child : dataNode.myChildren) {
      copy.addChild(copy(child, copy));
    }
    return copy;
  }

  private static class NoopOutputStream extends OutputStream {

    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    private static NoopOutputStream ourInstance = new NoopOutputStream();

    public static NoopOutputStream getInstance() {
      return ourInstance;
    }

    private NoopOutputStream() {}

    @Override
    public void write(int b) throws IOException {}
  }
}
