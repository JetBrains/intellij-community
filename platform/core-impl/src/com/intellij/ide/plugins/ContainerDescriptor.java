// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.components.ServiceDescriptor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.ListenerDescriptor;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public final class ContainerDescriptor {
  @Nullable
  List<ServiceDescriptor> services;
  @Nullable
  List<ComponentConfig> components;
  @Nullable
  List<ListenerDescriptor> listeners;
  @Nullable
  List<Element> extensionsPoints;

  transient THashMap<String, List<Element>> extensions;

  @NotNull
  public List<ServiceDescriptor> getServices() {
    return ContainerUtil.notNullize(services);
  }

  @NotNull
  public List<ComponentConfig> getComponents() {
    return ContainerUtil.notNullize(components);
  }

  @NotNull
  public List<ListenerDescriptor> getListeners() {
    return ContainerUtil.notNullize(listeners);
  }

  @Nullable
  public List<Element> getExtensionPoints() {
    return extensionsPoints;
  }

  void addService(@NotNull ServiceDescriptor serviceDescriptor) {
    if (services == null) {
      services = new ArrayList<>();
    }
    services.add(serviceDescriptor);
  }

  @NotNull
  List<ComponentConfig> getComponentListToAdd(int size) {
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
    extensionsPoints = concatOrNull(extensionsPoints, another.extensionsPoints);

    listeners = concatOrNull(listeners, another.listeners);
  }

  @Nullable
  static <T> List<T> concatOrNull(@Nullable List<T> l1, @Nullable List<T> l2) {
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
