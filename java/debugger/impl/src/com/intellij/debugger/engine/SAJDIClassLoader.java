// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.*;

class SAJDIClassLoader extends URLClassLoader {
  SAJDIClassLoader(ClassLoader parent, String classPath) {
    super(new URL[0], parent);
    try {
      addURL(new File(classPath).toURI().toURL());
    }
    catch (MalformedURLException mue) {
      throw new RuntimeException(mue);
    }
  }

  @Override
  public synchronized Class loadClass(String name) throws ClassNotFoundException {
    Class c = findLoadedClass(name);
    if (c == null) {
      /* to avoid loading same native library multiple times
       *  from multiple class loaders (which results in getting a
       *  UnsatisifiedLinkageError from System.loadLibrary).
       */

      if (name.startsWith("sun.jvm.hotspot.") && !name.startsWith("sun.jvm.hotspot.debugger.")) {
        return findClass(name);
      }
      return super.loadClass(name);
    }
    return c;
  }

  @Nullable
  @Override
  public URL getResource(String name) {
    if ("sa.properties".equals(name)) {
      URL resource = findResource(name);
      if (resource != null) {
        return resource;
      }
    }
    return super.getResource(name);
  }
}
