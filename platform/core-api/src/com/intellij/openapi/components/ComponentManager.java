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
 * @see ApplicationComponent
 * @see ProjectComponent
 * @see com.intellij.openapi.application.Application
 * @see com.intellij.openapi.project.Project
 */
public interface ComponentManager extends UserDataHolder, Disposable {
  /**
   * Gets the component by its name
   *
   * @param name the name of the component
   * @return component with given name or null if there is no such component
   * @see com.intellij.openapi.components.NamedComponent#getComponentName()
   */
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
   * @return <code>true</code> if there is a component with the specified interface class;
   * <code>false</code> otherwise
   */
  boolean hasComponent(@NotNull Class interfaceClass);

  /**
   * Gets all components whose implementation class is derived from <code>baseClass</code>.
   *
   * @return array of components
   * @deprecated use extension points instead
   */
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
  Condition getDisposed();
}
