// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.build;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.plugin.JpsPluginManager;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.ServiceLoader;

public class StandaloneJpsPluginManager extends JpsPluginManager {
  private final URLClassLoader myPluginsClassloader;
  private final List<Path> myPluginFiles;

  public StandaloneJpsPluginManager(@NotNull List<Path> additionalPluginFiles) {
    URL[] classloaderUrls = additionalPluginFiles.stream().map(path -> {
      try {
        return path.toUri().toURL();
      }
      catch (MalformedURLException e) {
        throw new RuntimeException(e);
      }
    }).toArray(URL[]::new);

    myPluginFiles = additionalPluginFiles;
    myPluginsClassloader = new URLClassLoader(classloaderUrls, getClass().getClassLoader());
  }

  @NotNull
  public List<Path> getAdditionalPluginFiles() {
    return myPluginFiles;
  }

  @NotNull
  @Override
  public <T> Collection<T> loadExtensions(@NotNull Class<T> extensionClass) {
    if (myPluginsClassloader == null) {
      throw new IllegalStateException("Plugins classpath was not initialized. Please call " + getClass().getName() + ".setAdditionalPluginFiles (" + this + ")");
    }

    ServiceLoader<T> loader = ServiceLoader.load(extensionClass, myPluginsClassloader);
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
