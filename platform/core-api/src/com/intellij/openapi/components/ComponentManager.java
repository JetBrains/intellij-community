// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.PicoContainer;

/**
 * Provides access to components. Serves as a base interface for {@link com.intellij.openapi.application.Application}
 * and {@link com.intellij.openapi.project.Project}.
 *
 * @see ProjectComponent
 * @see com.intellij.openapi.application.Application
 * @see com.intellij.openapi.project.Project
 */
public interface ComponentManager extends UserDataHolder, Disposable {
  /**
   * @deprecated Use {@link #getComponent(Class)} instead.
   */
  @Deprecated
  BaseComponent getComponent(@NotNull String name);

  /**
   * Gets the component by its interface class.
   *
   * @param interfaceClass the interface class of the component
   * @return component that matches interface class or null if there is no such component
   */
  <T> T getComponent(@NotNull Class<T> interfaceClass);

  /**
   * Gets the component by its interface class but returns a specified default implementation
   * if the actual component doesn't exist in the container.
   *
   * @param interfaceClass the interface class of the component
   * @param defaultImplementationIfAbsent the default implementation
   * @return component that matches interface class or default if there is no such component
   */
  <T> T getComponent(@NotNull Class<T> interfaceClass, T defaultImplementationIfAbsent);

  /**
   * Checks whether there is a component with the specified interface class.
   *
   * @param interfaceClass interface class of component to be checked
   * @return {@code true} if there is a component with the specified interface class;
   * {@code false} otherwise
   */
  boolean hasComponent(@NotNull Class interfaceClass);

  /**
   * Gets all components whose implementation class is derived from {@code baseClass}.
   *
   * @deprecated use <a href="http://www.jetbrains.org/intellij/sdk/docs/basics/plugin_structure/plugin_extensions_and_extension_points.html">extension points</a> instead
   */
  @Deprecated
  @NotNull
  <T> T[] getComponents(@NotNull Class<T> baseClass);

  @NotNull
  PicoContainer getPicoContainer();

  @NotNull
  MessageBus getMessageBus();

  boolean isDisposed();

  @NotNull
  <T> T[] getExtensions(@NotNull ExtensionPointName<T> extensionPointName);

  /**
   * @return condition for this component being disposed.
   * see {@link com.intellij.openapi.application.Application#invokeLater(Runnable, Condition)} for the usage example.
   */
  @NotNull
  Condition<?> getDisposed();
}