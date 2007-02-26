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
import com.intellij.util.pico.AssignableToComponentAdapter;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.AnnotationProvider;
import com.thoughtworks.xstream.annotations.AnnotationReflectionConverter;
import com.thoughtworks.xstream.annotations.Annotations;
import com.thoughtworks.xstream.converters.basic.*;
import com.thoughtworks.xstream.converters.collections.*;
import com.thoughtworks.xstream.converters.enums.EnumConverter;
import com.thoughtworks.xstream.converters.enums.EnumMapConverter;
import com.thoughtworks.xstream.converters.enums.EnumSetConverter;
import com.thoughtworks.xstream.converters.extended.EncodedByteArrayConverter;
import com.thoughtworks.xstream.converters.extended.FileConverter;
import com.thoughtworks.xstream.converters.extended.GregorianCalendarConverter;
import com.thoughtworks.xstream.converters.extended.LocaleConverter;
import com.thoughtworks.xstream.converters.reflection.ExternalizableConverter;
import com.thoughtworks.xstream.converters.reflection.ReflectionProvider;
import com.thoughtworks.xstream.converters.reflection.SerializableConverter;
import com.thoughtworks.xstream.core.util.CompositeClassLoader;
import com.thoughtworks.xstream.io.xml.JDomReader;
import com.thoughtworks.xstream.mapper.Mapper;
import org.jdom.Element;
import org.picocontainer.*;
import org.picocontainer.defaults.AssignabilityRegistrationException;
import org.picocontainer.defaults.CachingComponentAdapter;
import org.picocontainer.defaults.ConstructorInjectionComponentAdapter;
import org.picocontainer.defaults.NotConcreteRegistrationException;

/**
 * @author Alexander Kireyev
 */
public class ExtensionComponentAdapter implements ComponentAdapter, LoadingOrder.Orderable, AssignableToComponentAdapter {
  private Object myComponentInstance;
  private String myImplementationClassName;
  private Element myExtensionElement;
  private PicoContainer myContainer;
  private PluginDescriptor myPluginDescriptor;
  private ComponentAdapter myDelegate;
  private Class myImplementationClass;

  public ExtensionComponentAdapter(
    String implementationClass,
    Element extensionElement,
    PicoContainer container,
    PluginDescriptor pluginDescriptor) {
    myImplementationClassName = implementationClass;
    myExtensionElement = extensionElement;
    myContainer = container;
    myPluginDescriptor = pluginDescriptor;
  }

  public Object getComponentKey() {
    return this;
  }

  public Class getComponentImplementation() {
    return loadClass(myImplementationClassName);
  }

