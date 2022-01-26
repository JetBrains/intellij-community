// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation;
import com.intellij.openapi.extensions.PluginDescriptor;
import org.jetbrains.annotations.NotNull;

final class InterfaceExtensionImplementationClassResolver implements ImplementationClassResolver {
  static final ImplementationClassResolver INSTANCE = new InterfaceExtensionImplementationClassResolver();

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
        !result.isAnnotationPresent(InternalIgnoreDependencyViolation.class)) {
      String idString = pluginDescriptor.getPluginId().getIdString();
      if (!idString.equals("com.intellij.java") && !idString.equals("com.intellij.java.ide") && !idString.equals("org.jetbrains.android")
          && !idString.equals("com.intellij.kotlinNative.platformDeps") && !idString.equals("com.jetbrains.rider.android")) {
        ExtensionPointImpl.LOG.error(componentManager.createError("Created extension classloader is not equal to plugin's one.\n" +
                                                                  "See https://youtrack.jetbrains.com/articles/IDEA-A-65/Plugin-Model#internalignoredependencyviolation\n" +
                                                                  "(\n" +
                                                                  "  className=" + className + ",\n" +
                                                                  "  extensionInstanceClassloader=" + result.getClassLoader() + ",\n" +
                                                                  "  pluginClassloader=" + pluginDescriptor.getPluginClassLoader() +
                                                                  "\n)", pluginDescriptor.getPluginId()));
      }
    }
    implementationClassOrName = result;
    adapter.implementationClassOrName = implementationClassOrName;
    return result;
  }
}
