// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

class XmlExtensionAdapter extends ExtensionComponentAdapter {
  private @Nullable Element myExtensionElement;

  private volatile Object extensionInstance;
  private boolean initializing;

  XmlExtensionAdapter(@NotNull String implementationClassName,
                      @NotNull PluginDescriptor pluginDescriptor,
                      @Nullable String orderId,
                      @NotNull LoadingOrder order,
                      @Nullable Element extensionElement,
                      @NotNull ImplementationClassResolver implementationClassResolver) {
    super(implementationClassName, pluginDescriptor, orderId, order, implementationClassResolver);

    myExtensionElement = extensionElement;
  }

  @Override
  final synchronized boolean isInstanceCreated() {
    return extensionInstance != null;
  }

  @Override
  public @NotNull <T> T createInstance(@NotNull ComponentManager componentManager) {
    @SuppressWarnings("unchecked")
    T instance = (T)extensionInstance;
    if (instance != null) {
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
        throw componentManager.createError("Cyclic extension initialization: " + this, pluginDescriptor.getPluginId());
      }

      try {
        initializing = true;

        Class<T> aClass;
        try {
          //noinspection unchecked
          aClass = (Class<T>)implementationClassResolver.resolveImplementationClass(componentManager, this);
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Throwable e) {
          throw componentManager.createError(e, pluginDescriptor.getPluginId());
        }

        instance = instantiateClass(aClass, componentManager);
        if (instance instanceof PluginAware) {
          ((PluginAware)instance).setPluginDescriptor(pluginDescriptor);
        }

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

  protected @NotNull <T> T instantiateClass(@NotNull Class<T> aClass, @NotNull ComponentManager componentManager) {
    return componentManager.instantiateClass(aClass, pluginDescriptor.getPluginId());
  }

  static final class SimpleConstructorInjectionAdapter extends XmlExtensionAdapter {
    SimpleConstructorInjectionAdapter(@NotNull String implementationClassName,
                                      @NotNull PluginDescriptor pluginDescriptor,
                                      @Nullable String orderId,
                                      @NotNull LoadingOrder order,
                                      @Nullable Element extensionElement,
                                      @NotNull ImplementationClassResolver implementationClassResolver) {
      super(implementationClassName, pluginDescriptor, orderId, order, extensionElement, implementationClassResolver);
    }

    @Override
    protected @NotNull <T> T instantiateClass(@NotNull Class<T> aClass, @NotNull ComponentManager componentManager) {
      if (!aClass.getName().equals("org.jetbrains.kotlin.asJava.finder.JavaElementFinder")) {
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

          ExtensionPointImpl.LOG.error("Cannot create extension without pico container (class=" + aClass.getName() + ", constructors=" +
                                       Arrays.toString(aClass.getDeclaredConstructors()) + ")," +
                                       " please remove extra constructor parameters", e);
        }
      }
      return componentManager.instantiateClassWithConstructorInjection(aClass, aClass, pluginDescriptor.getPluginId());
    }
  }
}
