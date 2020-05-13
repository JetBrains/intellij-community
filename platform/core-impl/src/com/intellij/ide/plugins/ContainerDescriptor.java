// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.components.ServiceDescriptor;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.ListenerDescriptor;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApiStatus.Internal
public final class ContainerDescriptor {
  @Nullable List<ServiceDescriptor> services;
  @Nullable List<ComponentConfig> components;
  @Nullable List<ListenerDescriptor> listeners;
  @Nullable List<ExtensionPointImpl<?>> extensionPoints;

  transient Map<String, List<Element>> extensions;

  public @NotNull List<ServiceDescriptor> getServices() {
    return ContainerUtil.notNullize(services);
  }

  public @NotNull List<ComponentConfig> getComponents() {
    return ContainerUtil.notNullize(components);
  }

  public @NotNull List<ListenerDescriptor> getListeners() {
    return ContainerUtil.notNullize(listeners);
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

  void merge(@NotNull ContainerDescriptor another) {
    components = concatOrNull(components, another.components);
    services = concatOrNull(services, another.services);
    extensionPoints = concatOrNull(extensionPoints, another.extensionPoints);
    listeners = concatOrNull(listeners, another.listeners);
  }

  @Nullable static <T> List<T> concatOrNull(@Nullable List<T> l1, @Nullable List<T> l2) {
    if (l1 == null) {
      return l2;
    }
    else if (l2 == null) {
      return l1;
    }
    else {
      return ContainerUtil.concat(l1, l2);
    }
  }
}
