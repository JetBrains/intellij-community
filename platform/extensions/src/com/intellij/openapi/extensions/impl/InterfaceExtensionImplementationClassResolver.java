// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.PluginDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class InterfaceExtensionImplementationClassResolver implements ImplementationClassResolver {
  static final ImplementationClassResolver INSTANCE = new InterfaceExtensionImplementationClassResolver();

  @SuppressWarnings("SSBasedInspection")
  private static final Set<String> KNOWN_VIOLATORS = new HashSet<>(Arrays.asList(
    "com.intellij.internal.statistic.updater.StatisticsJobsScheduler",
    "com.intellij.javascript.debugger.execution.DebuggableProgramRunner",
    "com.intellij.spring.model.cacheable.EnableCachingModelProvider",
    "com.intellij.diff.actions.DiffCustomCommandHandler",
    "com.intellij.ide.actions.runAnything.RunAnythingCommandFolding"
  ));

  private InterfaceExtensionImplementationClassResolver() {
  }

  @Override
  public @NotNull Class<?> resolveImplementationClass(@NotNull ComponentManager componentManager,
                                                      @NotNull ExtensionComponentAdapter adapter) throws ClassNotFoundException {
    Object implementationClassOrName = adapter.implementationClassOrName;
    if (!(implementationClassOrName instanceof String)) {
      return (Class<?>)implementationClassOrName;
    }

    PluginDescriptor pluginDescriptor = adapter.getPluginDescriptor();
    String className = (String)implementationClassOrName;
    Class<?> result = componentManager.loadClass(className, pluginDescriptor);
    //noinspection SpellCheckingInspection
    if (result.getClassLoader() != pluginDescriptor.getPluginClassLoader() && pluginDescriptor.getPluginClassLoader() != null &&
        !className.startsWith("com.intellij.webcore.resourceRoots.") &&
        !className.startsWith("com.intellij.tasks.impl.") &&
        !KNOWN_VIOLATORS.contains(className)) {
      String idString = pluginDescriptor.getPluginId().getIdString();
      if (!idString.equals("com.intellij.java") && !idString.equals("com.intellij.java.ide")) {
        ExtensionPointImpl.LOG.error(componentManager.createError("Created extension classloader is not equal to plugin's one (" +
                                                                  "className=" + className + ", " +
                                                                  "extensionInstanceClassloader=" + result.getClassLoader() + ", " +
                                                                  "pluginClassloader=" + pluginDescriptor.getPluginClassLoader() +
                                                                  ")", pluginDescriptor.getPluginId()));
      }
    }
    implementationClassOrName = result;
    adapter.implementationClassOrName = implementationClassOrName;
    return result;
  }
}
