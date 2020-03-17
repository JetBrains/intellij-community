// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.Nullable;

public class PluginUtilImpl implements PluginUtil {
  @Nullable
  @Override
  public PluginId getCallerPlugin(int stackFrameCount) {
    Class<?> aClass = ReflectionUtil.getCallerClass(stackFrameCount + 1);
    if (aClass == null) return null;
    ClassLoader classLoader = aClass.getClassLoader();
    return classLoader instanceof PluginClassLoader ? ((PluginClassLoader)classLoader).getPluginId() : null;
  }
}
