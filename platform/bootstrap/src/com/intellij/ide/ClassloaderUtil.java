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

/*
 * @author max
 */
package com.intellij.ide;

import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.idea.Main;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.text.StringTokenizer;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

public class ClassloaderUtil extends ClassUtilCore {
  @NonNls public static final String PROPERTY_IGNORE_CLASSPATH = "ignore.classpath";

  private ClassloaderUtil() {}

  public static Logger getLogger() {
    return Logger.getInstance("ClassloaderUtil");
  }

  public static UrlClassLoader initClassloader(final List<URL> classpathElements, boolean updatePlugins) throws Exception {
    PathManager.loadProperties();

    addParentClasspath(classpathElements);
    addIDEALibraries(classpathElements);
    addAdditionalClassPath(classpathElements);

    filterClassPath(classpathElements);
    UrlClassLoader newClassLoader = new UrlClassLoader(classpathElements, null, true, true);

    // prepare plugins
    if (updatePlugins && !isLoadingOfExternalPluginsDisabled()) {
      try {
        StartupActionScriptManager.executeActionScript();
      }
      catch (IOException e) {
        Main.showMessage("Plugin Installation Error", e);
      }
    }

    Thread.currentThread().setContextClassLoader(newClassLoader);
    return newClassLoader;
  }

  public static void filterClassPath(final List<URL> classpathElements) {
    final String ignoreProperty = System.getProperty(PROPERTY_IGNORE_CLASSPATH);
    if (ignoreProperty == null) return;

    final Pattern pattern = Pattern.compile(ignoreProperty);

    for (Iterator<URL> i = classpathElements.iterator(); i.hasNext();) {
      URL url = i.next();
      final String u = url.toExternalForm();
      if (pattern.matcher(u).matches()) {
        i.remove();
      }
    }
  }

  public static void addParentClasspath(List<URL> aClasspathElements) throws MalformedURLException {
    final ClassLoader loader = ClassloaderUtil.class.getClassLoader();
    if (loader instanceof URLClassLoader) {
      URLClassLoader urlClassLoader = (URLClassLoader)loader;
      ContainerUtil.addAll(aClasspathElements, urlClassLoader.getURLs());
    }
    else {
      try {
        Class antClassLoaderClass = Class.forName("org.apache.tools.ant.AntClassLoader");
        if (antClassLoaderClass.isInstance(loader) ||
            loader.getClass().getName().equals("org.apache.tools.ant.AntClassLoader") ||
            loader.getClass().getName().equals("org.apache.tools.ant.loader.AntClassLoader2")) {
          //noinspection HardCodedStringLiteral
          final String classpath =
            (String)antClassLoaderClass.getDeclaredMethod("getClasspath", ArrayUtil.EMPTY_CLASS_ARRAY).invoke(loader, ArrayUtil.EMPTY_OBJECT_ARRAY);
          final StringTokenizer tokenizer = new StringTokenizer(classpath, File.separator, false);
          while (tokenizer.hasMoreTokens()) {
            final String token = tokenizer.nextToken();
            aClasspathElements.add(new File(token).toURI().toURL());
          }
        }
        else {
          getLogger().warn("Unknown classloader: " + loader.getClass().getName());
        }
      }
      catch (ClassCastException e) {
        getLogger().warn("Unknown classloader [CCE]: " + e.getMessage());
      }
      catch (ClassNotFoundException e) {
        getLogger().warn("Unknown classloader [CNFE]: " + loader.getClass().getName());
      }
      catch (NoSuchMethodException e) {
        getLogger().warn("Unknown classloader [NSME]: " + e.getMessage());
      }
      catch (IllegalAccessException e) {
        getLogger().warn("Unknown classloader [IAE]: " + e.getMessage());
      }
      catch (InvocationTargetException e) {
        getLogger().warn("Unknown classloader [ITE]: " + e.getMessage());
      }
    }
  }

  public static void addIDEALibraries(List<URL> classpathElements) {
    final String ideaHomePath = PathManager.getHomePath();
    addAllFromLibFolder(ideaHomePath, classpathElements);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static void addAllFromLibFolder(final String aFolderPath, List<URL> classPath) {
    try {
      final Class<ClassloaderUtil> aClass = ClassloaderUtil.class;
      final String selfRoot = PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");

      final URL selfRootUrl = new File(selfRoot).getAbsoluteFile().toURI().toURL();
      classPath.add(selfRootUrl);

      final File libFolder = new File(aFolderPath + File.separator + "lib");
      addLibraries(classPath, libFolder, selfRootUrl);

      final File extLib = new File(libFolder, "ext");
      addLibraries(classPath, extLib, selfRootUrl);

      final File antLib = new File(new File(libFolder, "ant"), "lib");
      addLibraries(classPath, antLib, selfRootUrl);
    }
    catch (MalformedURLException e) {
      getLogger().error(e);
    }
  }

  public static void addLibraries(List<URL> classPath, File fromDir, final URL selfRootUrl) throws MalformedURLException {
    final File[] files = fromDir.listFiles();
    if (files != null) {
      for (final File file : files) {
        if (!FileUtil.isJarOrZip(file)) {
          continue;
        }
        final URL url = file.toURI().toURL();
        if (selfRootUrl.equals(url)) {
          continue;
        }
        classPath.add(url);
      }
    }
  }

  public static void addAdditionalClassPath(List<URL> classPath) {
    try {
      //noinspection HardCodedStringLiteral
      final StringTokenizer tokenizer = new StringTokenizer(System.getProperty("idea.additional.classpath", ""), File.pathSeparator, false);
      while (tokenizer.hasMoreTokens()) {
        String pathItem = tokenizer.nextToken();
        classPath.add(new File(pathItem).toURI().toURL());
      }
    }
    catch (MalformedURLException e) {
      getLogger().error(e);
    }
  }
}
