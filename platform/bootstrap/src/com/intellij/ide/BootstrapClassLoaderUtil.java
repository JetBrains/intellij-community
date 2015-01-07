/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author max
 */
public class BootstrapClassLoaderUtil extends ClassUtilCore {
  private static final String PROPERTY_IGNORE_CLASSPATH = "ignore.classpath";
  private static final String PROPERTY_ALLOW_BOOTSTRAP_RESOURCES = "idea.allow.bootstrap.resources";
  private static final String PROPERTY_ADDITIONAL_CLASSPATH = "idea.additional.classpath";

  private BootstrapClassLoaderUtil() { }

  private static Logger getLogger() {
    return Logger.getInstance(BootstrapClassLoaderUtil.class);
  }

  @NotNull
  public static ClassLoader initClassLoader(boolean updatePlugins) throws MalformedURLException {
    PathManager.loadProperties();

    List<URL> classpath = new ArrayList<URL>();
    addParentClasspath(classpath);
    addIDEALibraries(classpath);
    addAdditionalClassPath(classpath);

    UrlClassLoader.Builder builder = UrlClassLoader.build()
      .urls(filterClassPath(classpath))
      .allowLock()
      .useCache();
    if (Boolean.valueOf(System.getProperty(PROPERTY_ALLOW_BOOTSTRAP_RESOURCES, "true"))) {
      builder.allowBootstrapResources();
    }

    UrlClassLoader newClassLoader = builder.get();

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

  private static List<URL> filterClassPath(List<URL> classpathElements) {
    String ignoreProperty = System.getProperty(PROPERTY_IGNORE_CLASSPATH);
    if (ignoreProperty != null) {
      Pattern pattern = Pattern.compile(ignoreProperty);
      for (Iterator<URL> i = classpathElements.iterator(); i.hasNext(); ) {
        String url = i.next().toExternalForm();
        if (pattern.matcher(url).matches()) {
          i.remove();
        }
      }
    }
    return classpathElements;
  }

  private static void addParentClasspath(List<URL> classpathElements) throws MalformedURLException {
    ClassLoader loader = BootstrapClassLoaderUtil.class.getClassLoader();
    if (loader instanceof URLClassLoader) {
      ContainerUtil.addAll(classpathElements, ((URLClassLoader)loader).getURLs());
    }
    else {
      String loaderName = loader.getClass().getName();
      try {
        Class<?> antClassLoaderClass = Class.forName("org.apache.tools.ant.AntClassLoader");
        if (antClassLoaderClass.isInstance(loader) ||
            "org.apache.tools.ant.AntClassLoader".equals(loaderName) ||
            "org.apache.tools.ant.loader.AntClassLoader2".equals(loaderName)) {
          String classpath = (String)antClassLoaderClass
            .getDeclaredMethod("getClasspath", ArrayUtil.EMPTY_CLASS_ARRAY)
            .invoke(loader, ArrayUtil.EMPTY_OBJECT_ARRAY);
          StringTokenizer tokenizer = new StringTokenizer(classpath, File.separator, false);
          while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            classpathElements.add(new File(token).toURI().toURL());
          }
        }
        else {
          getLogger().warn("Unknown class loader: " + loaderName);
        }
      }
      catch (ClassCastException e) { getLogger().warn("Unknown class loader '" + loaderName + "'", e); }
      catch (ClassNotFoundException e) { getLogger().warn("Unknown class loader '" + loaderName + "'", e); }
      catch (NoSuchMethodException e) { getLogger().warn("Unknown class loader '" + loaderName + "'", e); }
      catch (IllegalAccessException e) { getLogger().warn("Unknown class loader '" + loaderName + "'", e); }
      catch (InvocationTargetException e) { getLogger().warn("Unknown class loader '" + loaderName + "'", e); }
    }
  }

  private static void addIDEALibraries(List<URL> classpathElements) throws MalformedURLException {
    Class<BootstrapClassLoaderUtil> aClass = BootstrapClassLoaderUtil.class;
    String selfRoot = PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
    assert selfRoot != null;
    URL selfRootUrl = new File(selfRoot).getAbsoluteFile().toURI().toURL();
    classpathElements.add(selfRootUrl);

    File libFolder = new File(PathManager.getLibPath());
    addLibraries(classpathElements, libFolder, selfRootUrl);
    addLibraries(classpathElements, new File(libFolder, "ext"), selfRootUrl);
    addLibraries(classpathElements, new File(libFolder, "ant/lib"), selfRootUrl);
  }

  private static void addLibraries(List<URL> classPath, File fromDir, URL selfRootUrl) throws MalformedURLException {
    File[] files = fromDir.listFiles();
    if (files == null) return;

    for (File file : files) {
      if (FileUtil.isJarOrZip(file)) {
        URL url = file.toURI().toURL();
        if (!selfRootUrl.equals(url)) {
          classPath.add(url);
        }
      }
    }
  }

  private static void addAdditionalClassPath(List<URL> classPath) {
    try {
      StringTokenizer tokenizer = new StringTokenizer(System.getProperty(PROPERTY_ADDITIONAL_CLASSPATH, ""), File.pathSeparator, false);
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
