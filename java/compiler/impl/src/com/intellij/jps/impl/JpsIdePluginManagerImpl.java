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
package com.intellij.jps.impl;

import com.intellij.compiler.server.CompileServerPlugin;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.plugin.JpsPluginManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author nik
 */
public class JpsIdePluginManagerImpl extends JpsPluginManager {
  private List<PluginDescriptor> myExternalBuildPlugins = new CopyOnWriteArrayList<PluginDescriptor>();

  public JpsIdePluginManagerImpl() {
    ExtensionPoint<CompileServerPlugin> extensionPoint = Extensions.getRootArea().getExtensionPoint(CompileServerPlugin.EP_NAME);
    extensionPoint.addExtensionPointListener(new ExtensionPointListener<CompileServerPlugin>() {
      @Override
      public void extensionAdded(@NotNull CompileServerPlugin extension, @Nullable PluginDescriptor pluginDescriptor) {
        if (pluginDescriptor != null) {
          myExternalBuildPlugins.add(pluginDescriptor);
        }
      }

      @Override
      public void extensionRemoved(@NotNull CompileServerPlugin extension, @Nullable PluginDescriptor pluginDescriptor) {
      }
    });
  }

  @NotNull
  @Override
  public <T> Collection<T> loadExtensions(@NotNull Class<T> extensionClass) {
    String resourceName = "META-INF/services/" + extensionClass.getName();
    Set<Class<T>> classes = new LinkedHashSet<Class<T>>();
    for (PluginDescriptor plugin : myExternalBuildPlugins) {
      ClassLoader loader = plugin.getPluginClassLoader();
      if (loader == null) continue;
      try {
        Enumeration<URL> resources = loader.getResources(resourceName);
        while (resources.hasMoreElements()) {
          loadImplementations(resources.nextElement(), loader, classes);
        }
      }
      catch (IOException e) {
        throw new ServiceConfigurationError("Cannot load configuration files for " + extensionClass.getName(), e);
      }
    }
    List<T> extensions = new ArrayList<T>();
    for (Class<T> aClass : classes) {
      try {
        extensions.add(extensionClass.cast(aClass.newInstance()));
      }
      catch (Exception e) {
        throw new ServiceConfigurationError("Class " + aClass.getName() + " cannot be instantiated", e);
      }
    }
    return extensions;
  }

  private static <T> void loadImplementations(URL url, ClassLoader loader, Set<Class<T>> result) throws IOException {
    for (String name : loadClassNames(url)) {
      try {
        //noinspection unchecked
        result.add((Class<T>)Class.forName(name, false, loader));
      }
      catch (ClassNotFoundException e) {
        throw new ServiceConfigurationError("Cannot find class " + name, e);
      }
    }
  }

  private static List<String> loadClassNames(URL url) throws IOException {
    List<String> result = new ArrayList<String>();
    BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream(), CharsetToolkit.UTF8));
    try {
      String line;
      while ((line = in.readLine()) != null) {
        int i = line.indexOf('#');
        if (i >= 0) line = line.substring(0, i);
        line = line.trim();
        if (!line.isEmpty()) {
          result.add(line);
        }
      }
    }
    finally {
      in.close();
    }
    return result;
  }
}
