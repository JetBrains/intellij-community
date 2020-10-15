// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.components.ServiceDescriptor;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.util.messages.ListenerDescriptor;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@ApiStatus.Internal
public final class ContainerDescriptor {
  @Nullable List<ServiceDescriptor> services;
  @Nullable List<ComponentConfig> components;
  @Nullable List<ListenerDescriptor> listeners;
  @Nullable List<ExtensionPointImpl<?>> extensionPoints;

  public transient Map<String, List<Element>> extensions;

  public @NotNull List<ServiceDescriptor> getServices() {
    return services == null ? Collections.emptyList() : services;
  }

  public @Nullable List<ComponentConfig> getComponents() {
    return components;
  }

  public @Nullable List<ListenerDescriptor> getListeners() {
    return listeners;
  }

  public @Nullable List<ExtensionPointImpl<?>> getExtensionPoints() {
    return extensionPoints;
  }

  void addService(@NotNull ServiceDescriptor serviceDescriptor) {
    if (services == null) {
      services = new ArrayList<>();
    }
    services.add(serviceDescriptor);
  }

  @NotNull List<ComponentConfig> getComponentListToAdd(int size) {
    List<ComponentConfig> result = components;
    if (result == null) {
      result = new ArrayList<>(size);
      components = result;
    }
    else {
      ((ArrayList<ComponentConfig>)result).ensureCapacity(result.size() + size);
    }
    return result;
  }

  @Override
  public String toString() {
    if (services == null && components == null && extensionPoints == null && extensions == null && listeners == null) {
      return "ContainerDescriptor(empty)";
    }

    return "ContainerDescriptor(" +
           "services=" + services +
           ", components=" + components +
           ", extensionPoints=" + extensionPoints +
           ", extensions=" + extensions +
           ", listeners=" + listeners +
           ')';
  }
}
