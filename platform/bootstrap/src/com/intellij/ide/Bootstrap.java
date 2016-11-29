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

import com.intellij.idea.Main;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * @author max
 */
public class Bootstrap {
  private static final String PLUGIN_MANAGER = "com.intellij.ide.plugins.PluginManager";

  private Bootstrap() { }

  public static void main(String[] args, String mainClass, String methodName) throws Exception {
    main(args, mainClass, methodName, Collections.<String>emptyList(), null);
  }

  public static void main(String[] args, String mainClass, String methodName, List<String> classpath, ClassLoader parent) throws Exception {
    ClassLoader newClassLoader = BootstrapClassLoaderUtil.initClassLoader(parent, classpath, !Main.isCommandLine());
    WindowsCommandLineProcessor.ourMirrorClass = Class.forName(WindowsCommandLineProcessor.class.getName(), true, newClassLoader);

    Class<?> klass = Class.forName(PLUGIN_MANAGER, true, newClassLoader);
    Method startMethod = klass.getDeclaredMethod("start", String.class, String.class, String[].class);
    startMethod.setAccessible(true);
    startMethod.invoke(null, mainClass, methodName, args);
  }
}
