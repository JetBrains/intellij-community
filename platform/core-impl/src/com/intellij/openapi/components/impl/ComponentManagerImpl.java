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
package com.intellij.openapi.components.impl;

import com.intellij.diagnostic.PluginException;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.components.ex.ComponentManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusFactory;
import com.intellij.util.pico.ConstructorInjectionComponentAdapter;
import com.intellij.util.pico.DefaultPicoContainer;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.picocontainer.*;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author mike
 */
public abstract class ComponentManagerImpl extends UserDataHolderBase implements ComponentManagerEx, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.components.ComponentManager");

  private final Map<Class, Object> myInitializedComponents = ContainerUtil.newConcurrentMap();

  private boolean myComponentsCreated;

  private volatile MutablePicoContainer myPicoContainer;
  private volatile boolean myDisposed;
  private volatile boolean myDisposeCompleted;

  private MessageBus myMessageBus;

  private final ComponentManager myParentComponentManager;
  private ComponentsRegistry myComponentsRegistry;
  private final Condition myDisposedCondition = new Condition() {
    @Override
    public boolean value(final Object o) {
      return isDisposed();
    }
  };

  protected ComponentManagerImpl(@Nullable ComponentManager parentComponentManager) {
    myParentComponentManager = parentComponentManager;
    bootstrapPicoContainer(toString());
  }
  protected ComponentManagerImpl(@Nullable ComponentManager parentComponentManager, @NotNull String name) {
    myParentComponentManager = parentComponentManager;
    bootstrapPicoContainer(name);
  }

  protected final void init(@Nullable ProgressIndicator progressIndicator) {
    init(progressIndicator, null);
  }

  protected final void init(@Nullable ProgressIndicator indicator, @Nullable Runnable componentsRegistered) {
    try {
      ArrayList<ComponentConfig> componentConfigs = new ArrayList<ComponentConfig>();
      registerComponents(componentConfigs);
      myComponentsRegistry = new ComponentsRegistry(componentConfigs);

      if (componentsRegistered != null) {
        componentsRegistered.run();
      }

      if (indicator != null) {
        indicator.setIndeterminate(false);
      }
      createComponents(indicator);
    }
    finally {
      myComponentsCreated = true;
    }
  }

  protected void setProgressDuringInit(@NotNull ProgressIndicator indicator) {
    indicator.setFraction(getPercentageOfComponentsLoaded());
  }

  protected void createComponents(@Nullable ProgressIndicator indicator) {
    for (Class componentInterface : myComponentsRegistry.myComponentInterfaces) {
      getComponent(componentInterface);
      if (indicator != null) {
        indicator.checkCanceled();
      }
    }
  }

  @NotNull
  @Override
  public MessageBus getMessageBus() {
    if (myDisposeCompleted || myDisposed) {
      ProgressManager.checkCanceled();
      throw new AssertionError("Already disposed");
    }
    assert myMessageBus != null : "Not initialized yet";
    return myMessageBus;
  }

  public boolean isComponentsCreated() {
    return myComponentsCreated;
  }

  protected synchronized void disposeComponents() {
    assert !myDisposeCompleted : "Already disposed!";

    final List<Object> components = myComponentsRegistry == null ? Collections.emptyList() : myComponentsRegistry.getRegisteredImplementations();
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

  @SuppressWarnings("unchecked")
  @Nullable
  private <T> T getComponentFromContainer(@NotNull Class<T> componentInterface) {
    T component = (T)myInitializedComponents.get(componentInterface);
    if (component != null) {
      return component;
    }

    synchronized (this) {
      if (myComponentsRegistry == null || !myComponentsRegistry.containsInterface(componentInterface)) {
        return null;
      }

      synchronized (myComponentsRegistry.getComponentLock(componentInterface)) {
        component = (T)myInitializedComponents.get(componentInterface);
        if (component != null) {
          return component;
        }

        component = (T)getPicoContainer().getComponentInstance(componentInterface.getName());
        if (component == null) {
          LOG.error("Can't instantiate component for: " + componentInterface);
        }

        myInitializedComponents.put(componentInterface, component);

        if (component instanceof com.intellij.openapi.Disposable) {
          Disposer.register(this, (com.intellij.openapi.Disposable)component);
        }

        return component;
      }
    }
  }

  @Override
  public final <T> T getComponent(@NotNull Class<T> interfaceClass) {
    if (myDisposeCompleted) {
      ProgressManager.checkCanceled();
      throw new AssertionError("Already disposed: " + this);
    }
    return getComponent(interfaceClass, null);
  }

  @Override
  public final <T> T getComponent(@NotNull Class<T> interfaceClass, T defaultImplementation) {
    T component = getComponentFromContainer(interfaceClass);
    return component == null ? defaultImplementation : component;
  }

  @Nullable
  protected ProgressIndicator getProgressIndicator() {
    return ProgressManager.getInstance().getProgressIndicator();
  }

  protected final double getPercentageOfComponentsLoaded() {
    return myComponentsRegistry.getPercentageOfComponentsLoaded();
  }

  @Override
  public void initializeComponent(@NotNull Object component, boolean service) {
  }

  protected void handleInitComponentError(Throwable ex, String componentClassName, ComponentConfig config) {
    LOG.error(ex);
  }

  public synchronized void registerComponentImplementation(@NotNull Class componentKey, @NotNull Class componentImplementation) {
    getPicoContainer().registerComponentImplementation(componentKey.getName(), componentImplementation);
    myInitializedComponents.remove(componentKey);
  }

  @TestOnly
  public synchronized <T> T registerComponentInstance(@NotNull Class<T> componentKey, @NotNull T componentImplementation) {
    getPicoContainer().unregisterComponent(componentKey.getName());
    getPicoContainer().registerComponentInstance(componentKey.getName(), componentImplementation);
    @SuppressWarnings("unchecked") T t = (T)myInitializedComponents.remove(componentKey);
    return t;
  }

  @Override
  public synchronized boolean hasComponent(@NotNull Class interfaceClass) {
    return myComponentsRegistry != null && myComponentsRegistry.containsInterface(interfaceClass);
  }

  @Override
  @NotNull
  public synchronized <T> T[] getComponents(@NotNull Class<T> baseClass) {
    return myComponentsRegistry.getComponentsByType(baseClass);
  }

  @Override
  @NotNull
  public MutablePicoContainer getPicoContainer() {
    MutablePicoContainer container = myPicoContainer;
    if (container == null || myDisposeCompleted) {
      ProgressManager.checkCanceled();
      throw new AssertionError("Already disposed: "+toString());
    }
    return container;
  }

  @NotNull
  protected MutablePicoContainer createPicoContainer() {
    return myParentComponentManager == null ? new DefaultPicoContainer() : new DefaultPicoContainer(myParentComponentManager.getPicoContainer());
  }

  @Override
  public synchronized BaseComponent getComponent(@NotNull String name) {
    return myComponentsRegistry.getComponentByName(name);
  }

  protected boolean isComponentSuitable(@Nullable Map<String, String> options) {
    return options == null || (isComponentSuitableForOs(options.get("os")) && (!Boolean.parseBoolean(options.get("internal")) || ApplicationManager.getApplication().isInternal()));
  }

  public static boolean isComponentSuitableForOs(@Nullable String os) {
    if (StringUtil.isEmpty(os)) {
      return true;
    }

    if (os.equals("mac")) {
      return SystemInfoRt.isMac;
    }
    else if (os.equals("linux")) {
      return SystemInfoRt.isLinux;
    }
    else if (os.equals("windows")) {
      return SystemInfoRt.isWindows;
    }
    else if (os.equals("unix")) {
      return SystemInfoRt.isUnix;
    }
    else if (os.equals("freebsd")) {
      return SystemInfoRt.isFreeBSD;
    }
    else {
      LOG.warn("Unknown OS " + os);
      return true;
    }
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
    return myDisposed;
  }

  private void registerComponents(@NotNull ArrayList<ComponentConfig> componentConfigs) {
    boolean isDefaultProject = this instanceof Project && ((Project)this).isDefault();
    boolean headless = ApplicationManager.getApplication().isHeadlessEnvironment();
    for (IdeaPluginDescriptor plugin : PluginManagerCore.getPlugins()) {
      if (PluginManagerCore.shouldSkipPlugin(plugin)) {
        continue;
      }

      ComponentConfig[] configs = getMyComponentConfigsFromDescriptor(plugin);
      componentConfigs.ensureCapacity(componentConfigs.size() + configs.length);
      for (ComponentConfig config : configs) {
        if ((!isDefaultProject || config.isLoadForDefaultProject()) && isComponentSuitable(config.options) && config.prepareClasses(headless)) {
          config.pluginDescriptor = plugin;
          componentConfigs.add(config);
        }
      }
    }
  }

  @NotNull // used in upsource
  public ComponentConfig[] getMyComponentConfigsFromDescriptor(@NotNull IdeaPluginDescriptor plugin) {
    return plugin.getAppComponents();
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

  protected final int getComponentConfigurationsSize() {
    return myComponentsRegistry.myComponentConfigsSize;
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
    return component.getClass().getName();
  }

  protected boolean logSlowComponents() {
    return LOG.isDebugEnabled();
  }

  private class ComponentsRegistry {
    private final Map<Class, Object> myInterfaceToLockMap = new THashMap<Class, Object>();
    private final Map<Class, Class> myInterfaceToClassMap = new THashMap<Class, Class>();
    private final List<Class> myComponentInterfaces; // keeps order of component's registration
    private final Map<String, BaseComponent> myNameToComponent = new THashMap<String, BaseComponent>();
    private final int myComponentConfigsSize;
    private final List<Object> myImplementations = new ArrayList<Object>();
    private final Map<Class, ComponentConfig> myComponentClassToConfig = new THashMap<Class, ComponentConfig>();

    public ComponentsRegistry(@NotNull List<ComponentConfig> componentConfigs) {
      myComponentInterfaces = new ArrayList<Class>(componentConfigs.size());
      for (ComponentConfig config : componentConfigs) {
        loadClasses(config);
      }
      myComponentConfigsSize = componentConfigs.size();
    }

    private void loadClasses(@NotNull ComponentConfig config) {
      ClassLoader loader = config.getClassLoader();

      try {
        final Class<?> interfaceClass = Class.forName(config.getInterfaceClass(), true, loader);
        final Class<?> implementationClass = Comparing.equal(config.getInterfaceClass(), config.getImplementationClass()) ?
                                             interfaceClass : StringUtil.isEmpty(config.getImplementationClass()) ? null : Class.forName(config.getImplementationClass(), true, loader);
        boolean overrides = config.options != null && Boolean.parseBoolean(config.options.get("overrides"));
        MutablePicoContainer picoContainer = getPicoContainer();
        if (overrides) {
          ComponentAdapter oldAdapter = picoContainer.getComponentAdapterOfType(interfaceClass);
          if (oldAdapter == null) {
            throw new RuntimeException(config + " does not override anything");
          }
          picoContainer.unregisterComponent(oldAdapter.getComponentKey());
          myInterfaceToClassMap.remove(interfaceClass);
          myComponentClassToConfig.remove(oldAdapter.getComponentImplementation());
          myComponentInterfaces.remove(interfaceClass);
        }
        // implementationClass == null means we want to unregister this component
        if (implementationClass != null) {
          if (myInterfaceToClassMap.get(interfaceClass) != null) {
            throw new RuntimeException("Component already registered: " + interfaceClass.getName());
          }

          picoContainer.registerComponent(new ComponentConfigComponentAdapter(config, implementationClass));
          myInterfaceToClassMap.put(interfaceClass, implementationClass);
          myComponentClassToConfig.put(implementationClass, config);
          myComponentInterfaces.add(interfaceClass);
        }
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

    private boolean containsInterface(final Class interfaceClass) {
      return myInterfaceToClassMap.containsKey(interfaceClass);
    }

    private double getPercentageOfComponentsLoaded() {
      return ((double)myImplementations.size()) / myComponentConfigsSize;
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

    @NotNull
    private List<Object> getRegisteredImplementations() {
      return myImplementations;
    }

    private BaseComponent getComponentByName(final String name) {
      return myNameToComponent.get(name);
    }

    @SuppressWarnings("unchecked")
    private <T> T[] getComponentsByType(final Class<T> baseClass) {
      List<T> array = new ArrayList<T>();

      //noinspection ForLoopReplaceableByForEach
      for (int i = 0; i < myComponentInterfaces.size(); i++) {
        Class interfaceClass = myComponentInterfaces.get(i);
        final Class implClass = myInterfaceToClassMap.get(interfaceClass);
        if (ReflectionUtil.isAssignable(baseClass, implClass)) {
          array.add((T)getComponent(interfaceClass));
        }
      }

      return array.toArray((T[])Array.newInstance(baseClass, array.size()));
    }

    public ComponentConfig getConfig(final Class componentImplementation) {
      return myComponentClassToConfig.get(componentImplementation);
    }
  }

  private class ComponentConfigComponentAdapter implements ComponentAdapter {
    private final ComponentConfig myConfig;
    private final ComponentAdapter myDelegate;
    private boolean myInitialized;
    private boolean myInitializing;

    public ComponentConfigComponentAdapter(@NotNull final ComponentConfig config, @NotNull Class<?> implementationClass) {
      myConfig = config;

      final String componentKey = config.getInterfaceClass();
      myDelegate = new ConstructorInjectionComponentAdapter(componentKey, implementationClass, null, true) {
        @Override
        public Object getComponentInstance(PicoContainer picoContainer) throws PicoInitializationException, PicoIntrospectionException, ProcessCanceledException {
          Object componentInstance = null;
          try {
            long startTime = System.nanoTime();

            componentInstance = super.getComponentInstance(picoContainer);

            if (!myInitialized) {
              if (myInitializing) {
                String errorMessage = "Cyclic component initialization: " + componentKey;
                if (myConfig.pluginDescriptor != null) {
                  LOG.error(new PluginException(errorMessage, myConfig.pluginDescriptor.getPluginId()));
                }
                else {
                  LOG.error(new Throwable(errorMessage));
                }
              }

              try {
                myInitializing = true;
                myComponentsRegistry.registerComponentInstance(componentInstance);

                ProgressIndicator indicator = getProgressIndicator();
                if (indicator != null) {
                  indicator.checkCanceled();
                  setProgressDuringInit(indicator);
                }
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

    @Override
    public String toString() {
      return "ComponentConfigAdapter[" + getComponentKey() + "]: implementation=" + getComponentImplementation() + ", plugin=" + myConfig.getPluginId();
    }
  }
}
