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
package com.intellij.mock;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusFactory;
import com.intellij.util.pico.DefaultPicoContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.PicoContainer;

import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MockComponentManager extends UserDataHolderBase implements ComponentManager {
  private final MessageBus myMessageBus = MessageBusFactory.newMessageBus(this);
  private final MutablePicoContainer myPicoContainer;

  private final Map<Class, Object> myComponents = new HashMap<Class, Object>();
  private final Set<Object> myDisposableComponents = ContainerUtil.newConcurrentSet();

  public MockComponentManager(@Nullable PicoContainer parent, @NotNull Disposable parentDisposable) {
    myPicoContainer = new DefaultPicoContainer(parent) {
      @Override
      @Nullable
      public Object getComponentInstance(final Object componentKey) {
        final Object o = super.getComponentInstance(componentKey);
        registerComponentInDisposer(o);
        return o;
      }
    };

    myPicoContainer.registerComponentInstance(this);
    Disposer.register(parentDisposable, this);
  }

  private void registerComponentInDisposer(@Nullable Object o) {
    if (o instanceof Disposable && o != this) {
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

  @Override
  @NotNull
  public <T> T[] getComponents(@NotNull Class<T> baseClass) {
    final List<?> list = myPicoContainer.getComponentInstancesOfType(baseClass);
    return list.toArray((T[])Array.newInstance(baseClass, 0));
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
    return false;
  }

  @Override
  public void dispose() {
    myMessageBus.dispose();
  }

  @NotNull
  @Override
  public <T> T[] getExtensions(@NotNull final ExtensionPointName<T> extensionPointName) {
    throw new UnsupportedOperationException("getExtensions()");
  }

  @NotNull
  @Override
  public Condition getDisposed() {
    return Conditions.alwaysFalse();
  }
}
