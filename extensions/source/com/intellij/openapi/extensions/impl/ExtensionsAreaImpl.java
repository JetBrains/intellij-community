/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.*;
import org.apache.commons.collections.MultiHashMap;
import org.apache.commons.collections.MultiMap;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.output.XMLOutputter;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.PicoContainer;
import org.picocontainer.defaults.ConstructorInjectionComponentAdapter;
import org.picocontainer.defaults.DefaultPicoContainer;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.util.*;

public class ExtensionsAreaImpl implements ExtensionsArea {
  private final LogProvider myLogger;

  static Map ourDefaultEPs = new HashMap();

  static {
    ourDefaultEPs.put(EPAvailabilityListenerExtension.EXTENSION_POINT_NAME, EPAvailabilityListenerExtension.class.getName());
  }

  private static boolean DEBUG_REGISTRATION = true;

  private AreaPicoContainerImpl myPicoContainer;
  private Throwable myCreationTrace = null;
  private Map myExtensionPoints = new HashMap();
  private Map myEPTraces = new HashMap();
  private MultiMap myAvailabilityListeners = new MultiHashMap();
  private List mySuspendedListenerActions = new ArrayList();
  private boolean myAvailabilityNotificationsActive = true;

  private final AreaInstance myAreaInstance;
  private final String myAreaClass;
  private Map myExtensionElement2extension = new HashMap();
  private Map myPluginName2picoContainer = new HashMap();

  public ExtensionsAreaImpl(String areaClass, AreaInstance areaInstance, PicoContainer parentPicoContainer, LogProvider logger) {
    if (DEBUG_REGISTRATION) {
      myCreationTrace = new Throwable("Area creation trace");
    }
    myAreaClass = areaClass;
    myAreaInstance = areaInstance;
    myPicoContainer = new AreaPicoContainerImpl(parentPicoContainer, this);
    //if (areaInstance != null) {
    //  myPicoContainer.registerComponentInstance(areaInstance);
    //}
    myLogger = logger;
    initialize();
  }

  public ExtensionsAreaImpl(MutablePicoContainer picoContainer, LogProvider logger) {
    this(null, null, picoContainer, logger);
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
    registerExtensionPoint(new DefaultPluginDescriptor(pluginName), extensionPointElement);
  }

  public void registerExtensionPoint(PluginDescriptor pluginDescriptor, Element extensionPointElement) {
    assert pluginDescriptor.getPluginName() != null;
    String epName = pluginDescriptor.getPluginName() + '.' + extensionPointElement.getAttributeValue("name");
    String className = extensionPointElement.getAttributeValue("beanClass");
    if (className == null) {
      className = extensionPointElement.getAttributeValue("interface");
    }
    if (className == null) {
      throw new RuntimeException("No class specified for extension point: " + epName);
    }
    registerExtensionPoint(epName, className);
  }

  public void registerExtension(final String pluginName, final Element extensionElement) {
    registerExtension(new DefaultPluginDescriptor(pluginName), extensionElement);
  }

