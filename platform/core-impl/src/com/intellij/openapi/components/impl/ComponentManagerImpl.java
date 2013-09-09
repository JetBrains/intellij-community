/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.components.impl;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.components.ex.ComponentManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ReflectionCache;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusFactory;
import com.intellij.util.pico.IdeaPicoContainer;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.picocontainer.*;
import org.picocontainer.defaults.CachingComponentAdapter;
import org.picocontainer.defaults.ConstructorInjectionComponentAdapter;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author mike
 */
public abstract class ComponentManagerImpl extends UserDataHolderBase implements ComponentManagerEx, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.components.ComponentManager");

  private final Map<Class, Object> myInitializedComponents = new ConcurrentHashMap<Class, Object>();

  private boolean myComponentsCreated = false;

  private MutablePicoContainer myPicoContainer;
  private volatile boolean myDisposed = false;
  private volatile boolean myDisposeCompleted = false;

  private MessageBus myMessageBus;

  private final ComponentManagerConfigurator myConfigurator = new ComponentManagerConfigurator(this);
  private final ComponentManager myParentComponentManager;
  private Boolean myHeadless;
  private ComponentsRegistry myComponentsRegistry = new ComponentsRegistry();
  private final Condition myDisposedCondition = new Condition() {
    @Override
    public boolean value(final Object o) {
      return isDisposed();
    }
  };

  protected ComponentManagerImpl(ComponentManager parentComponentManager) {
    myParentComponentManager = parentComponentManager;
    bootstrapPicoContainer(toString());
  }
  protected ComponentManagerImpl(ComponentManager parentComponentManager, @NotNull String name) {
    myParentComponentManager = parentComponentManager;
    bootstrapPicoContainer(name);
  }

  public void init() {
    createComponents();
    getComponents();
  }

  @NotNull
  @Override
  public MessageBus getMessageBus() {
    assert !myDisposeCompleted && !myDisposed : "Already disposed";
    assert myMessageBus != null : "Not initialized yet";
    return myMessageBus;
  }

  public boolean isComponentsCreated() {
    return myComponentsCreated;
  }

  private void createComponents() {
    try {
      myComponentsRegistry.loadClasses();

      Class[] componentInterfaces = myComponentsRegistry.getComponentInterfaces();
      for (Class componentInterface : componentInterfaces) {
        ProgressIndicatorProvider.checkCanceled();
        createComponent(componentInterface);
      }
    }
    finally {
      myComponentsCreated = true;
    }
  }

  protected synchronized Object createComponent(Class componentInterface) {
    final Object component = getPicoContainer().getComponentInstance(componentInterface.getName());
    LOG.assertTrue(component != null, "Can't instantiate component for: " + componentInterface);
    return component;
  }

  protected synchronized void disposeComponents() {
    assert !myDisposeCompleted : "Already disposed!";

    final List<Object> components = myComponentsRegistry.getRegisteredImplementations();
    myDisposed = true;

    for (int i = components.size() - 1; i >= 0; i--) {
      Object component = components.get(i);
      if (component instanceof BaseComponent) {
        try {
          ((BaseComponent)component).disposeComponent();
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }

    myComponentsCreated = false;
  }

  @SuppressWarnings({"unchecked"})
  @Nullable
  protected <T> T getComponentFromContainer(Class<T> interfaceClass) {
    final T initializedComponent = (T)myInitializedComponents.get(interfaceClass);
    if (initializedComponent != null) return initializedComponent;

    synchronized (this) {
      if (myComponentsRegistry == null || !myComponentsRegistry.containsInterface(interfaceClass)) {
        return null;
      }

      Object lock = myComponentsRegistry.getComponentLock(interfaceClass);

      synchronized (lock) {
        T dcl = (T)myInitializedComponents.get(interfaceClass);
        if (dcl != null) return dcl;

        T component = (T)getPicoContainer().getComponentInstance(interfaceClass.getName());
        if (component == null) {
          component = (T)createComponent(interfaceClass);
        }

        if (component == null) {
          throw new IncorrectOperationException("createComponent() returns null for: " + interfaceClass);
        }

        myInitializedComponents.put(interfaceClass, component);

        if (component instanceof com.intellij.openapi.Disposable) {
          Disposer.register(this, (com.intellij.openapi.Disposable)component);
        }

        return component;
      }
    }
  }

  @Override
  public <T> T getComponent(@NotNull Class<T> interfaceClass) {
    assert !myDisposeCompleted : "Already disposed: "+this;
    return getComponent(interfaceClass, null);
  }

  @Override
  public <T> T getComponent(@NotNull Class<T> interfaceClass, T defaultImplementation) {
    final T fromContainer = getComponentFromContainer(interfaceClass);
    if (fromContainer != null) return fromContainer;
    if (defaultImplementation != null) return defaultImplementation;
    return null;
  }

  @Nullable
  protected static ProgressIndicator getProgressIndicator() {
    return ProgressIndicatorProvider.getGlobalProgressIndicator();
  }

  protected float getPercentageOfComponentsLoaded() {
    return myComponentsRegistry.getPercentageOfComponentsLoaded();
  }

  @Override
  public void initializeComponent(Object component, boolean service) {
  }

  protected void handleInitComponentError(Throwable ex, String componentClassName, ComponentConfig config) {
    LOG.error(ex);
  }

  @Override
  @SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext"})
  public synchronized void registerComponent(final ComponentConfig config, final PluginDescriptor pluginDescriptor) {
    if (!config.prepareClasses(isHeadless())) return;

    config.pluginDescriptor =  pluginDescriptor;
    myComponentsRegistry.registerComponent(config);
  }

  public synchronized void registerComponentImplementation(Class componentKey, Class componentImplementation) {
    getPicoContainer().registerComponentImplementation(componentKey.getName(), componentImplementation);
    myInitializedComponents.remove(componentKey);
  }

  @TestOnly
  public synchronized <T> T registerComponentInstance(Class<T> componentKey, T componentImplementation) {
    getPicoContainer().unregisterComponent(componentKey.getName());
    getPicoContainer().registerComponentInstance(componentKey.getName(), componentImplementation);
    @SuppressWarnings("unchecked") T t = (T)myInitializedComponents.remove(componentKey);
    return t;
  }

  @Override
  public synchronized boolean hasComponent(@NotNull Class interfaceClass) {
    return myComponentsRegistry != null && myComponentsRegistry.containsInterface(interfaceClass);
  }

  protected synchronized Object[] getComponents() {
    Class[] componentClasses = myComponentsRegistry.getComponentInterfaces();
    ArrayList<Object> components = new ArrayList<Object>(componentClasses.length);
    for (Class<?> interfaceClass : componentClasses) {
      ProgressIndicatorProvider.checkCanceled();
      Object component = getComponent(interfaceClass);
      if (component != null) components.add(component);
    }
    return ArrayUtil.toObjectArray(components);
  }

  @Override
  @SuppressWarnings({"unchecked"})
  @NotNull
  public synchronized <T> T[] getComponents(@NotNull Class<T> baseClass) {
    return myComponentsRegistry.getComponentsByType(baseClass);
  }

  @Override
  @NotNull
  public MutablePicoContainer getPicoContainer() {
    assert !myDisposeCompleted : "Already disposed";
    return myPicoContainer;
  }

  protected MutablePicoContainer createPicoContainer() {
    MutablePicoContainer result;

    if (myParentComponentManager != null) {
      result = new IdeaPicoContainer(myParentComponentManager.getPicoContainer());
    }
    else {
      result = new IdeaPicoContainer();
    }

    return result;
  }

  @Override
  public synchronized BaseComponent getComponent(@NotNull String name) {
    return myComponentsRegistry.getComponentByName(name);
  }

  protected boolean isComponentSuitable(Map<String, String> options) {
    return !isTrue(options, "internal") || ApplicationManager.getApplication().isInternal();
  }

  private static boolean isTrue(Map<String, String> options, @NonNls final String option) {
    return options != null && options.containsKey(option) && Boolean.valueOf(options.get(option)).booleanValue();
  }

  @Override
  public synchronized void dispose() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myDisposeCompleted = true;

    if (myMessageBus != null) {
      myMessageBus.dispose();
      myMessageBus = null;
    }

    myInitializedComponents.clear();
    myComponentsRegistry = null;
    myPicoContainer = null;
  }

  @Override
  public boolean isDisposed() {
    return myDisposed || temporarilyDisposed;
  }

  protected volatile boolean temporarilyDisposed = false;
  @TestOnly
  public void setTemporarilyDisposed(boolean disposed) {
    temporarilyDisposed = disposed;
  }

  protected void loadComponentsConfiguration(ComponentConfig[] components, @Nullable PluginDescriptor descriptor, boolean defaultProject) {
    myConfigurator.loadComponentsConfiguration(components, descriptor, defaultProject);
  }

  protected void bootstrapPicoContainer(@NotNull String name) {
    myPicoContainer = createPicoContainer();

    myMessageBus = MessageBusFactory.newMessageBus(name, myParentComponentManager == null ? null : myParentComponentManager.getMessageBus());
    final MutablePicoContainer picoContainer = getPicoContainer();
    picoContainer.registerComponentInstance(MessageBus.class, myMessageBus);
  }


  protected ComponentManager getParentComponentManager() {
    return myParentComponentManager;
  }

  private boolean isHeadless() {
    if (myHeadless == null) {
      myHeadless = ApplicationManager.getApplication().isHeadlessEnvironment();
    }

    return myHeadless.booleanValue();
  }


  @Override
  public void registerComponent(final ComponentConfig config) {
    registerComponent(config, null);
  }

  @NotNull
  public ComponentConfig[] getComponentConfigurations() {
    return myComponentsRegistry.getComponentConfigurations();
  }

  @Nullable
  public Object getComponent(final ComponentConfig componentConfig) {
    return getPicoContainer().getComponentInstance(componentConfig.getInterfaceClass());
  }

  public ComponentConfig getConfig(Class componentImplementation) {
    return myComponentsRegistry.getConfig(componentImplementation);
  }

  @Override
  @NotNull
  public Condition getDisposed() {
    return myDisposedCondition;
  }

  @NotNull
  public static String getComponentName(@NotNull final Object component) {
    if (component instanceof NamedComponent) {
      return ((NamedComponent)component).getComponentName();
    }
    else {
      return component.getClass().getName();
    }
  }

  protected boolean logSlowComponents() {
    return LOG.isDebugEnabled();
  }

  protected class ComponentsRegistry {
    private final Map<Class, Object> myInterfaceToLockMap = new THashMap<Class, Object>();
    private final Map<Class, Class> myInterfaceToClassMap = new THashMap<Class, Class>();
    private final List<Class> myComponentInterfaces = new ArrayList<Class>(); // keeps order of component's registration
    private final Map<String, BaseComponent> myNameToComponent = new THashMap<String, BaseComponent>();
    private final List<ComponentConfig> myComponentConfigs = new ArrayList<ComponentConfig>();
    private final List<Object> myImplementations = new ArrayList<Object>();
    private final Map<Class, ComponentConfig> myComponentClassToConfig = new THashMap<Class, ComponentConfig>();
    private boolean myClassesLoaded = false;

    private void loadClasses() {
      assert !myClassesLoaded;

      for (ComponentConfig config : myComponentConfigs) {
        loadClasses(config);
      }

      myClassesLoaded = true;
    }

    private void loadClasses(final ComponentConfig config) {
      ClassLoader loader = config.getClassLoader();

      try {
        final Class<?> interfaceClass = Class.forName(config.getInterfaceClass(), true, loader);
        final Class<?> implementationClass = Comparing.equal(config.getInterfaceClass(), config.getImplementationClass()) ?
                                             interfaceClass : Class.forName(config.getImplementationClass(), true, loader);

        if (myInterfaceToClassMap.get(interfaceClass) != null) {
          throw new RuntimeException("Component already registered: " + interfaceClass.getName());
        }

        getPicoContainer().registerComponent(new ComponentConfigComponentAdapter(config, implementationClass));
        myInterfaceToClassMap.put(interfaceClass, implementationClass);
        myComponentClassToConfig.put(implementationClass, config);
        myComponentInterfaces.add(interfaceClass);
      }
      catch (Throwable t) {
        handleInitComponentError(t, null, config);
      }
    }

    private Object getComponentLock(final Class componentClass) {
      Object lock = myInterfaceToLockMap.get(componentClass);
      if (lock == null) {
        myInterfaceToLockMap.put(componentClass, lock = new Object());
      }
      return lock;
    }

    private Class[] getComponentInterfaces() {
      assert myClassesLoaded;
      return myComponentInterfaces.toArray(new Class[myComponentInterfaces.size()]);
    }

    private boolean containsInterface(final Class interfaceClass) {
      return myInterfaceToClassMap.containsKey(interfaceClass);
    }

    public float getPercentageOfComponentsLoaded() {
      return ((float)myImplementations.size()) / myComponentConfigs.size();
    }

    private void registerComponentInstance(final Object component) {
      myImplementations.add(component);

      if (component instanceof BaseComponent) {
        BaseComponent baseComponent = (BaseComponent)component;
        final String componentName = baseComponent.getComponentName();

        if (myNameToComponent.containsKey(componentName)) {
          BaseComponent loadedComponent = myNameToComponent.get(componentName);
          // component may have been already loaded by PicoContainer, so fire error only if components are really different
          if (!component.equals(loadedComponent)) {
            LOG.error("Component name collision: " + componentName + " " + loadedComponent.getClass() + " and " + component.getClass());
          }
        }
        else {
          myNameToComponent.put(componentName, baseComponent);
        }
      }
    }

    public List<Object> getRegisteredImplementations() {
      return myImplementations;
    }

    private void registerComponent(ComponentConfig config) {
      myComponentConfigs.add(config);

      if (myClassesLoaded) {
        loadClasses(config);
      }
    }

    private BaseComponent getComponentByName(final String name) {
      return myNameToComponent.get(name);
    }

    @SuppressWarnings({"unchecked"})
    public <T> T[] getComponentsByType(final Class<T> baseClass) {
      ArrayList<T> array = new ArrayList<T>();

      //noinspection ForLoopReplaceableByForEach
      for (int i = 0; i < myComponentInterfaces.size(); i++) {
        Class interfaceClass = myComponentInterfaces.get(i);
        final Class implClass = myInterfaceToClassMap.get(interfaceClass);
        if (ReflectionCache.isAssignable(baseClass, implClass)) {
          array.add((T)getComponent(interfaceClass));
        }
      }

      return array.toArray((T[])Array.newInstance(baseClass, array.size()));
    }

    public ComponentConfig[] getComponentConfigurations() {
        return myComponentConfigs.toArray(new ComponentConfig[myComponentConfigs.size()]);
    }

    public ComponentConfig getConfig(final Class componentImplementation) {
      return myComponentClassToConfig.get(componentImplementation);
    }
  }

  private class ComponentConfigComponentAdapter implements ComponentAdapter {
    private final ComponentConfig myConfig;
    private final ComponentAdapter myDelegate;
    private boolean myInitialized = false;
    private boolean myInitializing = false;

    public ComponentConfigComponentAdapter(final ComponentConfig config, Class<?> implementationClass) {
      myConfig = config;

      final String componentKey = config.getInterfaceClass();
      myDelegate = new CachingComponentAdapter(new ConstructorInjectionComponentAdapter(componentKey, implementationClass, null, true)) {
        @Override
        public Object getComponentInstance(PicoContainer picoContainer) throws PicoInitializationException, PicoIntrospectionException, ProcessCanceledException {
          ProgressIndicator indicator = getProgressIndicator();
          if (indicator != null) {
            indicator.checkCanceled();
          }

          Object componentInstance = null;
          try {
            long startTime = myInitialized ? 0 : System.nanoTime();

            componentInstance = super.getComponentInstance(picoContainer);

            if (!myInitialized) {
              if (myInitializing) {
                if (myConfig.pluginDescriptor != null) {
                  LOG.error(new PluginException("Cyclic component initialization: " + componentKey, myConfig.pluginDescriptor.getPluginId()));
                }
                else {
                  LOG.error(new Throwable("Cyclic component initialization: " + componentKey));
                }
              }

              try {
                myInitializing = true;
                myComponentsRegistry.registerComponentInstance(componentInstance);

                initializeComponent(componentInstance, false);
                if (componentInstance instanceof BaseComponent) {
                  ((BaseComponent)componentInstance).initComponent();
                }

                long ms = (System.nanoTime() - startTime) / 1000000;
                if (ms > 10 && logSlowComponents()) {
                  LOG.info(componentInstance.getClass().getName() + " initialized in " + ms + " ms");
                }
              }
              finally {
                myInitializing = false;
              }

              myInitialized = true;
            }
          }
          catch (ProcessCanceledException e) {
            throw e;
          }
          catch (StateStorageException e) {
            throw e;
          }
          catch (Throwable t) {
            handleInitComponentError(t, componentKey, config);
          }

          return componentInstance;
        }
      };
    }

    @Override
    public Object getComponentKey() {
      return myConfig.getInterfaceClass();
    }

    @Override
    public Class getComponentImplementation() {
      return myDelegate.getComponentImplementation();
    }

    @Override
    public Object getComponentInstance(final PicoContainer container) throws PicoInitializationException, PicoIntrospectionException {
      return myDelegate.getComponentInstance(container);
    }

    @Override
    public void verify(final PicoContainer container) throws PicoIntrospectionException {
      myDelegate.verify(container);
    }

    @Override
    public void accept(final PicoVisitor visitor) {
      visitor.visitComponentAdapter(this);
      myDelegate.accept(visitor);
    }
  }
}
