/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.externalSystem.model;

import org.nustaq.serialization.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

/**
 * Uses FST based serialization
 * @param <T>
 */
public class FSTSerializer<T> implements DataNodeSerializer<T> {
  private static DataNodeSerializer ourInstance = new FSTSerializer();
  private final FSTConfiguration myConf = FSTConfiguration.createDefaultConfiguration();

  public FSTSerializer() {
    myConf.registerSerializer(Proxy.class, new FSTProxySerializer(), true);
    FSTConfiguration.LastResortClassResolver prevResolver = myConf.getLastResortResolver();
    myConf.setLastResortResolver(p -> p.startsWith("com.sun.proxy.$Proxy") ? Proxy.class : (prevResolver != null ? prevResolver.getClass(p) : null) );
  }

  @Override
  public byte[] getBytes(T data) throws IOException {
    ByteArrayOutputStream bOut = new ByteArrayOutputStream();

    try(FSTObjectOutput oOut = new FSTObjectOutput(bOut, myConf)) {
      oOut.writeObject(data);
      oOut.flush();
      return bOut.toByteArray();
    }
  }

  @Override
  public T readData(byte[] data, ClassLoader... classLoaders) throws IOException, ClassNotFoundException {
    final InputStream inputStream = new ByteArrayInputStream(data);

    ClassLoader original = null;
    FSTObjectInput oIn = new FSTObjectInput(inputStream, myConf);
    try {
      original = myConf.getClassLoader();
      myConf.setClassLoader(new MultiLoaderWrapper(getClass().getClassLoader(), classLoaders));
      @SuppressWarnings("unchecked") T object = (T) oIn.readObject();
      assert object != null;
      return object;
    } finally {
      myConf.setClassLoader(original);
      oIn.close();
    }
  }

  public static <T> DataNodeSerializer<T> getInstance() {
    return ourInstance;
  }
}

class MultiLoaderWrapper extends ClassLoader {
  private ClassLoader[] myDelegates;
  public MultiLoaderWrapper(ClassLoader parentCl, ClassLoader[] delegates) {
    super(parentCl);
    myDelegates = delegates;
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    for (ClassLoader delegate : myDelegates) {
      try {
        return Class.forName(name, false, delegate);
      } catch (ClassNotFoundException e) {
        // try next one
      }
    }

    throw new ClassNotFoundException(name);
  }
}


class FSTProxySerializer extends FSTBasicObjectSerializer {
  @Override
  public void writeObject(FSTObjectOutput out, Object toWrite, FSTClazzInfo clzInfo, FSTClazzInfo.FSTFieldInfo referencedBy, int streamPosition) throws IOException {
    Class<?>[] ifaces = clzInfo.getClazz().getInterfaces();
    out.writeInt(ifaces.length);
    for (Class i : ifaces) {
      out.writeUTF(i.getName());
    }
    out.writeObject(Proxy.getInvocationHandler(toWrite));
  }

  @Override
  public Object instantiate(Class objectClass, FSTObjectInput in, FSTClazzInfo serializationInfo, FSTClazzInfo.FSTFieldInfo referencee, int streamPosition) throws IOException, ClassNotFoundException {
    ClassLoader cl = in.getConf().getClassLoader();
    int numIfaces = in.readInt();
    String[] interfaces = new String[numIfaces];
    for (int i = 0; i < numIfaces; i++) {
      interfaces[i] = in.readUTF();
    }
    Class[] classObjs = new Class[interfaces.length];

    for(int i = 0; i < interfaces.length; ++i) {
      try {
        classObjs[i] = Class.forName(interfaces[i], false, cl);
      } catch (ClassNotFoundException e) {
        classObjs[i] = Class.forName(interfaces[i], false, this.getClass().getClassLoader());
      }
    }
    InvocationHandler ih = (InvocationHandler)in.readObject();
    Object res = Proxy.newProxyInstance(cl, classObjs, ih);
    in.registerObject(res, streamPosition, serializationInfo, referencee);
    return res;
  }
}
