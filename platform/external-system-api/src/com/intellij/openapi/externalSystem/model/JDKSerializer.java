/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.externalSystem.model;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

/**
 * Uses plain jdk serialization
 * @param <T>
 */
public class JDKSerializer<T> implements DataNodeSerializer<T> {
  private static DataNodeSerializer ourInstance = new JDKSerializer();

  @Override
  public byte[] getBytes(T data) throws IOException {
    ByteArrayOutputStream bOut = new ByteArrayOutputStream();
    ObjectOutputStream oOut = new ObjectOutputStream(bOut);
    try {
      oOut.writeObject(data);
      return bOut.toByteArray();
    }
    finally {
      oOut.close();
    }
  }

  @Override
  public T readData(byte[] data, ClassLoader... classLoaders) throws IOException, ClassNotFoundException {
    final InputStream inputStream = new ByteArrayInputStream(data);
    try(ObjectInputStream oIn = new MultiLoaderObjectInputStream(inputStream, classLoaders)) {
      @SuppressWarnings("unchecked") T object = (T) oIn.readObject();
      assert object != null;
      return object;
    }
  }

  public static <T> DataNodeSerializer<T> getInstance() {
    return ourInstance;
  }
}

class MultiLoaderObjectInputStream extends ObjectInputStream {
  private final ClassLoader[] myLoaders;

  public MultiLoaderObjectInputStream(InputStream inputStream, ClassLoader... loaders) throws IOException {
    super(inputStream);
    myLoaders = loaders;
  }

  @Override
  protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
    String name = desc.getName();
    for (ClassLoader loader : myLoaders) {
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
    for (ClassLoader loader : myLoaders) {
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
}
