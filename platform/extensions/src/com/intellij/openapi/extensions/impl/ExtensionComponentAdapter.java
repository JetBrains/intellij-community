/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import org.picocontainer.*;

/**
 * @author Alexander Kireyev
 * todo: optimize memory print
 */
public class ExtensionComponentAdapter implements LoadingOrder.Orderable, AssignableToComponentAdapter {
  public static final ExtensionComponentAdapter[] EMPTY_ARRAY = new ExtensionComponentAdapter[0];

  private Object myComponentInstance;
  private final String myImplementationClassName;
  private final Element myExtensionElement;
  private final PicoContainer myContainer;
  private final PluginDescriptor myPluginDescriptor;
  private final boolean myDeserializeInstance;
  private ComponentAdapter myDelegate;
  private Class myImplementationClass;
  private boolean myNotificationSent = false;

  public ExtensionComponentAdapter(@NotNull String implementationClass,
                                   Element extensionElement,
                                   PicoContainer container,
                                   PluginDescriptor pluginDescriptor,
                                   boolean deserializeInstance) {
    myImplementationClassName = implementationClass;
    myExtensionElement = extensionElement;
    myContainer = container;
    myPluginDescriptor = pluginDescriptor;
    myDeserializeInstance = deserializeInstance;
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
        if (Element.class.equals(getComponentImplementation())) {
          myComponentInstance = myExtensionElement;
        }
        else {
          Object componentInstance = getDelegate().getComponentInstance(container);

          if (myDeserializeInstance) {
            try {
              XmlSerializer.deserializeInto(componentInstance, myExtensionElement);
            }
            catch (Exception e) {
              throw new PicoInitializationException(e);
            }
          }

          myComponentInstance = componentInstance;
        }
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
    return LoadingOrder.readOrder(myExtensionElement.getAttributeValue("order"));
  }

  @Override
  public String getOrderId() {
    return myExtensionElement.getAttributeValue("id");
  }

  private Element getExtensionElement() {
    return myExtensionElement;
  }

  @Override
  public Element getDescribingElement() {
    return getExtensionElement();
  }

  public PluginId getPluginName() {
    return myPluginDescriptor.getPluginId();
  }

  public PluginDescriptor getPluginDescriptor() {
    return myPluginDescriptor;
  }

  @NotNull
  private Class loadImplementationClass() {
    if (myImplementationClass == null) {
      try {
        ClassLoader classLoader = myPluginDescriptor != null ? myPluginDescriptor.getPluginClassLoader() : getClass().getClassLoader();
        if (classLoader == null) {
          classLoader = getClass().getClassLoader();
        }
        myImplementationClass = Class.forName(myImplementationClassName, false, classLoader);
      }
      catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
    return myImplementationClass;
  }

  private synchronized ComponentAdapter getDelegate() {
    if (myDelegate == null) {
      Class impl = loadImplementationClass();
      myDelegate = new CachingConstructorInjectionComponentAdapter(getComponentKey(), impl, null, true);
    }

    return myDelegate;
  }

  @Override
  public String getAssignableToClassName() {
    return myImplementationClassName;
  }

  public boolean isNotificationSent() {
    return myNotificationSent;
  }

  public void setNotificationSent(boolean notificationSent) {
    myNotificationSent = notificationSent;
  }

  @Override
  public String toString() {
    return "ExtensionComponentAdapter[" + myImplementationClassName + "]: plugin=" + myPluginDescriptor;
  }
}
