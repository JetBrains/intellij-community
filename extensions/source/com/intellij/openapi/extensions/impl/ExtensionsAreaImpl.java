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
import com.intellij.util.containers.ConcurrentHashMap;
import org.apache.commons.collections.MultiHashMap;
import org.apache.commons.collections.MultiMap;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.PicoContainer;
import org.picocontainer.defaults.ConstructorInjectionComponentAdapter;
import org.picocontainer.defaults.DefaultPicoContainer;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.util.*;

@SuppressWarnings({"HardCodedStringLiteral"})
public class ExtensionsAreaImpl implements ExtensionsArea {
  private final LogProvider myLogger;
  private static final String ATTRIBUTE_AREA = "area";

  private static Map<String,String> ourDefaultEPs = new HashMap<String, String>();

  static {
    ourDefaultEPs.put(EPAvailabilityListenerExtension.EXTENSION_POINT_NAME, EPAvailabilityListenerExtension.class.getName());
  }

  private static boolean DEBUG_REGISTRATION = true;

  private AreaPicoContainerImpl myPicoContainer;
  private Throwable myCreationTrace = null;
  private Map<String,ExtensionPointImpl> myExtensionPoints = new ConcurrentHashMap<String, ExtensionPointImpl>();
  private Map<String,Throwable> myEPTraces = new HashMap<String, Throwable>();
  private MultiMap myAvailabilityListeners = new MultiHashMap();
  private List<Runnable> mySuspendedListenerActions = new ArrayList<Runnable>();
  private boolean myAvailabilityNotificationsActive = true;

  private final AreaInstance myAreaInstance;
  private final String myAreaClass;
  private Map<Element,ExtensionComponentAdapter> myExtensionElement2extension = new HashMap<Element, ExtensionComponentAdapter>();
  private Map<String,DefaultPicoContainer> myPluginName2picoContainer = new HashMap<String, DefaultPicoContainer>();

  public ExtensionsAreaImpl(String areaClass, AreaInstance areaInstance, PicoContainer parentPicoContainer, LogProvider logger) {
    if (DEBUG_REGISTRATION) {
      myCreationTrace = new Throwable("Area creation trace");
    }
    myAreaClass = areaClass;
    myAreaInstance = areaInstance;
    myPicoContainer = new AreaPicoContainerImpl(parentPicoContainer, areaInstance);
    //if (areaInstance != null) {
    //  myPicoContainer.registerComponentInstance(areaInstance);
    //}
    myLogger = logger;
    initialize();
  }

  public ExtensionsAreaImpl(MutablePicoContainer picoContainer, LogProvider logger) {
    this(null, null, picoContainer, logger);
  }

  @TestOnly
  public final void dropCaches() {
    for (final ExtensionPointImpl point : myExtensionPoints.values()) {
      point.dropCaches();
    }
  }

  public AreaPicoContainer getPicoContainer() {
    return myPicoContainer;
  }

  MutablePicoContainer getMutablePicoContainer() {
    return myPicoContainer;
  }

  public String getAreaClass() {
    return myAreaClass;
  }

  public void registerExtensionPoint(String pluginName, Element extensionPointElement) {
    registerExtensionPoint(new DefaultPluginDescriptor(PluginId.getId(pluginName)), extensionPointElement);
  }

  public void registerExtensionPoint(PluginDescriptor pluginDescriptor, Element extensionPointElement) {
    assert pluginDescriptor.getPluginId() != null;
    String epName = pluginDescriptor.getPluginId().getIdString() + '.' + extensionPointElement.getAttributeValue("name");
    String className = extensionPointElement.getAttributeValue("beanClass");
    if (className == null) {
      className = extensionPointElement.getAttributeValue("interface");
    }
    if (className == null) {
      throw new RuntimeException("No class specified for extension point: " + epName);
    }
    registerExtensionPoint(epName, className, pluginDescriptor);
  }

  public void registerExtension(final String pluginName, final Element extensionElement) {
    registerExtension(new DefaultPluginDescriptor(PluginId.getId(pluginName)), extensionElement);
  }

