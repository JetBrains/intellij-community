/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.openapi.extensions.*;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.core.util.CompositeClassLoader;
import com.thoughtworks.xstream.io.xml.JDomReader;
import org.jdom.Element;
import org.picocontainer.PicoContainer;
import org.picocontainer.PicoInitializationException;
import org.picocontainer.PicoIntrospectionException;
import org.picocontainer.defaults.AssignabilityRegistrationException;
import org.picocontainer.defaults.ConstructorInjectionComponentAdapter;
import org.picocontainer.defaults.NotConcreteRegistrationException;

/**
 * @author Alexander Kireyev
 */
public class ExtensionComponentAdapter extends ConstructorInjectionComponentAdapter implements LoadingOrder.Orderable {
  private Object myComponentInstance;
  private Element myExtensionElement;
  private PicoContainer myContainer;
  private PluginDescriptor myPluginDescriptor;

  public ExtensionComponentAdapter(Class implementationClass, Element extensionElement, PicoContainer container, PluginDescriptor pluginDescriptor) {
    super(new Object(), implementationClass);
    myExtensionElement = extensionElement;
    myContainer = container;
    myPluginDescriptor = pluginDescriptor;
  }

  public Object getComponentInstance(final PicoContainer container) throws PicoInitializationException, PicoIntrospectionException, AssignabilityRegistrationException, NotConcreteRegistrationException {
    //assert myContainer == container : "Different containers: " + myContainer + " - " + container;

    if (myComponentInstance == null) {
      if (!Element.class.equals(getComponentImplementation())) {
        final CompositeClassLoader classLoader = new CompositeClassLoader();
        if (myPluginDescriptor.getPluginClassLoader() != null) {
          classLoader.add(myPluginDescriptor.getPluginClassLoader());
        }
        XStream xStream = new XStream(new PropertyReflectionProvider());
        xStream.setClassLoader(classLoader);
        xStream.registerConverter(new ElementConverter());
        Object componentInstance = super.getComponentInstance(container);
        if (componentInstance instanceof ReaderConfigurator) {
          ReaderConfigurator readerConfigurator = (ReaderConfigurator) componentInstance;
          readerConfigurator.configureReader(xStream);
        }
        xStream.alias(myExtensionElement.getName(), componentInstance.getClass());
        myComponentInstance = xStream.unmarshal(new JDomReader(myExtensionElement), componentInstance);
      }
      else {
        myComponentInstance = myExtensionElement;
      }
      if (myComponentInstance instanceof PluginAware) {
        PluginAware pluginAware = (PluginAware) myComponentInstance;
        pluginAware.setPluginDescriptor(myPluginDescriptor);
      }
    }

    return myComponentInstance;
  }

  public Object getExtension() {
    return getComponentInstance(myContainer);
  }

  public LoadingOrder getOrder() {
    String orderAttr = myExtensionElement.getAttributeValue("order");
    return LoadingOrder.readOrder(orderAttr);
  }

  public String getOrderId() {
    return myExtensionElement.getAttributeValue("id");
  }

  public Element getExtensionElement() {
    return myExtensionElement;
  }

  public Element getDescribingElement() {
    return getExtensionElement();
  }

  public PluginId getPluginName() {
    return myPluginDescriptor.getPluginId();
  }
}
