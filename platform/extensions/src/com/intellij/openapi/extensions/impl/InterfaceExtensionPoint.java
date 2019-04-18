// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.*;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.ComponentAdapter;
import org.picocontainer.MutablePicoContainer;

import java.util.List;

public class InterfaceExtensionPoint<T> extends ExtensionPointImpl<T> {
  public InterfaceExtensionPoint(@NotNull String name, @NotNull Class<T> clazz, @NotNull MutablePicoContainer picoContainer) {
    super(name, clazz.getName(), picoContainer, new UndefinedPluginDescriptor());

    myExtensionClass = clazz;
  }

  InterfaceExtensionPoint(@NotNull String name,
                          @NotNull String className,
                          @NotNull MutablePicoContainer picoContainer,
                          @NotNull PluginDescriptor pluginDescriptor) {
    super(name, className, picoContainer, pluginDescriptor);
  }

  protected boolean isUsePicoComponentAdapter() {
    return false;
  }

  @Override
  @NotNull
  protected ExtensionComponentAdapter createAdapterAndRegisterInPicoContainerIfNeeded(@NotNull Element extensionElement, @NotNull PluginDescriptor pluginDescriptor, @NotNull MutablePicoContainer picoContainer) {
    String implementationClassName = extensionElement.getAttributeValue("implementation");
    if (implementationClassName == null) {
      throw new RuntimeException("'implementation' attribute not specified for '" + getName() + "' extension in '"
                                 + pluginDescriptor.getPluginId() + "' plugin");
    }
    return doCreateAdapter(implementationClassName, extensionElement, shouldDeserializeInstance(extensionElement), pluginDescriptor, true, isUsePicoComponentAdapter());
  }

  private static boolean shouldDeserializeInstance(@NotNull Element extensionElement) {
    // has content
    if (!extensionElement.getContent().isEmpty()) {
      return true;
    }

    // has custom attributes
    for (Attribute attribute : extensionElement.getAttributes()) {
      final String name = attribute.getName();
      if (!"implementation".equals(name) && !"id".equals(name) && !"order".equals(name) && !"os".equals(name)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  protected static <T> T findExtension(@NotNull BaseExtensionPointName pointName,
                                       @NotNull Class<T> instanceOf,
                                       @Nullable AreaInstance areaInstance,
                                       boolean isRequired) {
    ExtensionPoint<T> point = Extensions.getArea(areaInstance).getExtensionPoint(pointName.getName());

    List<T> list = point.getExtensionList();
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, size = list.size(); i < size; i++) {
      T object = list.get(i);
      if (instanceOf.isInstance(object)) {
        return object;
      }
    }

    if (isRequired) {
      String message = "could not find extension implementation " + instanceOf;
      if (((ExtensionPointImpl)point).isInReadOnlyMode()) {
        message += " (point in read-only mode)";
      }
      throw new IllegalArgumentException(message);
    }
    return null;
  }

  static final class PicoContainerAwareInterfaceExtensionPoint<T> extends InterfaceExtensionPoint<T> {
    PicoContainerAwareInterfaceExtensionPoint(@NotNull String name,
                                              @NotNull String className,
                                              @NotNull MutablePicoContainer picoContainer,
                                              @NotNull PluginDescriptor pluginDescriptor) {
      super(name, className, picoContainer, pluginDescriptor);
    }

    @Override
    protected boolean isUsePicoComponentAdapter() {
      return true;
    }

    @NotNull
    @Override
    protected ExtensionComponentAdapter createAdapterAndRegisterInPicoContainerIfNeeded(@NotNull Element extensionElement,
                                                                                        @NotNull PluginDescriptor pluginDescriptor,
                                                                                        @NotNull MutablePicoContainer picoContainer) {
      ExtensionComponentAdapter adapter = super.createAdapterAndRegisterInPicoContainerIfNeeded(extensionElement, pluginDescriptor, picoContainer);
      picoContainer.registerComponent((ComponentAdapter)adapter);
      return adapter;
    }

    @Override
    public synchronized void reset() {
      //noinspection NonPrivateFieldAccessedInSynchronizedContext
      for (ExtensionComponentAdapter adapter : myAdapters) {
        if (adapter instanceof ComponentAdapter) {
          myPicoContainer.unregisterComponent(((ComponentAdapter)adapter).getComponentKey());
        }
      }

      super.reset();
    }
  }
}
