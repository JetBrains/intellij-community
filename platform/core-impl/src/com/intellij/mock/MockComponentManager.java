// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.mock;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.impl.MessageBusFactoryImpl;
import com.intellij.util.pico.DefaultPicoContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.PicoContainer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MockComponentManager extends UserDataHolderBase implements ComponentManager {
  private final MessageBus myMessageBus = new MessageBusFactoryImpl().createMessageBus(this);
  private final MutablePicoContainer myPicoContainer;

  private final Map<Class<?>, Object> myComponents = new HashMap<>();
  private final Set<Object> myDisposableComponents = ContainerUtil.newConcurrentSet();
  private boolean myDisposed;

  public MockComponentManager(@Nullable PicoContainer parent, @NotNull Disposable parentDisposable) {
    myPicoContainer = new DefaultPicoContainer(parent) {
      @Override
      @Nullable
      public Object getComponentInstance(final Object componentKey) {
        if (myDisposed) {
          throw new IllegalStateException("Cannot get " + componentKey + " from already disposed " + this);
        }
        final Object o = super.getComponentInstance(componentKey);
        registerComponentInDisposer(o);
        return o;
      }
    };

    myPicoContainer.registerComponentInstance(this);
    Disposer.register(parentDisposable, this);
  }

  private void registerComponentInDisposer(@Nullable Object o) {
    if (o instanceof Disposable && o != this && !(o instanceof MessageBus)) {
      if (myDisposableComponents.add(o))
        Disposer.register(this, (Disposable)o);
    }
  }

  @Override
  public BaseComponent getComponent(@NotNull String name) {
    return null;
  }

  public <T> void registerService(@NotNull Class<T> serviceInterface, @NotNull Class<? extends T> serviceImplementation) {
    myPicoContainer.unregisterComponent(serviceInterface.getName());
    myPicoContainer.registerComponentImplementation(serviceInterface.getName(), serviceImplementation);
  }

  public <T> void registerService(@NotNull Class<T> serviceImplementation) {
    registerService(serviceImplementation, serviceImplementation);
  }

  public <T> void registerService(@NotNull Class<T> serviceInterface, @NotNull T serviceImplementation) {
    myPicoContainer.registerComponentInstance(serviceInterface.getName(), serviceImplementation);
    registerComponentInDisposer(serviceImplementation);
  }

  public <T> void addComponent(@NotNull Class<T> interfaceClass, @NotNull T instance) {
    myComponents.put(interfaceClass, instance);
    registerComponentInDisposer(instance);
  }

  @Nullable
  @Override
  public <T> T getComponent(@NotNull Class<T> interfaceClass) {
    final Object o = myPicoContainer.getComponentInstance(interfaceClass);
    //noinspection unchecked
    return (T)(o != null ? o : myComponents.get(interfaceClass));
  }

  @Override
  public <T> T getComponent(@NotNull Class<T> interfaceClass, T defaultImplementation) {
    return getComponent(interfaceClass);
  }

  @Override
  public boolean hasComponent(@NotNull Class interfaceClass) {
    return false;
  }

  @SuppressWarnings("unchecked")
  @Override
  @NotNull
  public <T> T[] getComponents(@NotNull Class<T> baseClass) {
    final List<T> list = myPicoContainer.getComponentInstancesOfType(baseClass);
    return list.toArray(ArrayUtil.newArray(baseClass, 0));
  }

  @Override
  @NotNull
  public MutablePicoContainer getPicoContainer() {
    return myPicoContainer;
  }

  @NotNull
  @Override
  public MessageBus getMessageBus() {
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

  @NotNull
  @Override
  public <T> T[] getExtensions(@NotNull final ExtensionPointName<T> extensionPointName) {
    throw new UnsupportedOperationException("getExtensions()");
  }

  @NotNull
  @Override
  public Condition<?> getDisposed() {
    return Conditions.alwaysFalse();
  }
}
