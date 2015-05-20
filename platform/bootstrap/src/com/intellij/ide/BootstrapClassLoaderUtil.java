/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.text.StringTokenizer;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
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

    Collection<URL> classpath = new LinkedHashSet<URL>();
    addParentClasspath(classpath);
    addIDEALibraries(classpath);
    addAdditionalClassPath(classpath);

    UrlClassLoader.Builder builder = UrlClassLoader.build()
      .urls(filterClassPath(new ArrayList<URL>(classpath)))
      .allowLock()
      .usePersistentClasspathIndexForLocalClassDirectories()
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

  private static void addParentClasspath(Collection<URL> classpath) throws MalformedURLException {
    List<URLClassLoader> loaders = new ArrayList<URLClassLoader>(2);
    for (ClassLoader loader = BootstrapClassLoaderUtil.class.getClassLoader(); loader != null; loader = loader.getParent()) {
      if (loader instanceof URLClassLoader) {
        loaders.add(0, (URLClassLoader)loader);
      }
      else {
        getLogger().warn("Unknown class loader: " + loader.getClass().getName());
      }
    }
    for (URLClassLoader loader : loaders) {
      ContainerUtil.addAll(classpath, loader.getURLs());
    }
  }

  private static void addIDEALibraries(Collection<URL> classpath) throws MalformedURLException {
    Class<BootstrapClassLoaderUtil> aClass = BootstrapClassLoaderUtil.class;
    String selfRoot = PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
    assert selfRoot != null;
    URL selfRootUrl = new File(selfRoot).getAbsoluteFile().toURI().toURL();
    classpath.add(selfRootUrl);

    File libFolder = new File(PathManager.getLibPath());
    addLibraries(classpath, libFolder, selfRootUrl);
    addLibraries(classpath, new File(libFolder, "ext"), selfRootUrl);
    addLibraries(classpath, new File(libFolder, "ant/lib"), selfRootUrl);
  }

  private static void addLibraries(Collection<URL> classPath, File fromDir, URL selfRootUrl) throws MalformedURLException {
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

  private static void addAdditionalClassPath(Collection<URL> classpath) {
    try {
      StringTokenizer tokenizer = new StringTokenizer(System.getProperty(PROPERTY_ADDITIONAL_CLASSPATH, ""), File.pathSeparator, false);
      while (tokenizer.hasMoreTokens()) {
        String pathItem = tokenizer.nextToken();
        classpath.add(new File(pathItem).toURI().toURL());
      }
    }
    catch (MalformedURLException e) {
      getLogger().error(e);
    }
  }

  private static List<URL> filterClassPath(List<URL> classpath) {
    String ignoreProperty = System.getProperty(PROPERTY_IGNORE_CLASSPATH);
    if (ignoreProperty != null) {
      Pattern pattern = Pattern.compile(ignoreProperty);
      for (Iterator<URL> i = classpath.iterator(); i.hasNext(); ) {
        String url = i.next().toExternalForm();
        if (pattern.matcher(url).matches()) {
          i.remove();
        }
      }
    }
    return classpath;
  }
}
