// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components;

import com.intellij.diagnostic.ActivityCategory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.client.ClientKind;
import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.*;

import java.util.Collections;
import java.util.List;
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
  @ApiStatus.ScheduledForRemoval
  default @Nullable BaseComponent getComponent(@NotNull String name) {
    return null;
  }

  /**
   * Gets the component by its interface class.
   *
   * @param interfaceClass the interface class of the component
   * @return component that matches interface class or null if there is no such component
   * @deprecated Components are deprecated, please see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-components.html">SDK Docs</a> for guidelines on migrating to other APIs.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  <T> T getComponent(@NotNull Class<T> interfaceClass);

  /**
   * Checks whether there is a component with the specified interface class.
   *
   * @param interfaceClass interface class of a component to be checked
   * @return {@code true} if there is a component with the specified interface class;
   * {@code false} otherwise
   */
  boolean hasComponent(@NotNull Class<?> interfaceClass);

  @ApiStatus.Internal
  boolean isInjectionForExtensionSupported();

  /**
   * @deprecated not all implementations support this functionality; 
   * call {@link com.intellij.openapi.application.Application#getMessageBus()} or {@link com.intellij.openapi.project.Project#getMessageBus()} 
   * instead.
   */
  @Deprecated
  @NotNull MessageBus getMessageBus();

  /**
   * @return true when this component is disposed (e.g. the "File|Close Project" invoked or the application is exited)
   * or is about to be disposed (e.g. the {@link com.intellij.openapi.project.impl.ProjectExImpl#dispose()} was called but not completed yet)
   * <br>
   * The result is only valid inside read action because the application/project/module can be disposed at any moment.
   * (see <a href="https://plugins.jetbrains.com/docs/intellij/threading-model.html">more details on read actions</a>)
   */
  boolean isDisposed();

  /**
   * @return condition for this component being disposed.
   * see {@link com.intellij.openapi.application.Application#invokeLater(Runnable, Condition)} for the usage example.
   */
  @NotNull
  Condition<?> getDisposed();

  /**
   * Gets the service by its interface class.
   * <p>
   * <p>This method is thread-safe and does not require wrapping in a read or write action.
   * <p>
   * If container is disposed, a {@link java.util.concurrent.CancellationException} will be thrown.
   * Note that accessing {@link #isDisposed()} is not recommended - it's better to rely on cancellation.
   * Container disposal is treated as a cancellation.
   * <p>
   * While internally {@link com.intellij.serviceContainer.AlreadyDisposedException} may be thrown
   * (which extends {@link java.util.concurrent.CancellationException}),
   * callers should only rely on {@link java.util.concurrent.CancellationException} being thrown.
   *
   * @param serviceClass service interface class
   * @return service instance, or null if no service found
   * @throws java.util.concurrent.CancellationException if the container is disposed
   */
  <T> T getService(@NotNull Class<T> serviceClass);

  @ApiStatus.Internal
  default <T> T getServiceForClient(@NotNull Class<T> serviceClass) {
    return getService(serviceClass);
  }

  /**
   * Collects all services registered with matching client="..." attribute in xml.
   * Take a look at {@link com.intellij.openapi.client.ClientSession}
   */
  @ApiStatus.Internal
  @ApiStatus.Experimental
  default @NotNull @Unmodifiable <T> List<T> getServices(@NotNull Class<T> serviceClass, ClientKind client) {
    T service = getService(serviceClass);
    //noinspection SSBasedInspection
    return service == null ? Collections.emptyList() : Collections.singletonList(service);
  }

  default @Nullable <T> T getServiceIfCreated(@NotNull Class<T> serviceClass) {
    return getService(serviceClass);
  }

  @Override
  @NotNull ExtensionsArea getExtensionArea();

  @ApiStatus.Internal
  <T> T instantiateClass(@NotNull Class<T> aClass, @NotNull PluginId pluginId);

  @ApiStatus.Internal
  <T> T instantiateClassWithConstructorInjection(@NotNull Class<T> aClass, @NotNull Object key, @NotNull PluginId pluginId);

  @ApiStatus.Internal
  default void logError(@NotNull Throwable error, @NotNull PluginId pluginId) {
    throw createError(error, pluginId);
  }

  @ApiStatus.Internal
  @NotNull RuntimeException createError(@NotNull Throwable error, @NotNull PluginId pluginId);

  @ApiStatus.Internal
  @NotNull RuntimeException createError(@NotNull @NonNls String message, @NotNull PluginId pluginId);

  @NotNull RuntimeException createError(@NotNull @NonNls String message,
                                        @Nullable Throwable error,
                                        @NotNull PluginId pluginId,
                                        @Nullable Map<String, String> attachments);

  @ApiStatus.Internal
  <T> @NotNull Class<T> loadClass(@NotNull String className, @NotNull PluginDescriptor pluginDescriptor) throws ClassNotFoundException;

  @ApiStatus.Internal
  @NotNull <T> T instantiateClass(@NotNull String className, @NotNull PluginDescriptor pluginDescriptor);

  @ApiStatus.Internal
  @NotNull ActivityCategory getActivityCategory(boolean isExtension);
}