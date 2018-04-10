// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.application.PathManager;

import java.lang.reflect.Method;

/**
 * @author max
 */
public class Bootstrap {
  private static final String PLUGIN_MANAGER = "com.intellij.ide.plugins.PluginManager";

  private Bootstrap() { }

  public static void main(String[] args, String mainClass, String methodName) throws Exception {
    PathManager.loadProperties();

    ClassLoader newClassLoader = BootstrapClassLoaderUtil.initClassLoader();
    Thread.currentThread().setContextClassLoader(newClassLoader);

    WindowsCommandLineProcessor.ourMirrorClass = Class.forName(WindowsCommandLineProcessor.class.getName(), true, newClassLoader);

    Class<?> klass = Class.forName(PLUGIN_MANAGER, true, newClassLoader);
    Method startMethod = klass.getDeclaredMethod("start", String.class, String.class, String[].class);
    startMethod.setAccessible(true);
    startMethod.invoke(null, mainClass, methodName, args);
  }
}