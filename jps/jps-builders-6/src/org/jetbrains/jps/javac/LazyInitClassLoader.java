// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.javac;

import com.intellij.util.Function;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicBoolean;

class LazyInitClassLoader extends URLClassLoader {
  private static final URL[] EMPTY_URL_ARRAY = new URL[0];
  private static final AtomicBoolean myInitialized = new AtomicBoolean(false);
  private final Iterable<URL> myUrls;

  LazyInitClassLoader(Iterable<URL> urls, ClassLoader parent) {
    super(EMPTY_URL_ARRAY, parent);
    myUrls = urls;
  }

  @Nullable
  static LazyInitClassLoader createFrom(@Nullable Iterable<? extends File> files, ClassLoader parent) {
    return files == null? null : new LazyInitClassLoader(Iterators.map(files, new Function<File, URL>() {
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


  private void init() {
    if (!myInitialized.getAndSet(true)) {
      for (URL url : myUrls) {
        addURL(url);
      }
    }
  }

  @Override
  public URL[] getURLs() {
    init();
    return super.getURLs();
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    init();
    return super.findClass(name);
  }

  @Override
  public URL findResource(String name) {
    init();
    return super.findResource(name);
  }

  @Override
  public Enumeration<URL> findResources(String name) throws IOException {
    init();
    return super.findResources(name);
  }

}
