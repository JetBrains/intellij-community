/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.components;

import com.intellij.openapi.util.UserDataHolder;
import org.picocontainer.PicoContainer;

/**
 * Provides access to components. Servers as a base interface for {@link com.intellij.openapi.application.Application}
 * and {@link com.intellij.openapi.project.Project}.
 *
 * @see ApplicationComponent
 * @see ProjectComponent
 * @see com.intellij.openapi.application.Application
 * @see com.intellij.openapi.project.Project
 */
public interface ComponentManager extends UserDataHolder {
  /**
   * Gets the component by its name
   *
   * @param name the name of the component
   * @return component that matches interface class or null if there is no such component
   */
  BaseComponent getComponent(String name);
  
  /**
   * Gets the component by its interface class.
   *
   * @param interfaceClass the interface class of the component
   * @return component that matches interface class or null if there is no such component
   */
  <T> T getComponent(Class<T> interfaceClass);

  /**
   * Gets the component by its interface class but returns a specified default implementation
   * if the actualt component doesn't exist in the container.
   *
   * @param interfaceClass the interface class of the component
   * @param defaultImplementationIfAbsent the default implementation
   * @return component that matches interface class or default if there is no such component
   */
  <T> T getComponent(Class<T> interfaceClass, T defaultImplementationIfAbsent);

  /**
   * Gets interface classes for all available components.
   *
   * @return array of interface classes
   */
  Class[] getComponentInterfaces();

  /**
   * Checks whether there is a component with the specified interface class.
   *
   * @param interfaceClass interface class of component to be checked
   * @return <code>true</code> if there is a component with the specified interface class;
   * <code>false</code> otherwise
   */
  boolean hasComponent(Class interfaceClass);

  /**
   * Gets all components whose interface class is derived from <code>baseInterfaceClass</code>.
   *
   * @param baseInterfaceClass base class
   * @return array of components
   */
  <T> T[] getComponents(Class<T> baseInterfaceClass);

  PicoContainer getPicoContainer();
}
