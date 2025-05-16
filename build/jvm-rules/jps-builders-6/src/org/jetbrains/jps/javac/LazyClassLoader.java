// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.util.Iterators;
import org.jetbrains.jps.util.Iterators.Function;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;

class LazyClassLoader extends ClassLoader implements Closeable {
  private static final URL[] EMPTY_URL_ARRAY = new URL[0];
  private final ClassLoader myParent;
  private final Iterable<URL> myUrls;
  private volatile DelegateClassLoader myDelegate;

  LazyClassLoader(Iterable<URL> urls, ClassLoader parent) {
    super(null);
    myParent = parent;
    myUrls = urls;
  }

  @Nullable
  static LazyClassLoader createFrom(@Nullable Iterable<? extends File> files, ClassLoader parent) {
    return Iterators.isEmptyCollection(files)? null : new LazyClassLoader(Iterators.map(files, new Function<File, URL>() {
      @Override
      public URL fun(File f) {
        try {
          return f.toURI().toURL();
        }
        catch (MalformedURLException e) {
          throw new AssertionError(e);
        }
      }
    }), parent);
  }

  private DelegateClassLoader getDelegate() {
    DelegateClassLoader delegate = myDelegate;
    if (delegate == null) {
      synchronized (myUrls) {
        delegate = myDelegate;
        if (delegate == null) {
          myDelegate = delegate = new DelegateClassLoader(Iterators.collect(myUrls, new ArrayList<URL>()).toArray(EMPTY_URL_ARRAY), myParent);
        }
      }
    }
    return delegate;
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    return getDelegate().loadClass(name);
  }

  @Override
  protected URL findResource(String name) {
    return getDelegate().findResource(name);
  }

  @Override
  protected Enumeration<URL> findResources(String name) throws IOException {
    return getDelegate().findResources(name);
  }

  @Override
  public void close() throws IOException {
    final ClassLoader delegate = myDelegate;
    if (delegate instanceof Closeable) {
      ((Closeable)delegate).close();
      myDelegate = null;
    }
  }

  private static class DelegateClassLoader extends URLClassLoader {
    DelegateClassLoader(URL[] urls, final ClassLoader parent) {
      super(urls, parent);
    }
  }
}
