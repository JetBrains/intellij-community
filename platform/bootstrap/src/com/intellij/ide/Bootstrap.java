// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.application.PathManager;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;

/**
 * @author max
 */
public class Bootstrap {
  private static final String PLUGIN_MANAGER = "com.intellij.ide.plugins.PluginManager";

  private Bootstrap() { }

  public static void main(String[] args, String mainClass, String methodName, LinkedHashMap<String, Long> startupTimings) throws Exception {
    startupTimings.put("Loading properties", System.nanoTime());
    PathManager.loadProperties();

    startupTimings.put("Classloader init", System.nanoTime());
    ClassLoader newClassLoader = BootstrapClassLoaderUtil.initClassLoader();
    Thread.currentThread().setContextClassLoader(newClassLoader);

    startupTimings.put("WindowsCommandLineProcessor search", System.nanoTime());
    WindowsCommandLineProcessor.ourMirrorClass = Class.forName(WindowsCommandLineProcessor.class.getName(), true, newClassLoader);

    startupTimings.put("PluginManager search", System.nanoTime());
    Class<?> klass = Class.forName(PLUGIN_MANAGER, true, newClassLoader);
    Method startMethod = klass.getDeclaredMethod("start", String.class, String.class, String[].class, LinkedHashMap.class);
    startMethod.setAccessible(true);
    startMethod.invoke(null, mainClass, methodName, args, startupTimings);
  }
}