// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.pico.AssignableToComponentAdapter;
import com.intellij.util.pico.CachingConstructorInjectionComponentAdapter;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.*;

/**
 * @author Alexander Kireyev
 */
public class ExtensionComponentAdapter implements LoadingOrder.Orderable, AssignableToComponentAdapter {
  public static final ExtensionComponentAdapter[] EMPTY_ARRAY = new ExtensionComponentAdapter[0];

  private Object myComponentInstance;
  @Nullable
  private final Element myExtensionElement;
  private final PicoContainer myContainer;
  private final PluginDescriptor myPluginDescriptor;
  @NotNull
  private Object myImplementationClassOrName; // Class or String
  private boolean myNotificationSent;

  private final String myOrderId;
  private final LoadingOrder myOrder;

  public ExtensionComponentAdapter(@NotNull String implementationClassName,
                                   @Nullable PicoContainer container,
                                   @Nullable PluginDescriptor pluginDescriptor,
                                   @Nullable String orderId,
                                   @NotNull LoadingOrder order,
                                   @Nullable Element extensionElement) {
    myImplementationClassOrName = implementationClassName;
    myContainer = container;
    myPluginDescriptor = pluginDescriptor;
    myExtensionElement = extensionElement;

    myOrderId = orderId;
    myOrder = order;
  }

  public ExtensionComponentAdapter(@NotNull String implementationClassName,
                                   @Nullable Element extensionElement,
                                   PicoContainer container,
                                   PluginDescriptor pluginDescriptor) {
    this(implementationClassName, container, pluginDescriptor, null, LoadingOrder.ANY, extensionElement);
  }

  @Override
  public Object getComponentKey() {
    return this;
  }

  @Override
  public Class getComponentImplementation() {
    return loadImplementationClass();
  }

  @Override
  public Object getComponentInstance(final PicoContainer container) throws PicoException, ProcessCanceledException {
    if (myComponentInstance == null) {
      try {
        Class impl = loadImplementationClass();
        Object componentInstance = new CachingConstructorInjectionComponentAdapter(getComponentKey(), impl, null, true).getComponentInstance(container);

        if (myExtensionElement != null) {
          try {
            XmlSerializer.deserializeInto(componentInstance, myExtensionElement);
          }
          catch (Exception e) {
            throw new PicoInitializationException(e);
          }
        }

        myComponentInstance = componentInstance;
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable t) {
        PluginId pluginId = myPluginDescriptor != null ? myPluginDescriptor.getPluginId() : null;
        throw new PicoPluginExtensionInitializationException(t.getMessage(), t, pluginId);
      }

      if (myComponentInstance instanceof PluginAware) {
        PluginAware pluginAware = (PluginAware)myComponentInstance;
        pluginAware.setPluginDescriptor(myPluginDescriptor);
      }
    }

    return myComponentInstance;
  }

  @Override
  public void verify(PicoContainer container) throws PicoIntrospectionException {
    throw new UnsupportedOperationException("Method verify is not supported in " + getClass());
  }

  @Override
  public void accept(PicoVisitor visitor) {
    throw new UnsupportedOperationException("Method accept is not supported in " + getClass());
  }

  public Object getExtension() {
    return getComponentInstance(myContainer);
  }

  @Override
  public LoadingOrder getOrder() {
    return myOrder;
  }

  @Override
  public final String getOrderId() {
    return myOrderId;
  }

  public PluginId getPluginName() {
    return myPluginDescriptor.getPluginId();
  }

  public PluginDescriptor getPluginDescriptor() {
    return myPluginDescriptor;
  }

  @NotNull
  private Class loadImplementationClass() {
    Object implementationClassOrName = myImplementationClassOrName;
    if (implementationClassOrName instanceof String) {
      try {
        ClassLoader classLoader = myPluginDescriptor == null ? getClass().getClassLoader() : myPluginDescriptor.getPluginClassLoader();
        if (classLoader == null) {
          classLoader = getClass().getClassLoader();
        }
        myImplementationClassOrName = implementationClassOrName = Class.forName((String)implementationClassOrName, false, classLoader);
      }
      catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
    return (Class)implementationClassOrName;
  }

  @Override
  public String getAssignableToClassName() {
    Object implementationClassOrName = myImplementationClassOrName;
    if (implementationClassOrName instanceof String) {
      return (String)implementationClassOrName;
    }
    return ((Class)implementationClassOrName).getName();
  }

  boolean isNotificationSent() {
    return myNotificationSent;
  }

  void setNotificationSent(boolean notificationSent) {
    myNotificationSent = notificationSent;
  }

  @Override
  public String toString() {
    return "ExtensionComponentAdapter[" + getAssignableToClassName() + "]: plugin=" + myPluginDescriptor;
  }
}
