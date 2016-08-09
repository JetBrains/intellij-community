/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.NamedComponent;
import com.intellij.openapi.components.ex.ComponentManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusFactory;
import com.intellij.util.pico.CachingConstructorInjectionComponentAdapter;
import com.intellij.util.pico.DefaultPicoContainer;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.picocontainer.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.extensions.Extensions.isComponentSuitableForOs;

public abstract class ComponentManagerImpl extends UserDataHolderBase implements ComponentManagerEx, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.components.ComponentManager");

  private volatile MutablePicoContainer myPicoContainer;
  private volatile boolean myDisposed;
  private volatile boolean myDisposeCompleted;

  private MessageBus myMessageBus;

  private final Map<String, BaseComponent> myNameToComponent = new THashMap<String, BaseComponent>(); // contents guarded by this

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private int myComponentConfigCount;
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private int myInstantiatedComponentCount = -1;
  private boolean myComponentsCreated;

  private final List<BaseComponent> myBaseComponents = new ArrayList<BaseComponent>();

  private final ComponentManager myParentComponentManager;
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
    List<ComponentConfig> componentConfigs = getComponentConfigs();
    for (ComponentConfig config : componentConfigs) {
      registerComponents(config);
    }
    myComponentConfigCount = componentConfigs.size();

    if (componentsRegistered != null) {
      componentsRegistered.run();
    }
    createComponents(indicator);
    myComponentsCreated = true;
  }

  protected void setProgressDuringInit(@NotNull ProgressIndicator indicator) {
    indicator.setFraction(getPercentageOfComponentsLoaded());
  }

  protected final double getPercentageOfComponentsLoaded() {
    return (double)myInstantiatedComponentCount / myComponentConfigCount;
  }

  protected void createComponents(@Nullable ProgressIndicator indicator) {
    DefaultPicoContainer picoContainer = (DefaultPicoContainer)getPicoContainer();
    for (ComponentAdapter componentAdapter : picoContainer.getComponentAdapters()) {
      if (componentAdapter instanceof ComponentConfigComponentAdapter) {
        componentAdapter.getComponentInstance(picoContainer);
        if (indicator != null) {
          indicator.checkCanceled();
        }
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

  public final boolean isComponentsCreated() {
    return myComponentsCreated;
  }

  protected final synchronized void disposeComponents() {
    assert !myDisposeCompleted : "Already disposed!";
    myDisposed = true;

    // we cannot use list of component adapters because we must dispose in reverse order of creation
    List<BaseComponent> components = myBaseComponents;
    for (int i = components.size() - 1; i >= 0; i--) {
      try {
        components.get(i).disposeComponent();
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
    myBaseComponents.clear();

    myComponentConfigCount = -1;
  }

  @SuppressWarnings("unchecked")
  @Override
  public final <T> T getComponent(@NotNull Class<T> interfaceClass) {
    if (myDisposeCompleted) {
      ProgressManager.checkCanceled();
      throw new AssertionError("Already disposed: " + this);
    }

    ComponentAdapter adapter = getPicoContainer().getComponentAdapter(interfaceClass);
    //noinspection unchecked
    if (!(adapter instanceof ComponentConfigComponentAdapter)) {
      return null;
    }

    if (myDisposed) {
      // getComponent could be called during some component.dispose() call, in this case we don't attempt to instantiate component
      return (T)((ComponentConfigComponentAdapter)adapter).myInitializedComponentInstance;
    }
    return (T)adapter.getComponentInstance(getPicoContainer());
  }

  @Override
  public final <T> T getComponent(@NotNull Class<T> interfaceClass, T defaultImplementation) {
    T component = getComponent(interfaceClass);
    return component == null ? defaultImplementation : component;
  }

  @Nullable
  protected ProgressIndicator getProgressIndicator() {
    return ProgressManager.getInstance().getProgressIndicator();
  }

  @Override
  public void initializeComponent(@NotNull Object component, boolean service) {
  }

  protected void handleInitComponentError(Throwable ex, String componentClassName, PluginId pluginId) {
    LOG.error(ex);
  }

  @TestOnly
  public void registerComponentImplementation(@NotNull Class<?> componentKey, @NotNull Class<?> componentImplementation) {
    MutablePicoContainer picoContainer = getPicoContainer();
    ComponentConfigComponentAdapter adapter = (ComponentConfigComponentAdapter)picoContainer.unregisterComponent(componentKey);
    LOG.assertTrue(adapter != null);
    picoContainer.registerComponent(new ComponentConfigComponentAdapter(componentKey, componentImplementation, null, false));
  }

  @SuppressWarnings("unchecked")
  @TestOnly
  public synchronized <T> T registerComponentInstance(@NotNull Class<T> componentKey, @NotNull T componentImplementation) {
    MutablePicoContainer picoContainer = getPicoContainer();
    ComponentAdapter adapter = picoContainer.getComponentAdapter(componentKey);
    LOG.assertTrue(adapter instanceof ComponentConfigComponentAdapter);
    ComponentConfigComponentAdapter componentAdapter = (ComponentConfigComponentAdapter)adapter;
    Object oldInstance = componentAdapter.myInitializedComponentInstance;
    // we don't update pluginId - method is test only
    componentAdapter.myInitializedComponentInstance = componentImplementation;
    return (T)oldInstance;
  }

  @Override
  public boolean hasComponent(@NotNull Class interfaceClass) {
    return getPicoContainer().getComponentAdapter(interfaceClass) != null;
  }

  @Override
  @NotNull
  public <T> T[] getComponents(@NotNull Class<T> baseClass) {
    return ArrayUtil.toObjectArray(getComponentInstancesOfType(baseClass), baseClass);
  }

  @NotNull
  @Override
  public final <T> List<T> getComponentInstancesOfType(@NotNull Class<T> baseClass) {
    List<T> result = null;
    // we must use instances only from our adapter (could be service or extension point or something else)
    for (ComponentAdapter componentAdapter : ((DefaultPicoContainer)getPicoContainer()).getComponentAdapters()) {
      if (componentAdapter instanceof ComponentConfigComponentAdapter && ReflectionUtil.isAssignable(baseClass, componentAdapter.getComponentImplementation())) {
        //noinspection unchecked
        T instance = (T)((ComponentConfigComponentAdapter)componentAdapter).myInitializedComponentInstance;
        if (instance != null) {
          if (result == null) {
            result = new ArrayList<T>();
          }
          result.add(instance);
        }
      }
    }
    return ContainerUtil.notNullize(result);
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

  protected boolean isComponentSuitable(@Nullable Map<String, String> options) {
    return options == null ||
           isComponentSuitableForOs(options.get("os")) &&
           (!Boolean.parseBoolean(options.get("internal")) || ApplicationManager.getApplication().isInternal());
  }

  @Override
  public void dispose() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myDisposeCompleted = true;

    if (myMessageBus != null) {
      myMessageBus.dispose();
      myMessageBus = null;
    }

    myPicoContainer = null;
    //noinspection SynchronizeOnThis
    synchronized (this) {
      myNameToComponent.clear();
    }
  }

  @Override
  public boolean isDisposed() {
    return myDisposed;
  }

  @NotNull
  private List<ComponentConfig> getComponentConfigs() {
    ArrayList<ComponentConfig> componentConfigs = new ArrayList<ComponentConfig>();
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
    return componentConfigs;
  }

  // used in upsource
  @NotNull
  public ComponentConfig[] getMyComponentConfigsFromDescriptor(@NotNull IdeaPluginDescriptor plugin) {
    return plugin.getAppComponents();
  }

  protected void bootstrapPicoContainer(@NotNull String name) {
    MutablePicoContainer picoContainer = createPicoContainer();
    myPicoContainer = picoContainer;

    myMessageBus = MessageBusFactory.newMessageBus(name, myParentComponentManager == null ? null : myParentComponentManager.getMessageBus());
    picoContainer.registerComponentInstance(MessageBus.class, myMessageBus);
  }

  protected final ComponentManager getParentComponentManager() {
    return myParentComponentManager;
  }

  protected final int getComponentConfigCount() {
    return myComponentConfigCount;
  }

  @Nullable
  public final PluginId getConfig(@NotNull ComponentAdapter adapter) {
    return adapter instanceof ComponentConfigComponentAdapter ? ((ComponentConfigComponentAdapter)adapter).myPluginId : null;
  }

  public final boolean isWorkspaceComponent(@NotNull Class<?> componentImplementation) {
    ComponentConfigComponentAdapter adapter = getComponentAdapter(componentImplementation);
    return adapter != null && adapter.isWorkspaceComponent;
  }

  @Nullable
  private ComponentConfigComponentAdapter getComponentAdapter(@NotNull Class<?> componentImplementation) {
    for (ComponentAdapter componentAdapter : ((DefaultPicoContainer)getPicoContainer()).getComponentAdapters()) {
      if (componentAdapter instanceof ComponentConfigComponentAdapter && componentAdapter.getComponentImplementation() == componentImplementation) {
        return (ComponentConfigComponentAdapter)componentAdapter;
      }
    }
    return null;
  }

  @Override
  @NotNull
  public final Condition getDisposed() {
    return myDisposedCondition;
  }

  @NotNull
  public static String getComponentName(@NotNull Object component) {
    if (component instanceof NamedComponent) {
      return ((NamedComponent)component).getComponentName();
    }
    return component.getClass().getName();
  }

  protected boolean logSlowComponents() {
    return LOG.isDebugEnabled();
  }

  private void registerComponents(@NotNull ComponentConfig config) {
    ClassLoader loader = config.getClassLoader();
    try {
      final Class<?> interfaceClass = Class.forName(config.getInterfaceClass(), true, loader);
      final Class<?> implementationClass = Comparing.equal(config.getInterfaceClass(), config.getImplementationClass())
                                           ?
                                           interfaceClass
                                           : StringUtil.isEmpty(config.getImplementationClass()) ? null : Class.forName(config.getImplementationClass(), true, loader);
      MutablePicoContainer picoContainer = getPicoContainer();
      if (config.options != null && Boolean.parseBoolean(config.options.get("overrides"))) {
        ComponentAdapter oldAdapter = picoContainer.getComponentAdapterOfType(interfaceClass);
        if (oldAdapter == null) {
          throw new RuntimeException(config + " does not override anything");
        }
        picoContainer.unregisterComponent(oldAdapter.getComponentKey());
      }
      // implementationClass == null means we want to unregister this component
      if (implementationClass != null) {
        picoContainer.registerComponent(new ComponentConfigComponentAdapter(interfaceClass, implementationClass, config.getPluginId(),
                                                                            config.options != null && Boolean.parseBoolean(config.options.get("workspace"))));
      }
    }
    catch (Throwable t) {
      handleInitComponentError(t, null, config.getPluginId());
    }
  }

  private void registerComponentInstance(@NotNull Object instance) {
    myInstantiatedComponentCount++;

    if (instance instanceof com.intellij.openapi.Disposable) {
      Disposer.register(this, (com.intellij.openapi.Disposable)instance);
    }

    if (!(instance instanceof BaseComponent)) {
      return;
    }

    BaseComponent baseComponent = (BaseComponent)instance;
    String componentName = baseComponent.getComponentName();
    if (myNameToComponent.containsKey(componentName)) {
      BaseComponent loadedComponent = myNameToComponent.get(componentName);
      // component may have been already loaded by PicoContainer, so fire error only if components are really different
      if (!instance.equals(loadedComponent)) {
        LOG.error("Component name collision: " + componentName + " " + loadedComponent.getClass() + " and " + instance.getClass());
      }
    }
    else {
      myNameToComponent.put(componentName, baseComponent);
    }

    myBaseComponents.add(baseComponent);
  }

  @Override
  public synchronized BaseComponent getComponent(@NotNull String name) {
    return myNameToComponent.get(name);
  }

  private final class ComponentConfigComponentAdapter extends CachingConstructorInjectionComponentAdapter {
    private final PluginId myPluginId;
    private volatile Object myInitializedComponentInstance;
    private boolean myInitializing;

    final boolean isWorkspaceComponent;

    public ComponentConfigComponentAdapter(@NotNull Class<?> interfaceClass, @NotNull Class<?> implementationClass, @Nullable PluginId pluginId, boolean isWorkspaceComponent) {
      super(interfaceClass, implementationClass, null, true);

      myPluginId = pluginId;
      this.isWorkspaceComponent = isWorkspaceComponent;
    }

    @Override
    public Object getComponentInstance(PicoContainer picoContainer) throws PicoInitializationException, PicoIntrospectionException, ProcessCanceledException {
      Object instance = myInitializedComponentInstance;
      if (instance != null) {
        return instance;
      }

      try {
        //noinspection SynchronizeOnThis
        synchronized (this) {
          instance = myInitializedComponentInstance;
          if (instance != null) {
            return instance;
          }

          long startTime = System.nanoTime();

          instance = super.getComponentInstance(picoContainer);

          if (myInitializing) {
            String errorMessage = "Cyclic component initialization: " + getComponentKey();
            if (myPluginId != null) {
              LOG.error(new PluginException(errorMessage, myPluginId));
            }
            else {
              LOG.error(new Throwable(errorMessage));
            }
          }

          try {
            myInitializing = true;
            registerComponentInstance(instance);

            ProgressIndicator indicator = getProgressIndicator();
            if (indicator != null) {
              indicator.checkCanceled();
              setProgressDuringInit(indicator);
            }
            initializeComponent(instance, false);
            if (instance instanceof BaseComponent) {
              ((BaseComponent)instance).initComponent();
            }

            long ms = (System.nanoTime() - startTime) / 1000000;
            if (ms > 10 && logSlowComponents()) {
              LOG.info(instance.getClass().getName() + " initialized in " + ms + " ms");
            }
          }
          finally {
            myInitializing = false;
          }
          myInitializedComponentInstance = instance;
        }
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable t) {
        handleInitComponentError(t, ((Class)getComponentKey()).getName(), myPluginId);
      }

      return instance;
    }

    @Override
    public String toString() {
      return "ComponentConfigAdapter[" + getComponentKey() + "]: implementation=" + getComponentImplementation() + ", plugin=" + myPluginId;
    }
  }
}
