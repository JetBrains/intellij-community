// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl;

import com.intellij.diagnostic.PluginException;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.NamedComponent;
import com.intellij.openapi.components.ex.ComponentManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.picocontainer.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class ComponentManagerImpl extends UserDataHolderBase implements ComponentManagerEx, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.components.ComponentManager");

  private volatile MutablePicoContainer myPicoContainer;
  private volatile boolean myDisposed;
  private volatile boolean myDisposeCompleted;

  private MessageBus myMessageBus;

  private final Map<String, BaseComponent> myNameToComponent = new THashMap<>(); // contents guarded by this

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private int myComponentConfigCount;
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private int myInstantiatedComponentCount = -1;
  private boolean myComponentsCreated;

  private final List<BaseComponent> myBaseComponents = new ArrayList<>();

  private final ComponentManager myParentComponentManager;
  private final Condition myDisposedCondition = o -> isDisposed();

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
    List<ComponentConfig> componentConfigs = getComponentConfigs(indicator);
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
    if (myDisposed) {
      throwAlreadyDisposed();
    }
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

  @Override
  public final <T> T getComponent(@NotNull Class<T> interfaceClass) {
    MutablePicoContainer picoContainer = getPicoContainer();
    ComponentAdapter adapter = picoContainer.getComponentAdapter(interfaceClass);
    if (!(adapter instanceof ComponentConfigComponentAdapter)) {
      return null;
    }

    //noinspection unchecked
    return (T)adapter.getComponentInstance(picoContainer);
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
    registerComponentImplementation(componentKey, componentImplementation, false);
  }

  @TestOnly
  public void registerComponentImplementation(@NotNull Class<?> componentKey, @NotNull Class<?> componentImplementation, boolean shouldBeRegistered) {
    MutablePicoContainer picoContainer = getPicoContainer();
    ComponentConfigComponentAdapter adapter = (ComponentConfigComponentAdapter)picoContainer.unregisterComponent(componentKey);
    if (shouldBeRegistered) LOG.assertTrue(adapter != null);
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
  public final <T> List<T> getComponentInstancesOfType(@NotNull Class<T> baseClass) {
    List<T> result = null;
    // we must use instances only from our adapter (could be service or extension point or something else)
    for (ComponentAdapter componentAdapter : ((DefaultPicoContainer)getPicoContainer()).getComponentAdapters()) {
      if (componentAdapter instanceof ComponentConfigComponentAdapter && ReflectionUtil.isAssignable(baseClass, componentAdapter.getComponentImplementation())) {
        //noinspection unchecked
        T instance = (T)((ComponentConfigComponentAdapter)componentAdapter).myInitializedComponentInstance;
        if (instance != null) {
          if (result == null) {
            result = new ArrayList<>();
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
      throwAlreadyDisposed();
    }
    return container;
  }

  @Contract("->fail")
  private void throwAlreadyDisposed() {
    ReadAction.run(() -> {
      ProgressManager.checkCanceled();
      throw new AssertionError("Already disposed: "+toString());
    });
  }

  @NotNull
  protected MutablePicoContainer createPicoContainer() {
    return myParentComponentManager == null ? new DefaultPicoContainer() : new DefaultPicoContainer(myParentComponentManager.getPicoContainer());
  }

  protected boolean isComponentSuitable(@Nullable Map<String, String> options) {
    return options == null ||
           Extensions.isComponentSuitableForOs(options.get("os")) &&
           (!Boolean.parseBoolean(options.get("internal")) || ApplicationManager.getApplication().isInternal());
  }

  @Override
  public void dispose() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myDisposeCompleted = true;

    if (myMessageBus != null) {
      Disposer.dispose(myMessageBus);
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
  private List<ComponentConfig> getComponentConfigs(final ProgressIndicator indicator) {
    ArrayList<ComponentConfig> componentConfigs = new ArrayList<>();
    boolean isDefaultProject = this instanceof Project && ((Project)this).isDefault();
    boolean headless = ApplicationManager.getApplication().isHeadlessEnvironment();
    for (IdeaPluginDescriptor plugin : PluginManagerCore.getPlugins((message, progress) -> indicator.setFraction(progress))) {
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
  public final Condition<?> getDisposed() {
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
      Class<?> interfaceClass = Class.forName(config.getInterfaceClass(), true, loader);
      Class<?> implementationClass = Comparing.equal(config.getInterfaceClass(), config.getImplementationClass()) ? interfaceClass :
                                     StringUtil.isEmpty(config.getImplementationClass()) ? null :
                                     Class.forName(config.getImplementationClass(), true, loader);
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
        boolean ws = config.options != null && Boolean.parseBoolean(config.options.get("workspace"));
        picoContainer.registerComponent(new ComponentConfigComponentAdapter(interfaceClass, implementationClass, config.getPluginId(), ws));
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
        LOG.error("Component name collision: " + componentName + " " + (loadedComponent == null ? "null" : loadedComponent.getClass()) + " and " + instance.getClass());
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

    ComponentConfigComponentAdapter(@NotNull Class<?> interfaceClass,
                                    @NotNull Class<?> implementationClass,
                                    @Nullable PluginId pluginId,
                                    boolean isWorkspaceComponent) {
      super(interfaceClass, implementationClass, null, true);

      myPluginId = pluginId;
      this.isWorkspaceComponent = isWorkspaceComponent;
    }

    @Override
    public Object getComponentInstance(PicoContainer picoContainer) throws PicoInitializationException, PicoIntrospectionException, ProcessCanceledException {
      Object instance = myInitializedComponentInstance;
      // getComponent could be called during some component.dispose() call, in this case we don't attempt to instantiate component
      if (instance != null || myDisposed) {
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
              indicator.setIndeterminate(false);
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