// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.*
import com.intellij.util.XmlElement
import com.intellij.util.xmlb.XmlSerializer
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import java.util.*

class XmlExtensionAdapter extends ExtensionComponentAdapter {
  private @Nullable XmlElement extensionElement;

  private static final Object NOT_APPLICABLE = new Object();

  private volatile Object extensionInstance;
  private boolean initializing;

  XmlExtensionAdapter(@NotNull String implementationClassName,
                      @NotNull PluginDescriptor pluginDescriptor,
                      @Nullable String orderId,
                      @NotNull LoadingOrder order,
                      @Nullable XmlElement extensionElement,
                      @NotNull ImplementationClassResolver implementationClassResolver) {
    super(implementationClassName, pluginDescriptor, orderId, order, implementationClassResolver);

    this.extensionElement = extensionElement;
  }

  @Override
  final synchronized boolean isInstanceCreated() {
    return extensionInstance != null;
  }

  @Override
  public @Nullable <T> T createInstance(@NotNull ComponentManager componentManager) {
    @SuppressWarnings("unchecked")
    T instance = (T)extensionInstance;
    if (instance != null) {
      return instance == NOT_APPLICABLE ? null : instance;
    }

    //noinspection SynchronizeOnThis
    synchronized (this) {
      //noinspection unchecked
      instance = (T)extensionInstance;
      if (instance != null) {
        return instance == NOT_APPLICABLE ? null : instance;
      }

      if (initializing) {
        throw componentManager.createError("Cyclic extension initialization: " + this, pluginDescriptor.getPluginId());
      }

      try {
        initializing = true;

        //noinspection unchecked
        Class<T> aClass = (Class<T>)implementationClassResolver.resolveImplementationClass(componentManager, this);
        instance = instantiateClass(aClass, componentManager);
        if (instance instanceof PluginAware) {
          ((PluginAware)instance).setPluginDescriptor(pluginDescriptor);
        }

        XmlElement element = extensionElement;
        if (element != null) {
          XmlSerializer.getBeanBinding(instance.getClass()).deserializeInto(instance, element);
          extensionElement = null;
        }

        extensionInstance = instance;
      }
      catch (ExtensionNotApplicableException e) {
        extensionInstance = NOT_APPLICABLE;
        extensionElement = null;
        return null;
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
        throw componentManager.createError("Cannot create extension (class=" + getAssignableToClassName() + ")", e,
                                           pluginDescriptor.getPluginId(), null);
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
                                      @NotNull ExtensionDescriptor descriptor,
                                      @NotNull ImplementationClassResolver implementationClassResolver) {
      super(implementationClassName, pluginDescriptor, descriptor.orderId, descriptor.order, descriptor.element, implementationClassResolver);
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
