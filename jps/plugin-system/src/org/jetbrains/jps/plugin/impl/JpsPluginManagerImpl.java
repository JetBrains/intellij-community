package org.jetbrains.jps.plugin.impl;

import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.plugin.JpsPluginManager;

import java.util.Collection;
import java.util.ServiceLoader;

/**
 * @author nik
 */
public class JpsPluginManagerImpl extends JpsPluginManager {
  @NotNull
  @Override
  public <T> Collection<T> loadExtensions(@NotNull Class<T> extensionClass) {
    ServiceLoader<T> loader = ServiceLoader.load(extensionClass, extensionClass.getClassLoader());
    return ContainerUtilRt.newArrayList(loader);
  }
}