  public Object getComponentInstance(final PicoContainer container) throws PicoInitializationException, PicoIntrospectionException, AssignabilityRegistrationException, NotConcreteRegistrationException {
    //assert myContainer == container : "Different containers: " + myContainer + " - " + container;

    if (myComponentInstance == null) {
      if (!Element.class.equals(getComponentImplementation())) {
        Object componentInstance = getDelegate().getComponentInstance(container);

        final CompositeClassLoader classLoader = new CompositeClassLoader();
        if (myPluginDescriptor.getPluginClassLoader() != null) {
          classLoader.add(myPluginDescriptor.getPluginClassLoader());
        }
        //XStream xStream = new XStream(new PropertyReflectionProvider());
        XStream xStream = new XStream()
        {
          @Override
          protected void setupConverters() {
            final Mapper mapper = getMapper();
            final ReflectionProvider reflectionProvider = getReflectionProvider();

              registerConverter(new AnnotationReflectionConverter(mapper, reflectionProvider, new AnnotationProvider()), PRIORITY_LOW);
              registerConverter(new SerializableConverter(mapper, reflectionProvider), PRIORITY_LOW);
              registerConverter(new ExternalizableConverter(mapper), PRIORITY_LOW);

              registerConverter(new NullConverter(), PRIORITY_VERY_HIGH);
              registerConverter(new IntConverter(), PRIORITY_NORMAL);
              registerConverter(new FloatConverter(), PRIORITY_NORMAL);
              registerConverter(new DoubleConverter(), PRIORITY_NORMAL);
              registerConverter(new LongConverter(), PRIORITY_NORMAL);
              registerConverter(new ShortConverter(), PRIORITY_NORMAL);
              registerConverter(new CharConverter(), PRIORITY_NORMAL);
              registerConverter(new BooleanConverter(), PRIORITY_NORMAL);
              registerConverter(new ByteConverter(), PRIORITY_NORMAL);

              registerConverter(new StringConverter(), PRIORITY_NORMAL);
              registerConverter(new StringBufferConverter(), PRIORITY_NORMAL);
              registerConverter(new DateConverter(), PRIORITY_NORMAL);
              registerConverter(new BitSetConverter(), PRIORITY_NORMAL);
              registerConverter(new URLConverter(), PRIORITY_NORMAL);
              registerConverter(new BigIntegerConverter(), PRIORITY_NORMAL);
              registerConverter(new BigDecimalConverter(), PRIORITY_NORMAL);

              registerConverter(new ArrayConverter(mapper), PRIORITY_NORMAL);
              registerConverter(new CharArrayConverter(), PRIORITY_NORMAL);
              registerConverter(new CollectionConverter(mapper), PRIORITY_NORMAL);
              registerConverter(new MapConverter(mapper), PRIORITY_NORMAL);
              registerConverter(new TreeMapConverter(mapper), PRIORITY_NORMAL);
              registerConverter(new TreeSetConverter(mapper), PRIORITY_NORMAL);
              registerConverter(new PropertiesConverter(), PRIORITY_NORMAL);
              registerConverter(new EncodedByteArrayConverter(), PRIORITY_NORMAL);

              registerConverter(new FileConverter(), PRIORITY_NORMAL);
              /*
              registerConverter(new DynamicProxyConverter(mapper, classLoaderReference), PRIORITY_NORMAL);
              registerConverter(new JavaClassConverter(classLoaderReference), PRIORITY_NORMAL);
              registerConverter(new JavaMethodConverter(classLoaderReference), PRIORITY_NORMAL);
              */
              registerConverter(new LocaleConverter(), PRIORITY_NORMAL);
              registerConverter(new GregorianCalendarConverter(), PRIORITY_NORMAL);

              registerConverter(new EnumConverter(), PRIORITY_NORMAL);
              registerConverter(new EnumSetConverter(mapper), PRIORITY_NORMAL);
              registerConverter(new EnumMapConverter(mapper), PRIORITY_NORMAL);


              //registerConverter(new SelfStreamingInstanceChecker(reflectionConverter, this), PRIORITY_NORMAL);
          }
        };
        xStream.setClassLoader(classLoader);
        //xStream.registerConverter(new ElementConverter());
        if (componentInstance instanceof ReaderConfigurator) {
          ReaderConfigurator readerConfigurator = (ReaderConfigurator) componentInstance;
          readerConfigurator.configureReader(xStream);
        }
        Annotations.configureAliases(xStream, componentInstance.getClass());
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

  public void verify(PicoContainer container) throws PicoIntrospectionException {
    throw new UnsupportedOperationException("Method verify is not supported in " + getClass());
  }

  public void accept(PicoVisitor visitor) {
    throw new UnsupportedOperationException("Method accept is not supported in " + getClass());
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

  private Element getExtensionElement() {
    return myExtensionElement;
  }

  public Element getDescribingElement() {
    return getExtensionElement();
  }

  public PluginId getPluginName() {
    return myPluginDescriptor.getPluginId();
  }

  public PluginDescriptor getPluginDescriptor() {
    return myPluginDescriptor;
  }

  private Class loadClass(final String className) {
    if (myImplementationClass != null) return myImplementationClass;

    try {
      ClassLoader classLoader = myPluginDescriptor != null ? myPluginDescriptor.getPluginClassLoader() : getClass().getClassLoader();
      if (classLoader == null) {
        classLoader = getClass().getClassLoader();
      }


      myImplementationClass = Class.forName(className, true, classLoader);
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }

    return myImplementationClass;
  }

  private synchronized ComponentAdapter getDelegate() {
    if (myDelegate == null) {
      myDelegate = new CachingComponentAdapter(new ConstructorInjectionComponentAdapter(getComponentKey(), loadClass(
        myImplementationClassName), null, true));
    }

    return myDelegate;
  }

  public boolean isAssignableTo(Class aClass) {
    return aClass.getName().equals(myImplementationClassName);
  }
}
