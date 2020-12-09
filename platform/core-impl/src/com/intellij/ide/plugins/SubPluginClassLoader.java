// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SubPluginClassLoader extends PluginClassLoader {
  private final String[] packagePrefixes;

  SubPluginClassLoader(@NotNull IdeaPluginDescriptorImpl pluginDescriptor,
                       @NotNull UrlClassLoader.Builder urlClassLoaderBuilder,
                       @NotNull ClassLoader @NotNull [] parents,
                       @NotNull String @NotNull [] packagePrefixes,
                       @NotNull ClassLoader coreLoader) {
    super(urlClassLoaderBuilder, parents, pluginDescriptor, pluginDescriptor.getPluginPath(), coreLoader, pluginDescriptor.packagePrefix);

    this.packagePrefixes = packagePrefixes;
  }

  @Override
  public @Nullable Class<?> loadClassInsideSelf(@NotNull String name, boolean forceLoadFromSubPluginClassloader) {
    if (forceLoadFromSubPluginClassloader) {
      return super.loadClassInsideSelf(name, true);
    }

    for (String packagePrefix : packagePrefixes) {
      if (name.startsWith(packagePrefix)) {
        return super.loadClassInsideSelf(name, true);
      }
    }

    int subIndex = name.indexOf('$');
    if (subIndex > 0) {
      // load inner classes
      // we check findLoadedClass because classNames doesn't have full set of suitable names - PluginAwareClassLoader.SubClassLoader is used to force loading classes from sub classloader
      Class<?> loadedClass = findLoadedClass(name.substring(0, subIndex));
      if (loadedClass != null && loadedClass.getClassLoader() == this) {
        return super.loadClassInsideSelf(name, true);
      }
    }

    return null;
  }
}