  public void registerExtension(final PluginDescriptor pluginDescriptor, final Element extensionElement) {
    final String pluginName = pluginDescriptor.getPluginName();

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
      try {
        Class implementationClass;
        if (pluginDescriptor.getPluginClassLoader() == null) {
          implementationClass = Class.forName(implClass);
        }
        else {
          implementationClass = Class.forName(implClass, true, pluginDescriptor.getPluginClassLoader());
        }
        adapter = new ExtensionComponentAdapter(implementationClass, extensionElement, getPluginContainer(pluginName), pluginDescriptor);
      }
      catch (ClassNotFoundException e) {
        myLogger.warn("Extension implementation class not found: " + implClass);
        myExtensionElement2extension.put(extensionElement, null);
        return;
      }
    }
    else {
      final ExtensionPoint extensionPoint = getExtensionPoint(epName);
      adapter = new ExtensionComponentAdapter(extensionPoint.getExtensionClass(), extensionElement, getPluginContainer(pluginName), pluginDescriptor);
    }
    myExtensionElement2extension.put(extensionElement, adapter);
    internalGetPluginContainer(pluginName).registerComponent(adapter);
    getExtensionPointImpl(epName).registerExtensionAdapter(adapter);
  }

  private String extractEPName(final Element extensionElement) {
    String epName = extensionElement.getAttributeValue("point");
    if (epName == null) {
      Namespace namespace = extensionElement.getNamespace();
      epName = namespace.getURI() + '.' + extensionElement.getName();
    }
    return epName;
  }

  public PicoContainer getPluginContainer(String pluginName) {
    return internalGetPluginContainer(pluginName);
  }

  private MutablePicoContainer internalGetPluginContainer(String pluginName) {
    DefaultPicoContainer pluginContainer = (DefaultPicoContainer) myPluginName2picoContainer.get(pluginName);
    if (pluginContainer == null) {
      pluginContainer = new DefaultPicoContainer(myPicoContainer);
      myPicoContainer.addChildContainer(pluginContainer);
      myPluginName2picoContainer.put(pluginName, pluginContainer);
    }
    return pluginContainer;
  }

  private void disposePluginContainer(String pluginName) {
    DefaultPicoContainer pluginContainer = (DefaultPicoContainer) myPluginName2picoContainer.remove(pluginName);
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
      xmlOutputter.setIndent("  ");
      xmlOutputter.setTextNormalize(true);
      xmlOutputter.setNewlines(true);
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
    ExtensionComponentAdapter adapter = (ExtensionComponentAdapter) myExtensionElement2extension.remove(extensionElement);
    if (adapter == null) return;
    if (getExtensionPointImpl(epName).unregisterComponentAdapter(adapter)) {
      MutablePicoContainer pluginContainer = internalGetPluginContainer(pluginName);
      pluginContainer.unregisterComponent(adapter.getComponentKey());
      if (pluginContainer.getComponentAdapters().size() == 0) {
        disposePluginContainer(pluginName);
      }
    }
  }

  public void initialize() {
    for (Iterator iterator = ourDefaultEPs.keySet().iterator(); iterator.hasNext();) {
      String epName = (String) iterator.next();
      registerExtensionPoint(epName, (String) ourDefaultEPs.get(epName));
    }
    getExtensionPoint(EPAvailabilityListenerExtension.EXTENSION_POINT_NAME).addExtensionPointListener(new ExtensionPointListener() {
      public void extensionRemoved(Object extension) {
        EPAvailabilityListenerExtension epListenerExtension = (EPAvailabilityListenerExtension) extension;
        List listeners = (List) myAvailabilityListeners.get(epListenerExtension.getExtensionPointName());
        for (Iterator iterator = listeners.iterator(); iterator.hasNext();) {
          Object listener = iterator.next();
          if (listener.getClass().getName().equals(epListenerExtension.getListenerClass())) {
            iterator.remove();
            return;
          }
        }
        myLogger.warn("Failed to find EP availability listener: " + epListenerExtension.getListenerClass());
      }

      public void extensionAdded(Object extension) {
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

  public Object instantiate(Class clazz) {
    ConstructorInjectionComponentAdapter adapter = new ConstructorInjectionComponentAdapter(System.identityHashCode(new Object()) + "", clazz);
    return adapter.getComponentInstance(getPicoContainer());
  }

  public Throwable getCreationTrace() {
    return myCreationTrace;
  }

  public void addAvailabilityListener(String epName, ExtensionPointAvailabilityListener listener) {
    myAvailabilityListeners.put(epName, listener);
    if (hasExtensionPoint(epName)) {
      notifyAvailableListener(listener, (ExtensionPoint) myExtensionPoints.get(epName));
    }
  }

  public void registerExtensionPoint(final String extensionPointName, String extensionPointBeanClass) {
    registerExtensionPoint(extensionPointName, extensionPointBeanClass, new UndefinedPluginDescriptor());
  }

  public void registerExtensionPoint(final String extensionPointName, String extensionPointBeanClass, PluginDescriptor descriptor) {
    if (hasExtensionPoint(extensionPointName)) {
      if (DEBUG_REGISTRATION) {
        myLogger.error((Throwable) myEPTraces.get(extensionPointName));
      }
      throw new RuntimeException("Duplicate registration for EP: " + extensionPointName);
    }

    ExtensionPointImpl extensionPoint = new ExtensionPointImpl(extensionPointName, extensionPointBeanClass, this, myAreaInstance, myLogger, descriptor);
    myExtensionPoints.put(extensionPointName, extensionPoint);
    notifyEPRegistered(extensionPoint);
    if (DEBUG_REGISTRATION) {
      myEPTraces.put(extensionPointName, new Throwable("Original registration for " + extensionPointName));
    }
  }

  private void notifyEPRegistered(final ExtensionPoint extensionPoint) {
    List listeners = (List) myAvailabilityListeners.get(extensionPoint.getName());
    if (listeners != null) {
      for (Iterator i = listeners.iterator(); i.hasNext();) {
        final ExtensionPointAvailabilityListener listener = (ExtensionPointAvailabilityListener) i.next();
        notifyAvailableListener(listener, extensionPoint);
      }
    }
  }

  private void notifyAvailableListener(final ExtensionPointAvailabilityListener listener, final ExtensionPoint extensionPoint) {
    Runnable action = new Runnable() {
      public void run() {
        listener.extensionPointRegistered(extensionPoint);
      }
    };
    if (myAvailabilityNotificationsActive) {
      action.run();
    }
    else {
      mySuspendedListenerActions.add(action);
    }
  }

  public ExtensionPoint getExtensionPoint(String extensionPointName) {
    return getExtensionPointImpl(extensionPointName);
  }

  private ExtensionPointImpl getExtensionPointImpl(String extensionPointName) {
    if (!hasExtensionPoint(extensionPointName)) {
      throw new IllegalArgumentException("Missing extension point: " + extensionPointName +
          " in area " + myAreaInstance );
    }
    return (ExtensionPointImpl) myExtensionPoints.get(extensionPointName);
  }

  public ExtensionPoint[] getExtensionPoints() {
    return (ExtensionPoint[]) myExtensionPoints.values().toArray(new ExtensionPoint[myExtensionPoints.size()]);
  }

  public void unregisterExtensionPoint(final String extensionPointName) {
    ExtensionPoint extensionPoint = (ExtensionPoint) myExtensionPoints.get(extensionPointName);
    if (extensionPoint != null) {
      extensionPoint.reset();
      myExtensionPoints.remove(extensionPointName);
      notifyEPRemoved(extensionPoint);
    }
  }

  private void notifyEPRemoved(final ExtensionPoint extensionPoint) {
    List listeners = (List) myAvailabilityListeners.get(extensionPoint.getName());
    if (listeners != null) {
      for (Iterator i = listeners.iterator(); i.hasNext();) {
        final ExtensionPointAvailabilityListener listener = (ExtensionPointAvailabilityListener) i.next();
        Runnable action = new Runnable() {
          public void run() {
            listener.extensionPointRemoved(extensionPoint);
          }
        };
        if (myAvailabilityNotificationsActive) {
          action.run();
        }
        else {
          mySuspendedListenerActions.add(action);
        }
      }
    }
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
    for (int i = 0; i < extensionPoints.length; i++) {
      ExtensionPoint extensionPoint = extensionPoints[i];
      extensionPoint.getExtensions(); // creates extensions from ComponentAdapters
    }
    for (Iterator i = mySuspendedListenerActions.iterator(); i.hasNext();) {
      Runnable action = (Runnable) i.next();
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
    return (MutablePicoContainer[])myPluginName2picoContainer.values().toArray(new MutablePicoContainer[myPluginName2picoContainer.values().size()]);
  }

  public void removeAllComponents(final Set extensionAdapters) {
    for (Iterator iterator = extensionAdapters.iterator(); iterator.hasNext();) {
      ExtensionComponentAdapter componentAdapter = (ExtensionComponentAdapter)iterator.next();
      final String pluginName = componentAdapter.getPluginName();
      internalGetPluginContainer(pluginName).unregisterComponent(componentAdapter.getComponentKey());
    }
  }

}
