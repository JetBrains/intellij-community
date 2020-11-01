// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.extensions.PluginDescriptor;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public final class InterfaceExtensionPoint<T> extends ExtensionPointImpl<T> {
  public InterfaceExtensionPoint(@NotNull String name,
                                 @NotNull String className,
                                 @NotNull PluginDescriptor pluginDescriptor,
                                 @Nullable Class<T> clazz,
                                 boolean dynamic) {
    super(name, className, pluginDescriptor, clazz, dynamic);
  }

  static final class InterfaceExtensionImplementationClassResolver implements ImplementationClassResolver {
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
          !className.equals("com.intellij.internal.statistic.updater.StatisticsJobsScheduler") &&
          !className.startsWith("com.intellij.webcore.resourceRoots.") &&
          !className.startsWith("com.intellij.tasks.impl.") &&
          !className.equals("com.intellij.javascript.debugger.execution.DebuggableProgramRunner")) {
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

  @Override
  public @NotNull ExtensionPointImpl<T> cloneFor(@NotNull ComponentManager manager) {
    InterfaceExtensionPoint<T> result = new InterfaceExtensionPoint<>(getName(), getClassName(), getPluginDescriptor(), null, isDynamic());
    result.setComponentManager(manager);
    return result;
  }

  @Override
  protected @NotNull ExtensionComponentAdapter createAdapterAndRegisterInPicoContainerIfNeeded(@NotNull Element extensionElement, @NotNull PluginDescriptor pluginDescriptor, @NotNull ComponentManager componentManager) {
    String implementationClassName = extensionElement.getAttributeValue("implementation");
    if (implementationClassName == null) {
      // deprecated
      implementationClassName = extensionElement.getAttributeValue("implementationClass");
      if (implementationClassName == null) {
        throw componentManager.createError("Attribute \"implementation\" is not specified for \"" + getName() + "\" extension", pluginDescriptor.getPluginId());
      }
    }

    String orderId = extensionElement.getAttributeValue("id");
    LoadingOrder order = LoadingOrder.readOrder(extensionElement.getAttributeValue("order"));
    Element effectiveElement = shouldDeserializeInstance(extensionElement) ? extensionElement : null;
    return new XmlExtensionAdapter.SimpleConstructorInjectionAdapter(implementationClassName, pluginDescriptor, orderId, order, effectiveElement, InterfaceExtensionImplementationClassResolver.INSTANCE);
  }

  @Override
  void unregisterExtensions(@NotNull ComponentManager componentManager,
                            @NotNull PluginDescriptor pluginDescriptor,
                            @NotNull List<Element> elements,
                            @NotNull List<Runnable> priorityListenerCallbacks,
                            @NotNull List<Runnable> listenerCallbacks) {
    unregisterExtensions(adapter -> adapter.getPluginDescriptor() != pluginDescriptor, false, priorityListenerCallbacks, listenerCallbacks);
  }

  private static boolean shouldDeserializeInstance(@NotNull Element extensionElement) {
    // has content
    if (!extensionElement.getContent().isEmpty()) {
      return true;
    }

    // has custom attributes
    for (Attribute attribute : extensionElement.getAttributes()) {
      String name = attribute.getName();
      if (!("implementation".equals(name) ||
            "implementationClass".equals(name) ||
            "id".equals(name) ||
            "order".equals(name) ||
            "os".equals(name))) {
        return true;
      }
    }
    return false;
  }
}
