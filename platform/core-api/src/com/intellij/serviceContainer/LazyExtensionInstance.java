// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serviceContainer;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.function.BiConsumer;

/**
 * Use only if you need to cache created instance.
 * @see {@link com.intellij.openapi.extensions.ExtensionPointName#processWithPluginDescriptor(BiConsumer)}
 */
public abstract class LazyExtensionInstance<T> {
  private volatile T instance;

  protected LazyExtensionInstance() {
  }

  @TestOnly
  protected LazyExtensionInstance(@NotNull T instance) {
    this.instance = instance;
  }

  protected abstract @Nullable String getImplementationClassName();

  public final @NotNull T getInstance(@NotNull ComponentManager componentManager, @NotNull PluginDescriptor pluginDescriptor) {
    T result = instance;
    if (result != null) {
      return result;
    }

    //noinspection SynchronizeOnThis
    synchronized (this) {
      result = instance;
      if (result != null) {
        return result;
      }

      result = createInstance(componentManager, pluginDescriptor);
      instance = result;
    }
    return result;
  }

  public @NotNull T createInstance(@NotNull ComponentManager componentManager, @NotNull PluginDescriptor pluginDescriptor) {
    String className = getImplementationClassName();
    if (className == null) {
      throw new PluginException("implementation class is not specified", pluginDescriptor.getPluginId());
    }
    return componentManager.instantiateClass(className, pluginDescriptor);
  }
}
