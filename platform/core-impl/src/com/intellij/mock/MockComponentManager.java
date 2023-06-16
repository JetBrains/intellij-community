// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mock;

import com.intellij.diagnostic.ActivityCategory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.ExceptionUtilRt;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.ListenerDescriptor;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusOwner;
import com.intellij.util.messages.impl.MessageBusFactoryImpl;
import com.intellij.util.pico.DefaultPicoContainer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.ComponentAdapter;
import org.picocontainer.PicoContainer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MockComponentManager extends UserDataHolderBase implements ComponentManager, MessageBusOwner {
  private final MessageBus myMessageBus = MessageBusFactoryImpl.createRootBus(this);
  private final DefaultPicoContainer picoContainer;
  private final ExtensionsAreaImpl myExtensionArea;

  private final Map<Class<?>, Object> myComponents = new HashMap<>();
  private final Set<Object> myDisposableComponents = ContainerUtil.newConcurrentSet();
  private boolean myDisposed;

  public MockComponentManager(@Nullable PicoContainer parent, @NotNull Disposable parentDisposable) {
    picoContainer = new DefaultPicoContainer((DefaultPicoContainer)parent) {
      @Override
      public @Nullable Object getComponentInstance(@NotNull Object componentKey) {
        if (myDisposed) {
          throw new IllegalStateException("Cannot get " + componentKey + " from already disposed " + this);
        }

        Object o = super.getComponentInstance(componentKey);
        registerComponentInDisposer(o);
        return o;
      }
    };

    picoContainer.registerComponentInstance(getClass(), this);
    myExtensionArea = new ExtensionsAreaImpl(this);
    Disposer.register(parentDisposable, this);
  }

  public DefaultPicoContainer getPicoContainer() {
    return picoContainer;
  }

  @Override
  public <T> T instantiateClass(@NotNull Class<T> aClass, @NotNull PluginId pluginId) {
    return ReflectionUtil.newInstance(aClass, false);
  }

  @Override
  public @NotNull ExtensionsAreaImpl getExtensionArea() {
    return myExtensionArea;
  }

  @Override
  public <T> T instantiateClassWithConstructorInjection(@NotNull Class<T> aClass,
                                                        @NotNull Object key,
                                                        @NotNull PluginId pluginId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull RuntimeException createError(@NotNull Throwable error, @NotNull PluginId pluginId) {
    ExceptionUtilRt.rethrowUnchecked(error);
    return new RuntimeException(error);
  }

  @Override
  public @NotNull RuntimeException createError(@NotNull @NonNls String message, @NotNull PluginId pluginId) {
    return new RuntimeException(message);
  }

  @Override
  public @NotNull RuntimeException createError(@NotNull @NonNls String message,
                                               @Nullable Throwable error,
                                               @NotNull PluginId pluginId,
                                               @Nullable Map<String, String> attachments) {
    return new RuntimeException(message);
  }

  protected void registerComponentInDisposer(@Nullable Object o) {
    if (o instanceof Disposable && o != this && !(o instanceof MessageBus) && myDisposableComponents.add(o)) {
      Disposer.register(this, (Disposable)o);
    }
  }

  public <T> void registerService(@NotNull Class<T> serviceInterface, @NotNull Class<? extends T> serviceImplementation) {
    picoContainer.unregisterComponent(serviceInterface.getName());
    picoContainer.registerComponentImplementation(serviceInterface.getName(), serviceImplementation);
  }

  public <T> void registerService(@NotNull Class<T> serviceImplementation) {
    registerService(serviceImplementation, serviceImplementation);
  }

  public <T> void registerService(@NotNull Class<T> serviceInterface, @NotNull T serviceImplementation) {
    picoContainer.registerComponentInstance(serviceInterface.getName(), serviceImplementation);
    registerComponentInDisposer(serviceImplementation);
  }

  public <T> void registerService(@NotNull Class<T> serviceInterface, @NotNull T serviceImplementation, @NotNull Disposable parentDisposable) {
    String key = serviceInterface.getName();
    registerService(serviceInterface, serviceImplementation);
    Disposer.register(parentDisposable, () -> picoContainer.unregisterComponent(key));
  }

  public <T> void addComponent(@NotNull Class<T> interfaceClass, @NotNull T instance) {
    myComponents.put(interfaceClass, instance);
    registerComponentInDisposer(instance);
  }

  @Override
  public @Nullable <T> T getComponent(@NotNull Class<T> interfaceClass) {
    final Object o = picoContainer.getComponentInstance(interfaceClass);
    //noinspection unchecked
    return (T)(o != null ? o : myComponents.get(interfaceClass));
  }

  @Override
  public <T> T getService(@NotNull Class<T> serviceClass) {
    T result = picoContainer.getService(serviceClass);
    registerComponentInDisposer(result);
    return result;
  }

  public final ComponentAdapter getComponentAdapter(@NotNull Object componentKey) {
    return picoContainer.getComponentAdapter(componentKey);
  }

  @Override
  public final boolean hasComponent(@NotNull Class<?> interfaceClass) {
    return getComponentAdapter(interfaceClass) != null;
  }

  @Override
  public boolean isInjectionForExtensionSupported() {
    return false;
  }

  @Override
  public @NotNull MessageBus getMessageBus() {
    return myMessageBus;
  }

  @Override
  public boolean isDisposed() {
    return myDisposed;
  }

  @Override
  public void dispose() {
    Disposer.dispose(myMessageBus);
    myDisposed = true;
  }

  @Override
  public @NotNull Condition<?> getDisposed() {
    return Conditions.alwaysFalse();
  }

  @Override
  public @NotNull Object createListener(@NotNull ListenerDescriptor descriptor) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> @NotNull Class<T> loadClass(@NotNull String className, @NotNull PluginDescriptor pluginDescriptor) throws ClassNotFoundException {
    //noinspection unchecked
    return (Class<T>)Class.forName(className);
  }

  @Override
  public @NotNull ActivityCategory getActivityCategory(boolean isExtension) {
    return isExtension ? ActivityCategory.APP_EXTENSION : ActivityCategory.APP_SERVICE;
  }

  @Override
  public final @NotNull <T> T instantiateClass(@NotNull String className, @NotNull PluginDescriptor pluginDescriptor) {
    try {
      return ReflectionUtil.newInstance(loadClass(className, pluginDescriptor));
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}