  public void registerExtension(final PluginDescriptor pluginDescriptor, final Element extensionElement) {
    final PluginId pluginId = pluginDescriptor.getPluginId();

    String epName = extractEPName(extensionElement);
    ExtensionComponentAdapter adapter;
    String implClass;
    Class extensionClass = getExtensionPoint(epName).getExtensionClass();
    implClass = extensionElement.getAttributeValue("implementation");

    if (extensionClass.isInterface() || Modifier.isAbstract(extensionClass.getModifiers())) {
      if (implClass == null) {
        throw new RuntimeException("Expected implementation for extension declaration (ep = " + epName + ")");
      }
    }
    if (implClass != null) {
      adapter = new ExtensionComponentAdapter(implClass, extensionElement, getPluginContainer(pluginId.getIdString()), pluginDescriptor, false);
    }
    else {
      final ExtensionPoint extensionPoint = getExtensionPoint(epName);
      adapter = new ExtensionComponentAdapter(extensionPoint.getExtensionClass().getName(), extensionElement, getPluginContainer(pluginId.getIdString()), pluginDescriptor, true);
    }
    myExtensionElement2extension.put(extensionElement, adapter);
    internalGetPluginContainer().registerComponent(adapter);
    getExtensionPointImpl(epName).registerExtensionAdapter(adapter);
  }

  private static String extractEPName(final Element extensionElement) {
    String epName = extensionElement.getAttributeValue("point");

    if (epName == null) {
      final Element parentElement = extensionElement.getParentElement();
      final String ns = parentElement != null ? parentElement.getAttributeValue("defaultExtensionNs"):null;

      if (ns != null) {
        epName = ns + '.' + extensionElement.getName();
      } else {
        Namespace namespace = extensionElement.getNamespace();
        epName = namespace.getURI() + '.' + extensionElement.getName();
      }
    }
    return epName;
  }

  public PicoContainer getPluginContainer(String pluginName) {
    return internalGetPluginContainer();
  }

  private MutablePicoContainer internalGetPluginContainer() {
    return myPicoContainer;
  }

  private void disposePluginContainer(String pluginName) {
    DefaultPicoContainer pluginContainer = myPluginName2picoContainer.remove(pluginName);
    if (pluginContainer != null) {
      myPicoContainer.removeChildContainer(pluginContainer);
    }
  }

  public void unregisterExtensionPoint(String pluginName, Element extensionPointElement) {
    assert pluginName != null;
    String epName = pluginName + '.' + extensionPointElement.getAttributeValue("name");
    unregisterExtensionPoint(epName);
  }

