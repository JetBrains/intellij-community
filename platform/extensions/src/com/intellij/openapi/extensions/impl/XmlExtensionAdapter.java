// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.pico.DefaultPicoContainer;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
          if (pluginDescriptor.isBundled()) {
            ExtensionPointImpl.LOG.error(message, e);
          }
          else {
            ExtensionPointImpl.LOG.warn(message, e);
          }
        }
      }
      return componentManager.instantiateClassWithConstructorInjection(aClass, aClass, pluginDescriptor.getPluginId());
    }
  }
}
