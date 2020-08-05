// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.pico.DefaultPicoContainer;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

class XmlExtensionAdapter extends ExtensionComponentAdapter {
  private @Nullable Element myExtensionElement;

  private volatile Object extensionInstance;
  private boolean initializing;

  XmlExtensionAdapter(@NotNull String implementationClassName,
                      @NotNull PluginDescriptor pluginDescriptor,
                      @Nullable String orderId,
                      @NotNull LoadingOrder order,
                      @Nullable Element extensionElement) {
    super(implementationClassName, pluginDescriptor, orderId, order);

    myExtensionElement = extensionElement;
  }

  @Override
  synchronized boolean isInstanceCreated() {
    return extensionInstance != null;
  }

  @Override
  public @NotNull <T> T createInstance(@NotNull ComponentManager componentManager) {
    @SuppressWarnings("unchecked")
    T instance = (T)extensionInstance;
    if (instance != null) {
      // todo add assert that createInstance was already called
      // problem is that ExtensionPointImpl clears cache on runtime modification and so adapter instance need to be recreated
      // it will be addressed later, for now better to reduce scope of changes
      return instance;
    }

    //noinspection SynchronizeOnThis
    synchronized (this) {
      //noinspection unchecked
      instance = (T)extensionInstance;
      if (instance != null) {
        return instance;
      }

      if (initializing) {
        componentManager.logError(new IllegalStateException("Cyclic extension initialization: " + this), getPluginDescriptor().getPluginId());
      }

      try {
        initializing = true;

        instance = super.createInstance(componentManager);

        Element element = myExtensionElement;
        if (element != null) {
          XmlSerializer.deserializeInto(instance, element);
          myExtensionElement = null;
        }

        extensionInstance = instance;
      }
      finally {
        initializing = false;
      }
    }
    return instance;
  }

  boolean isLoadedFromAnyElement(@NotNull List<? extends Element> candidateElements, @NotNull Map<String, String> defaultAttributes) {
    SkipDefaultValuesSerializationFilters filter = new SkipDefaultValuesSerializationFilters();
    if (myExtensionElement == null && extensionInstance == null) {
      // dummy extension with no data; unload based on PluginDescriptor check in calling method
      return true;
    }

    Element serializedElement = myExtensionElement != null ? myExtensionElement : XmlSerializer.serialize(extensionInstance, filter);
    Map<String, String> serializedData = getSerializedDataMap(serializedElement);

    for (Element candidateElement : candidateElements) {
      Map<String, String> candidateData = getSerializedDataMap(candidateElement);
      candidateData.entrySet().removeIf(entry -> Objects.equals(defaultAttributes.get(entry.getKey()), entry.getValue()));
      if (serializedData.equals(candidateData)) {
        return true;
      }
    }
    return false;
  }

  static Map<String, String> getSerializedDataMap(Element serializedElement) {
    Map<String, String> data = new HashMap<>();
    for (Attribute attribute : serializedElement.getAttributes()) {
      if (!attribute.getName().equals("id") && !attribute.getName().equals("order")) {
        data.put(attribute.getName(), attribute.getValue());
      }
    }
    for (Element child : serializedElement.getChildren()) {
      data.put(child.getName(), child.getText());
    }
    return data;
  }

  static final class SimpleConstructorInjectionAdapter extends XmlExtensionAdapter {
    SimpleConstructorInjectionAdapter(@NotNull String implementationClassName,
                                      @NotNull PluginDescriptor pluginDescriptor,
                                      @Nullable String orderId,
                                      @NotNull LoadingOrder order,
                                      @Nullable Element extensionElement) {
      super(implementationClassName, pluginDescriptor, orderId, order, extensionElement);
    }

    @Override
    protected @NotNull <T> T instantiateClass(@NotNull Class<T> aClass, @NotNull ComponentManager componentManager) {
      // enable simple instantiateClass for project/module containers in 2020.0 (once Kotlin will be fixed - it is one of the important plugin)
      if (((DefaultPicoContainer)componentManager.getPicoContainer()).getParent() == null) {
        try {
          return super.instantiateClass(aClass, componentManager);
        }
        catch (ProcessCanceledException | ExtensionNotApplicableException e) {
          throw e;
        }
        catch (RuntimeException e) {
          Throwable cause = e.getCause();
          if (!(cause instanceof NoSuchMethodException || cause instanceof IllegalArgumentException)) {
            throw e;
          }

          String message = "Cannot create extension without pico container (class=" + aClass.getName() + ")," +
                           " please remove extra constructor parameters";
          PluginDescriptor pluginDescriptor = getPluginDescriptor();
          if (pluginDescriptor.isBundled() && !isKnownBadPlugin(pluginDescriptor)) {
            ExtensionPointImpl.LOG.error(message, e);
          }
          else {
            ExtensionPointImpl.LOG.warn(message, e);
          }
        }
      }
      return componentManager.instantiateClassWithConstructorInjection(aClass, aClass, getPluginDescriptor().getPluginId());
    }

    private static boolean isKnownBadPlugin(@NotNull PluginDescriptor pluginDescriptor) {
      String id = pluginDescriptor.getPluginId().getIdString();
      //noinspection SpellCheckingInspection
      return id.equals("Lombook Plugin");
    }
  }
}