  public void unregisterExtension(String pluginName, Element extensionElement) {
    String epName = extractEPName(extensionElement);
    if (!myExtensionElement2extension.containsKey(extensionElement)) {
      XMLOutputter xmlOutputter = new XMLOutputter();
      Format format = Format.getCompactFormat().setIndent("  ").setTextMode(Format.TextMode.NORMALIZE);
      xmlOutputter.setFormat(format);
      StringWriter stringWriter = new StringWriter();
      try {
        xmlOutputter.output(extensionElement, stringWriter);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      myLogger.warn(stringWriter.toString());
      throw new IllegalArgumentException("Trying to unregister extension element that was never registered");
    }
    ExtensionComponentAdapter adapter = myExtensionElement2extension.remove(extensionElement);
    if (adapter == null) return;
    if (getExtensionPointImpl(epName).unregisterComponentAdapter(adapter)) {
      MutablePicoContainer pluginContainer = internalGetPluginContainer();
      pluginContainer.unregisterComponent(adapter.getComponentKey());
      if (pluginContainer.getComponentAdapters().size() == 0) {
        disposePluginContainer(pluginName);
      }
    }
  }

  @SuppressWarnings({"unchecked"})
  private void initialize() {
    for (String epName : ourDefaultEPs.keySet()) {
      registerExtensionPoint(epName, ourDefaultEPs.get(epName));
    }

    getExtensionPoint(EPAvailabilityListenerExtension.EXTENSION_POINT_NAME).addExtensionPointListener(new ExtensionPointListener() {
      @SuppressWarnings({"unchecked"})
      public void extensionRemoved(Object extension, final PluginDescriptor pluginDescriptor) {
        EPAvailabilityListenerExtension epListenerExtension = (EPAvailabilityListenerExtension) extension;
        List<Object> listeners = (List<Object>) myAvailabilityListeners.get(epListenerExtension.getExtensionPointName());
        for (Iterator<Object> iterator = listeners.iterator(); iterator.hasNext();) {
          Object listener = iterator.next();
          if (listener.getClass().getName().equals(epListenerExtension.getListenerClass())) {
            iterator.remove();
            return;
          }
        }
        myLogger.warn("Failed to find EP availability listener: " + epListenerExtension.getListenerClass());
      }

      public void extensionAdded(Object extension, final PluginDescriptor pluginDescriptor) {
        EPAvailabilityListenerExtension epListenerExtension = (EPAvailabilityListenerExtension) extension;
        try {
          String epName = epListenerExtension.getExtensionPointName();

          ExtensionPointAvailabilityListener listener = (ExtensionPointAvailabilityListener) instantiate(epListenerExtension.loadListenerClass());
          addAvailabilityListener(epName, listener);
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  private Object instantiate(Class clazz) {
    ConstructorInjectionComponentAdapter adapter =
      new ConstructorInjectionComponentAdapter(Integer.toString(System.identityHashCode(new Object())), clazz);

    return adapter.getComponentInstance(getPicoContainer());
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public Throwable getCreationTrace() {
    return myCreationTrace;
  }

  public void addAvailabilityListener(String epName, ExtensionPointAvailabilityListener listener) {
    myAvailabilityListeners.put(epName, listener);
    if (hasExtensionPoint(epName)) {
      notifyAvailableListener(listener, myExtensionPoints.get(epName));
    }
  }

  public void registerExtensionPoint(final String extensionPointName, String extensionPointBeanClass) {
    registerExtensionPoint(extensionPointName, extensionPointBeanClass, new UndefinedPluginDescriptor());
  }

  public void registerExtensionPoint(final String extensionPointName, String extensionPointBeanClass, PluginDescriptor descriptor) {
    if (hasExtensionPoint(extensionPointName)) {
      if (DEBUG_REGISTRATION) {
        myLogger.error(myEPTraces.get(extensionPointName));
      }
      throw new RuntimeException("Duplicate registration for EP: " + extensionPointName);
    }

    registerExtensionPoint(new ExtensionPointImpl(extensionPointName, extensionPointBeanClass, this, myAreaInstance, myLogger, descriptor));
  }

  public void registerExtensionPoint(final ExtensionPointImpl extensionPoint) {
    final String name = extensionPoint.getName();
    myExtensionPoints.put(name, extensionPoint);
    notifyEPRegistered(extensionPoint);
    if (DEBUG_REGISTRATION) {
      myEPTraces.put(name, new Throwable("Original registration for " + name));
    }
  }

  public void registerAreaExtensionsAndPoints(final PluginDescriptor pluginDescriptor,
                                              final List<Element> extensionsPoints,
                                              final List<Element> extensions) {
    final String areaClass = getAreaClass();
    if (extensionsPoints != null) {
      for (Element element : extensionsPoints) {
        if (equal(areaClass, element.getAttributeValue(ATTRIBUTE_AREA))) {
          registerExtensionPoint(pluginDescriptor, element);
        }
      }
    }

    if (extensions != null) {
      for (Element element : extensions) {
        if (hasExtensionPoint(extractEPName(element))) {
          registerExtension(pluginDescriptor, element);
        }
        else {
          //todo check that other classes have EP
        }
      }
    }
  }

  private static boolean equal(final String areaClass, final String anotherAreaClass) {
    if (areaClass != null) {
      return areaClass.equals(anotherAreaClass);
    }
    else {
      return anotherAreaClass == null;
    }
  }

  @SuppressWarnings({"unchecked"})
  private void notifyEPRegistered(final ExtensionPoint extensionPoint) {
    List<ExtensionPointAvailabilityListener> listeners = (List<ExtensionPointAvailabilityListener>) myAvailabilityListeners.get(extensionPoint.getName());
    if (listeners != null) {
      for (final ExtensionPointAvailabilityListener listener : listeners) {
        notifyAvailableListener(listener, extensionPoint);
      }
    }
  }

  private void notifyAvailableListener(final ExtensionPointAvailabilityListener listener, final ExtensionPoint extensionPoint) {
    queueNotificationAction(new Runnable() {
      public void run() {
        listener.extensionPointRegistered(extensionPoint);
      }
    });
  }

  private void queueNotificationAction(final Runnable action) {
    if (myAvailabilityNotificationsActive) {
      action.run();
    }
    else {
      mySuspendedListenerActions.add(action);
    }
  }

  @NotNull public ExtensionPoint getExtensionPoint(String extensionPointName) {
    return getExtensionPointImpl(extensionPointName);
  }


  @SuppressWarnings({"unchecked"})
  public <T> ExtensionPoint<T> getExtensionPoint(ExtensionPointName<T> extensionPointName) {
    return (ExtensionPoint<T>)getExtensionPoint(extensionPointName.getName());
  }

  @NotNull
  private ExtensionPointImpl getExtensionPointImpl(String extensionPointName) {
    final ExtensionPointImpl extensionPoint = myExtensionPoints.get(extensionPointName);
    if (extensionPoint == null) {
      throw new IllegalArgumentException("Missing extension point: " + extensionPointName +
                                         " in area " + myAreaInstance );
    }
    return extensionPoint;
  }

  public ExtensionPoint[] getExtensionPoints() {
    return myExtensionPoints.values().toArray(new ExtensionPoint[myExtensionPoints.size()]);
  }

  public void unregisterExtensionPoint(final String extensionPointName) {
    ExtensionPoint extensionPoint = myExtensionPoints.get(extensionPointName);
    if (extensionPoint != null) {
      extensionPoint.reset();
      myExtensionPoints.remove(extensionPointName);
      notifyEPRemoved(extensionPoint);
    }
  }

  @SuppressWarnings({"unchecked"})
  private void notifyEPRemoved(final ExtensionPoint extensionPoint) {
    List<ExtensionPointAvailabilityListener> listeners = (List<ExtensionPointAvailabilityListener>) myAvailabilityListeners.get(extensionPoint.getName());
    if (listeners != null) {
      for (final ExtensionPointAvailabilityListener listener : listeners) {
        notifyUnavailableListener(extensionPoint, listener);
      }
    }
  }

  private void notifyUnavailableListener(final ExtensionPoint extensionPoint, final ExtensionPointAvailabilityListener listener) {
    queueNotificationAction(new Runnable() {
      public void run() {
        listener.extensionPointRemoved(extensionPoint);
      }
    });
  }

  public boolean hasExtensionPoint(String extensionPointName) {
    return myExtensionPoints.containsKey(extensionPointName);
  }

  public void suspendInteractions() {
    myAvailabilityNotificationsActive = false;
  }

  public void resumeInteractions() {
    myAvailabilityNotificationsActive = true;
    ExtensionPoint[] extensionPoints = getExtensionPoints();
    for (ExtensionPoint extensionPoint : extensionPoints) {
      extensionPoint.getExtensions(); // creates extensions from ComponentAdapters
    }
    for (Runnable action : mySuspendedListenerActions) {
      try {
        action.run();
      }
      catch (Exception e) {
        myLogger.error(e);
      }
    }
    mySuspendedListenerActions.clear();
  }

  public void killPendingInteractions() {
    mySuspendedListenerActions.clear();
  }

  public MutablePicoContainer[] getPluginContainers() {
    return myPluginName2picoContainer.values().toArray(new MutablePicoContainer[myPluginName2picoContainer.values().size()]);
  }

  public void removeAllComponents(final Set<ExtensionComponentAdapter> extensionAdapters) {
    for (final Object extensionAdapter : extensionAdapters) {
      ExtensionComponentAdapter componentAdapter = (ExtensionComponentAdapter)extensionAdapter;
      internalGetPluginContainer().unregisterComponent(componentAdapter.getComponentKey());
    }
  }

}
