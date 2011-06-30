/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.intellij.ide.plugins.cl;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.Nullable;
import sun.misc.CompoundEnumeration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 * Date: Mar 6, 2003
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr"})
public class PluginClassLoader extends UrlClassLoader {
  private final ClassLoader[] myParents;
  private final PluginId myPluginId;
  private final File myLibDirectory;

  public PluginClassLoader(List<URL> urls, ClassLoader[] parents, PluginId pluginId, File pluginRoot) {
    super(urls, null, true, true);
    myParents = parents;
    myPluginId = pluginId;

    //noinspection HardCodedStringLiteral
    final File file = new File(pluginRoot, "lib");
    myLibDirectory = file.exists()? file : null;
  }

  // changed sequence in which classes are searched, this is essential if plugin uses library, a different version of which
  // is used in IDEA.
  public Class loadClass(final String name, final boolean resolve) throws ClassNotFoundException{
    Class c = loadClassInsideSelf(name);

    if (c == null) {
      c = loadClassFromParents(name);
    }

    if (c != null) {
      if (resolve) {
        resolveClass(c);
      }
      return c;
    }
    PluginManager.addPluginClass(name, myPluginId);
    throw new ClassNotFoundException(name + " [" + myPluginId + "]");
  }

  @Nullable
  private Class loadClassFromParents(final String name) {
    for (ClassLoader parent : myParents) {
      try {
        return parent.loadClass(name);
      }
      catch (ClassNotFoundException ignoreAndContinue) {
        // Ignore and continue
      }
    }

    return null;
  }

  @Nullable
  private synchronized Class loadClassInsideSelf(final String name) {
    Class c = findLoadedClass(name);
    if (c != null) {
      return c;
    }

    c = _findClass(name);
    if (c != null) {
      PluginManager.addPluginClass(c.getName(), myPluginId);
    }

    return c;
  }

  public URL findResource(final String name) {
    final long started = myDebugTime ? System.nanoTime():0;
    
    try {
      final URL resource = findResourceImpl(name);
      if (resource != null) {
        return resource;
      }

      for (ClassLoader parent : myParents) {
        final URL parentResource = fetchResource(parent, name);
        if (parentResource != null) {
          return parentResource;
        }
      }
      return null;
    }
    finally {
      long doneFor = myDebugTime ? (System.nanoTime() - started):0;
      if (doneFor > NS_THRESHOLD) {
        System.out.println((doneFor / 1000000) + " ms for " + (myPluginId != null?myPluginId.getIdString():null)+ ", resource:"+name);
      }
    }
  }

  @Nullable
  @Override
  public InputStream getResourceAsStream(final String name) {
    final long started = myDebugTime ? System.nanoTime():0;

    try {
      final InputStream stream = super.getResourceAsStream(name);
      if (stream != null) return stream;

      for (ClassLoader parent : myParents) {
        final InputStream inputStream = parent.getResourceAsStream(name);
        if (inputStream != null) return inputStream;
      }

      return null;
    }
    finally {
      long doneFor = myDebugTime ? System.nanoTime() - started:0;
      if (doneFor > NS_THRESHOLD) {
        System.out.println((doneFor/1000000) + " ms for " + (myPluginId != null?myPluginId.getIdString():null)+ ", resource as stream:"+name);
      }
    }
  }

  public Enumeration<URL> findResources(final String name) throws IOException {
    final long started = myDebugTime ? System.nanoTime() : 0;
    try {
      final Enumeration[] resources = new Enumeration[myParents.length + 1];
      resources[0] = super.findResources(name);
      for (int idx = 0; idx < myParents.length; idx++) {
        resources[idx + 1] = fetchResources(myParents[idx], name);
      }
      return new CompoundEnumeration<URL>(resources);
    }
    finally {
      long doneFor = myDebugTime ? System.nanoTime() - started:0;
      if (doneFor > NS_THRESHOLD) {
        System.out.println((doneFor / 1000000) + " ms for " + (myPluginId != null?myPluginId.getIdString():null)+ ", find resources:"+name);
      }
    }
  }

  protected String findLibrary(String libName) {
    if (myLibDirectory == null) {
      return null;
    }
    final File libraryFile = new File(myLibDirectory, System.mapLibraryName(libName));
    return libraryFile.exists()? libraryFile.getAbsolutePath() : null;
  }


  private static URL fetchResource(ClassLoader cl, String resourceName) {
    //protected URL findResource(String s)
    try {
      //noinspection HardCodedStringLiteral
      final Method findResourceMethod = getFindResourceMethod(cl.getClass(), "findResource");
      return (URL)findResourceMethod.invoke(cl, resourceName);
    }
    catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  private static Enumeration fetchResources(ClassLoader cl, String resourceName) {
    //protected Enumeration findResources(String s) throws IOException
    try {
      //noinspection HardCodedStringLiteral
      final Method findResourceMethod = getFindResourceMethod(cl.getClass(), "findResources");
      if (findResourceMethod == null) {
        return null;
      }
      return (Enumeration)findResourceMethod.invoke(cl, resourceName);
    }
    catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  private static Method getFindResourceMethod(final Class clClass, final String methodName) {
    try {
      final Method declaredMethod = clClass.getDeclaredMethod(methodName, String.class);
      declaredMethod.setAccessible(true);
      return declaredMethod;
    }
    catch (NoSuchMethodException e) {
      final Class superclass = clClass.getSuperclass();
      if (superclass == null || superclass.equals(Object.class)) {
        return null;
      }
      return getFindResourceMethod(superclass, methodName);
    }
  }

  public PluginId getPluginId() {
    return myPluginId;
  }

  public String toString() {
    return "PluginClassloader[" + myPluginId + "]";
  }
}
