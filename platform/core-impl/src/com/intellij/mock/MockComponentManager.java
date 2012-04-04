/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.components.StateStorageException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusFactory;
import com.intellij.util.pico.IdeaPicoContainer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.*;
import org.picocontainer.defaults.CachingComponentAdapter;
import org.picocontainer.defaults.ConstructorInjectionComponentAdapter;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MockComponentManager extends UserDataHolderBase implements ComponentManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.mock.MockComponentManager");

  private final MessageBus myMessageBus = MessageBusFactory.newMessageBus(this);
  private final MutablePicoContainer myPicoContainer;

  private final Map<Class, Object> myComponents = new HashMap<Class, Object>();
  private Collection<Object> myInitializedComponents = new HashSet<Object>();

  public MockComponentManager(@Nullable PicoContainer parent, @NotNull Disposable parentDisposable) {
    myPicoContainer = new IdeaPicoContainer(parent) {
      @Override
      @Nullable
      public Object getComponentInstance(final Object componentKey) {
        final Object o = super.getComponentInstance(componentKey);
        if (o instanceof Disposable && o != MockComponentManager.this) {
          Disposer.register(MockComponentManager.this, (Disposable)o);
        }

        return o;
      }
    };
    myPicoContainer.registerComponentInstance(this);
    Disposer.register(parentDisposable, this);
  }

  private void initComponent(Object componentInstance) {
    if (componentInstance instanceof BaseComponent && componentInstance != MockComponentManager.this) {
      ((BaseComponent)componentInstance).initComponent();
    }
  }

  @Override
  public BaseComponent getComponent(String name) {
    return null;
  }

  public <T> void registerService(Class<T> serviceInterface, Class<? extends T> serviceImplementation) {
    myPicoContainer.unregisterComponent(serviceInterface.getName());
    myPicoContainer.registerComponent(new ComponentInitializingAdapter(serviceInterface.getName(), serviceImplementation));
  }

  public <T> void registerService(Class<T> serviceImplementation) {
    registerService(serviceImplementation, serviceImplementation);
  }

  public <T> void registerService(Class<T> serviceInterface, T serviceImplementation) {
    myPicoContainer.registerComponentInstance(serviceInterface.getName(), serviceImplementation);
  }

  public <T> void addComponent(Class<T> interfaceClass, T instance) {
    myComponents.put(interfaceClass, instance);
  }

  @Override
  public <T> T getComponent(Class<T> interfaceClass) {
    final Object o = myPicoContainer.getComponentInstance(interfaceClass);
    return (T)(o != null ? o : myComponents.get(interfaceClass));
  }

  @Override
  public <T> T getComponent(Class<T> interfaceClass, T defaultImplementation) {
    return getComponent(interfaceClass);
  }

  @Override
  public boolean hasComponent(@NotNull Class interfaceClass) {
    return false;
  }

  @Override
  @NotNull
  public <T> T[] getComponents(Class<T> baseClass) {
    final List<?> list = myPicoContainer.getComponentInstancesOfType(baseClass);
    return list.toArray((T[])Array.newInstance(baseClass, 0));
  }

  @Override
  @NotNull
  public MutablePicoContainer getPicoContainer() {
    return myPicoContainer;
  }

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
    disposeComponents();
  }

  protected void disposeComponents() {
    Collection<Object> components = myInitializedComponents;
    for(Object component: components)    {
      if (component instanceof BaseComponent) {
        try {
          ((BaseComponent)component).disposeComponent();
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }
  }

  @Override
  public <T> T[] getExtensions(final ExtensionPointName<T> extensionPointName) {
    throw new UnsupportedOperationException("getExtensions()");
  }

  @NotNull
  @Override
  public Condition getDisposed() {
    return Condition.FALSE;
  }

  private class ComponentInitializingAdapter implements ComponentAdapter {
    private ComponentAdapter myDelegate;
    private boolean myInitialized = false;
    private boolean myInitializing = false;
    private boolean logSlowComponents = false;
    private final String myComponentKey;
    private final Class<? extends Object> myComponentImplementation;


    public <T> ComponentInitializingAdapter(String componentKey, Class<? extends T> componentImplementation) {

      myComponentKey = componentKey;
      myComponentImplementation = componentImplementation;
    }

    @Override
    public Object getComponentKey() {
      return myComponentKey;
    }

    @Override
    public Class getComponentImplementation() {
      return getDelegate().getComponentImplementation();
    }

    @Override
    public Object getComponentInstance(final PicoContainer container) throws PicoInitializationException, PicoIntrospectionException {
      return getDelegate().getComponentInstance(container);
    }

    @Override
    public void verify(final PicoContainer container) throws PicoIntrospectionException {
      getDelegate().verify(container);
    }

    @Override
    public void accept(final PicoVisitor visitor) {
      visitor.visitComponentAdapter(this);
      getDelegate().accept(visitor);
    }

    private ComponentAdapter getDelegate() {
      if (myDelegate == null) {
        final Object componentKey = getComponentKey();

        Class<?> implementationClass = null;

        try {
          implementationClass = myComponentImplementation;
        }
        catch (Exception e) {
          @NonNls final String message = "Error while registering component: " + myComponentKey;
        }

        assert implementationClass != null;

        myDelegate = new CachingComponentAdapter(new ConstructorInjectionComponentAdapter(componentKey, implementationClass, null, true)) {
          @Override
          public Object getComponentInstance(PicoContainer picoContainer) throws PicoInitializationException, PicoIntrospectionException {
            Object componentInstance = null;
            try {
              long startTime = myInitialized ? 0 : System.nanoTime();
              componentInstance = super.getComponentInstance(picoContainer);

              if (!myInitialized) {
                if (myInitializing) {
                }

                myInitializing = true;
                initComponent(componentInstance);
                long endTime = System.nanoTime();
                long ms = (endTime - startTime) / 1000000;
                if (ms > 10) {
                  if (logSlowComponents) {
                    LOG.info(componentInstance.getClass().getName() + " initialized in " + ms + " ms");
                  }
                }
                myInitializing = false;
                myInitialized = true;
                myInitializedComponents.add(componentInstance);
              }
            }
            catch (ProcessCanceledException e) {
              throw e;
            }
            catch (StateStorageException e) {
              throw e;
            }
            catch (Throwable t) {
              handleInitComponentError(t, componentInstance == null, componentKey.toString());
            }
            return componentInstance;
          }
        };
      }

      return myDelegate;
    }
  }

  protected void handleInitComponentError(final Throwable ex, final boolean fatal, final String componentClassName) {
    LOG.error(ex);
  }
}
