// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.plugin;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.ServiceLoader;

final class SingleClassLoaderPluginManager extends JpsPluginManager {
  @NotNull
  @Override
  public <T> Collection<T> loadExtensions(@NotNull Class<T> extensionClass) {
    ServiceLoader<T> loader = ServiceLoader.load(extensionClass, extensionClass.getClassLoader());
    return ContainerUtil.newArrayList(loader);
  }

  @Override
  public boolean isFullyLoaded() {
    return true;
  }

  @Override
  public int getModificationStamp() {
    return 0;
  }
}
