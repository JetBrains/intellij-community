// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.ExceptionUtilRt;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.pico.CachingConstructorInjectionComponentAdapter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

/**
 * Provides access to components. Serves as a base interface for {@link com.intellij.openapi.application.Application}
 * and {@link com.intellij.openapi.project.Project}.
 *
 * @see ProjectComponent
 * @see com.intellij.openapi.application.Application
 * @see com.intellij.openapi.project.Project
 */
public interface ComponentManager extends UserDataHolder, Disposable, AreaInstance {
  /**
   * @deprecated Use {@link #getComponent(Class)} instead.
   */
  @Deprecated
  default BaseComponent getComponent(@NotNull String name) {
    return null;
  }

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
  boolean hasComponent(@NotNull Class<?> interfaceClass);

  /**
   * Gets all components whose implementation class is derived from {@code baseClass}.
   *
   * @deprecated use <a href="https://www.jetbrains.org/intellij/sdk/docs/basics/plugin_structure/plugin_extensions_and_extension_points.html">extension points</a> instead
   */
  @Deprecated
  @NotNull
  <T> T[] getComponents(@NotNull Class<T> baseClass);

  @NotNull
  PicoContainer getPicoContainer();

  /**
   * @see com.intellij.application.Topics#subscribe
   */
  @NotNull
  MessageBus getMessageBus();

  /**
   * Result is valid only in scope of a read action.
   * (see https://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/general_threading_rules.html#readwrite-lock)
   * Checking outside of a read action is meaningless, because application/project/module can be disposed at any moment.
   */
  boolean isDisposed();

  /**
   * @deprecated Use {@link ExtensionPointName#getExtensionList(AreaInstance)}
   */
  @NotNull
  @Deprecated
  default <T> T[] getExtensions(@NotNull ExtensionPointName<T> extensionPointName) {
    return getExtensionArea().getExtensionPoint(extensionPointName).getExtensions();
  }

  /**
   * @return condition for this component being disposed.
   * see {@link com.intellij.openapi.application.Application#invokeLater(Runnable, Condition)} for the usage example.
   */
  @NotNull
  Condition<?> getDisposed();

  @ApiStatus.Experimental
  default <T> T getService(@NotNull Class<T> serviceClass) {
    return getService(serviceClass, true);
  }

  @ApiStatus.Experimental
  @Nullable
  default <T> T getServiceIfCreated(@NotNull Class<T> serviceClass) {
    return getService(serviceClass, false);
  }

  @ApiStatus.Internal
  default <T> T getService(@NotNull Class<T> serviceClass, boolean createIfNeeded) {
    // default impl to keep backward compatibility
    //noinspection unchecked
    return (T)getPicoContainer().getComponentInstance(serviceClass.getName());
  }

  @NotNull
  @Override
  default ExtensionsArea getExtensionArea() {
    // default impl to keep backward compatibility
    throw new AbstractMethodError();
  }

  @ApiStatus.Internal
  default <T> T instantiateClass(@NotNull Class<T> aClass, @Nullable PluginId pluginId) {
    return ReflectionUtil.newInstance(aClass, false);
  }

  @ApiStatus.Internal
  default <T> T instantiateClassWithConstructorInjection(@NotNull Class<T> aClass, @NotNull Object key, @Nullable PluginId pluginId) {
    //noinspection unchecked
    return (T)new CachingConstructorInjectionComponentAdapter(key, aClass, null, true).getComponentInstance(getPicoContainer());
  }

  @ApiStatus.Internal
  default void logError(@NotNull Throwable error, @NotNull PluginId pluginId) {
    throw createError(error, pluginId);
  }

  @ApiStatus.Internal
  @NotNull
  default RuntimeException createError(@NotNull Throwable error, @NotNull PluginId pluginId) {
    ExceptionUtilRt.rethrowUnchecked(error);
    return new RuntimeException(error);
  }

  @ApiStatus.Internal
  @NotNull
  default RuntimeException createError(@NotNull String message, @NotNull PluginId pluginId) {
    return new RuntimeException(message);
  }

  // todo make pluginDescriptor as not-null
  @NotNull
  default <T> T instantiateExtensionWithPicoContainerOnlyIfNeeded(@Nullable String name, @Nullable PluginDescriptor pluginDescriptor) {
    try {
      //noinspection unchecked
      return (T)ReflectionUtil.newInstance(Class.forName(name));
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}