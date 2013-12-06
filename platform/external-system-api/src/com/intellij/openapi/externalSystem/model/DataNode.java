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

import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
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
  private transient T myData;
  private byte[] myRawData;

  @Nullable private final DataNode<?> myParent;

  public DataNode(@NotNull Key<T> key, @NotNull T data, @Nullable DataNode<?> parent) {
    myKey = key;
    myData = data;
    myParent = parent;
  }

  @Nullable
  public DataNode<?> getParent() {
    return myParent;
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
    if (myData == null) {
      prepareData(getClass().getClassLoader(), Thread.currentThread().getContextClassLoader());
    }
    return myData;
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
    ObjectInputStream oIn = null;
    try {
      oIn = new ObjectInputStream(new ByteArrayInputStream(myRawData)) {
        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
          String name = desc.getName();
          for (ClassLoader loader : loaders) {
            try {
              return Class.forName(name, false, loader);
            }
            catch (ClassNotFoundException e) {
              // Ignore
            }
          }
          return super.resolveClass(desc);
        }

        @Override
        protected Class<?> resolveProxyClass(String[] interfaces) throws IOException, ClassNotFoundException {
          for (ClassLoader loader : loaders) {
            try {
              return doResolveProxyClass(interfaces, loader);
            }
            catch (ClassNotFoundException e) {
              // Ignore
            }
          }
          return super.resolveProxyClass(interfaces);
        }
        
        private Class<?> doResolveProxyClass(@NotNull String[] interfaces, @NotNull ClassLoader loader) throws ClassNotFoundException {
          ClassLoader nonPublicLoader = null;
          boolean hasNonPublicInterface = false;

          // define proxy in class loader of non-public interface(s), if any
          Class[] classObjs = new Class[interfaces.length];
          for (int i = 0; i < interfaces.length; i++) {
            Class cl = Class.forName(interfaces[i], false, loader);
            if ((cl.getModifiers() & Modifier.PUBLIC) == 0) {
              if (hasNonPublicInterface) {
                if (nonPublicLoader != cl.getClassLoader()) {
                  throw new IllegalAccessError(
                    "conflicting non-public interface class loaders");
                }
              } else {
                nonPublicLoader = cl.getClassLoader();
                hasNonPublicInterface = true;
              }
            }
            classObjs[i] = cl;
          }
          try {
            return Proxy.getProxyClass(hasNonPublicInterface ? nonPublicLoader : loader, classObjs);
          }
          catch (IllegalArgumentException e) {
            throw new ClassNotFoundException(null, e);
          }
        }
      };
      myData = (T)oIn.readObject();
      myRawData = null;
    }
    catch (IOException e) {
      throw new IllegalStateException(
        String.format("Can't deserialize target data of key '%s'. Given class loaders: %s", myKey, Arrays.toString(loaders)),
        e
      );
    }
    catch (ClassNotFoundException e) {
      throw new IllegalStateException(
        String.format("Can't deserialize target data of key '%s'. Given class loaders: %s", myKey, Arrays.toString(loaders)),
        e
      );
    }
    finally {
      StreamUtil.closeStream(oIn);
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

  public void addChild(@NotNull DataNode<?> child) {
    myChildren.add(child);
  }

  @NotNull
  public Collection<DataNode<?>> getChildren() {
    return myChildren;
  }

  private void writeObject(ObjectOutputStream out) throws IOException {
    ByteArrayOutputStream bOut = new ByteArrayOutputStream();
    ObjectOutputStream oOut = new ObjectOutputStream(bOut);
    try {
      oOut.writeObject(myData);
    }
    finally {
      oOut.close();
    }
    myRawData = bOut.toByteArray();
    out.defaultWriteObject();
  }
  
  @Override
  public int hashCode() {
    int result = myChildren.hashCode();
    result = 31 * result + myKey.hashCode();
    result = 31 * result + getData().hashCode();
    return result;
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
    return String.format("%s: %s", myKey, getData());
  }
}
