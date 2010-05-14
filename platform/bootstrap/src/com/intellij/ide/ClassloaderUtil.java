/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.text.StringTokenizer;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

public class ClassloaderUtil {
  @NonNls static final String FILE_CACHE = "fileCache";
  @NonNls static final String URL_CACHE = "urlCache";// See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4167874
  @NonNls public static final String PROPERTY_IGNORE_CLASSPATH = "ignore.classpath";

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static final String ERROR = "Error";

  private ClassloaderUtil() {}

  public static void clearJarURLCache() {
    try {
      /*
      new URLConnection(null) {
        public void connect() throws IOException {
          throw new UnsupportedOperationException();
        }
      }.setDefaultUseCaches(false);
      */

      Class jarFileFactory = Class.forName("sun.net.www.protocol.jar.JarFileFactory");

      Field fileCache = jarFileFactory.getDeclaredField(FILE_CACHE);
      Field urlCache = jarFileFactory.getDeclaredField(URL_CACHE);

      fileCache.setAccessible(true);
      fileCache.set(null, new HashMap());

      urlCache.setAccessible(true);
      urlCache.set(null, new HashMap());
    }
    catch (Exception e) {
      System.out.println("Failed to clear URL cache");
      // Do nothing.
    }
  }

  public static Logger getLogger() {
    return Logger.getInstance("ClassloaderUtil");
  }

  public static UrlClassLoader initClassloader(final List<URL> classpathElements) {
    PathManager.loadProperties();

    try {
      addParentClasspath(classpathElements);
      addIDEALibraries(classpathElements);
      addAdditionalClassPath(classpathElements);
    }
    catch (IllegalArgumentException e) {
      if (Main.isHeadless()) {
        getLogger().error(e);
      } else {
        JOptionPane
          .showMessageDialog(JOptionPane.getRootFrame(), e.getMessage(), ERROR, JOptionPane.INFORMATION_MESSAGE);
      }
      System.exit(1);
    }
    catch (MalformedURLException e) {
      if (Main.isHeadless()) {
        getLogger().error(e.getMessage());
      } else {
        JOptionPane
          .showMessageDialog(JOptionPane.getRootFrame(), e.getMessage(), ERROR, JOptionPane.INFORMATION_MESSAGE);
      }
      System.exit(1);
    }

    filterClassPath(classpathElements);

    UrlClassLoader newClassLoader = null;
    try {
      newClassLoader = new UrlClassLoader(classpathElements, null, true, true);

      // prepare plugins
      if (!isLoadingOfExternalPluginsDisabled()) {
        try {
          StartupActionScriptManager.executeActionScript();
        }
        catch (IOException e) {
          final String errorMessage = "Error executing plugin installation script: " + e.getMessage();
          if (Main.isHeadless()) {
            System.out.println(errorMessage);
          } else {
            JOptionPane
              .showMessageDialog(JOptionPane.getRootFrame(), errorMessage, ERROR, JOptionPane.INFORMATION_MESSAGE);
          }
        }
      }

      Thread.currentThread().setContextClassLoader(newClassLoader);

    }
    catch (Exception e) {
      Logger logger = getLogger();
      if (logger == null) {
        e.printStackTrace(System.err);
      }
      else {
        logger.error(e);
      }
    }
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
      aClasspathElements.addAll(Arrays.asList(urlClassLoader.getURLs()));
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
        if (!isJarOrZip(file)) {
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

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static boolean isJarOrZip(File file) {
    if (file.isDirectory()) {
      return false;
    }
    final String name = file.getName();
    return StringUtil.endsWithIgnoreCase(name, ".jar") || StringUtil.endsWithIgnoreCase(name, ".zip");
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

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static boolean isLoadingOfExternalPluginsDisabled() {
    return !"true".equalsIgnoreCase(System.getProperty("idea.plugins.load", "true"));
  }
}
