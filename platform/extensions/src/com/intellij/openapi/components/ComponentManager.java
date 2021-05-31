// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import com.intellij.diagnostic.ActivityCategory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

import java.util.Map;

/**
 * Provides access to components. Serves as a base interface for {@link com.intellij.openapi.application.Application}
 * and {@link com.intellij.openapi.project.Project}.
 *
 * @see com.intellij.openapi.application.Application
 * @see com.intellij.openapi.project.Project
 */
@ApiStatus.NonExtendable
public interface ComponentManager extends UserDataHolder, Disposable, AreaInstance {
  /**
   * @deprecated Use {@link #getComponent(Class)} instead.
   */
  @Deprecated
  default @Nullable BaseComponent getComponent(@NotNull String name) {
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
   * Checks whether there is a component with the specified interface class.
   *
   * @param interfaceClass interface class of component to be checked
   * @return {@code true} if there is a component with the specified interface class;
   * {@code false} otherwise
   */
  default boolean hasComponent(@NotNull Class<?> interfaceClass) {
    return getPicoContainer().getComponentAdapter(interfaceClass) != null;
  }

  /**
   * @deprecated use <a href="https://plugins.jetbrains.com/docs/intellij/plugin-extensions.html">extension points</a> instead
   */
  @Deprecated
  <T> T @NotNull [] getComponents(@NotNull Class<T> baseClass);

  @ApiStatus.Internal
  @NotNull PicoContainer getPicoContainer();

  @ApiStatus.Internal
  boolean isInjectionForExtensionSupported();

  /**
   * @see com.intellij.application.Topics#subscribe
   */
  @NotNull MessageBus getMessageBus();

  /**
   * @return true when this component is disposed (e.g. the "File|Close Project" invoked or the application is exited)
   * or is about to be disposed (e.g. the {@link com.intellij.openapi.project.impl.ProjectExImpl#dispose()} was called but not completed yet)
   * <br>
   * The result is only valid inside read action because the application/project/module can be disposed at any moment.
   * (see <a href="https://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/general_threading_rules.html#readwrite-lock">more details on read actions</a>)
   */
  boolean isDisposed();

  /**
   * @deprecated Use {@link ExtensionPointName#getExtensionList(AreaInstance)}
   */
  @Deprecated
  default <T> T @NotNull [] getExtensions(@NotNull ExtensionPointName<T> extensionPointName) {
    return getExtensionArea().getExtensionPoint(extensionPointName).getExtensions();
  }

  /**
   * @return condition for this component being disposed.
   * see {@link com.intellij.openapi.application.Application#invokeLater(Runnable, Condition)} for the usage example.
   */
  @NotNull
  Condition<?> getDisposed();

  /**
   * @deprecated Use {@link #getServiceIfCreated(Class)} or {@link #getService(Class)}.
   */
  @Deprecated
  default <T> T getService(@NotNull Class<T> serviceClass, boolean createIfNeeded) {
    if (createIfNeeded) {
      return getService(serviceClass);
    }
    else {
      return getServiceIfCreated(serviceClass);
    }
  }

  <T> T getService(@NotNull Class<T> serviceClass);

  default @Nullable <T> T getServiceIfCreated(@NotNull Class<T> serviceClass) {
    return getService(serviceClass);
  }

  @Override
  default @NotNull ExtensionsArea getExtensionArea() {
    // default impl to keep backward compatibility
    throw new AbstractMethodError();
  }

  @ApiStatus.Internal
  default <T> T instantiateClass(@NotNull Class<T> aClass, @SuppressWarnings("unused") @Nullable PluginId pluginId) {
    return ReflectionUtil.newInstance(aClass, false);
  }

  @ApiStatus.Internal
  <T> T instantiateClassWithConstructorInjection(@NotNull Class<T> aClass, @NotNull Object key, @SuppressWarnings("unused") @NotNull PluginId pluginId);

  @ApiStatus.Internal
  default void logError(@NotNull Throwable error, @NotNull PluginId pluginId) {
    throw createError(error, pluginId);
  }

  @ApiStatus.Internal
  @NotNull RuntimeException createError(@NotNull Throwable error, @NotNull PluginId pluginId);

  @ApiStatus.Internal
  @NotNull RuntimeException createError(@NotNull @NonNls String message, @NotNull PluginId pluginId);

  @NotNull RuntimeException createError(@NotNull @NonNls String message, @NotNull PluginId pluginId, @Nullable Map<String, String> attachments);

  @ApiStatus.Internal
  <@NotNull T> @NotNull Class<T> loadClass(@NotNull String className, @NotNull PluginDescriptor pluginDescriptor) throws ClassNotFoundException;

  @ApiStatus.Internal
  default @NotNull <@NotNull T> T instantiateClass(@NotNull String className, @NotNull PluginDescriptor pluginDescriptor) {
    try {
      return ReflectionUtil.newInstance(loadClass(className, pluginDescriptor));
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull ActivityCategory getActivityCategory(boolean isExtension);

  @ApiStatus.Internal
  default boolean isSuitableForOs(@NotNull ExtensionDescriptor.Os os) {
    return true;
  }
}