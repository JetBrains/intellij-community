// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.ParallelActivity;
import com.intellij.diagnostic.PluginException;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.diagnostic.StartUpMeasurer.Level;
import com.intellij.diagnostic.StartUpMeasurer.Phases;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.NamedComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusFactory;
import com.intellij.util.messages.Topic;
import com.intellij.util.messages.impl.MessageBusImpl;
import com.intellij.util.pico.CachingConstructorInjectionComponentAdapter;
import com.intellij.util.pico.DefaultPicoContainer;
import gnu.trove.THashMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.picocontainer.ComponentAdapter;
import org.picocontainer.Disposable;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.PicoContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class ComponentManagerImpl extends UserDataHolderBase implements ComponentManager, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.components.ComponentManager");

  private volatile MutablePicoContainer myPicoContainer;
  private volatile boolean myDisposed;
  private volatile boolean myDisposeCompleted;

  private MessageBus myMessageBus;

  private final Map<String, BaseComponent> myNameToComponent = new THashMap<>(); // contents guarded by this

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private int myComponentConfigCount;
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private int myInstantiatedComponentCount = 0;
  private boolean myComponentsCreated;

  private final List<BaseComponent> myBaseComponents = new SmartList<>();

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

  @Nullable
  protected String activityNamePrefix() {
    return null;
  }

  protected void registerComponents(@NotNull List<? extends IdeaPluginDescriptor> plugins) {
    Application app = ApplicationManager.getApplication();
    boolean headless = app == null || app.isHeadlessEnvironment();

    int componentConfigCount = 0;
    for (IdeaPluginDescriptor plugin : plugins) {
      for (ComponentConfig config : getMyComponentConfigsFromDescriptor(plugin)) {
        if (!config.prepareClasses(headless)) {
          continue;
        }

        if (isComponentSuitable(config)) {
          registerComponents(config, plugin);
          componentConfigCount++;
        }
      }

      registerServices(plugin);
    }
    myComponentConfigCount = componentConfigCount;
  }

  protected final void createComponents(@Nullable ProgressIndicator indicator) {
    LOG.assertTrue(!myComponentsCreated);

    if (indicator != null) {
      indicator.setIndeterminate(false);
    }

    String activityNamePrefix = activityNamePrefix();
    Activity activity = activityNamePrefix == null ? null : StartUpMeasurer.start(activityNamePrefix + Phases.CREATE_COMPONENTS_SUFFIX);

    DefaultPicoContainer picoContainer = (DefaultPicoContainer)getPicoContainer();
    for (ComponentAdapter componentAdapter : picoContainer.getComponentAdapters()) {
      if (componentAdapter instanceof ComponentConfigComponentAdapter) {
        ((ComponentConfigComponentAdapter)componentAdapter).getComponentInstance(picoContainer, indicator);
      }
    }

    if (activity != null) {
      activity.end();
    }

    myComponentsCreated = true;
  }

  protected void registerServices(@NotNull IdeaPluginDescriptor pluginDescriptor) {
  }

  protected void setProgressDuringInit(@NotNull ProgressIndicator indicator) {
    indicator.setFraction(getPercentageOfComponentsLoaded());
  }

  protected final double getPercentageOfComponentsLoaded() {
    return (double)myInstantiatedComponentCount / myComponentConfigCount;
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
    if (adapter == null) {
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

  protected void handleInitComponentError(@NotNull Throwable ex, String componentClassName, PluginId pluginId) {
    LOG.error(ex);
  }

  @TestOnly
  public void registerComponentImplementation(@NotNull Class<?> componentKey, @NotNull Class<?> componentImplementation) {
    registerComponentImplementation(componentKey, componentImplementation, false);
  }

  @TestOnly
  public void registerComponentImplementation(@NotNull Class<?> componentKey,
                                              @NotNull Class<?> componentImplementation,
                                              boolean shouldBeRegistered) {
    MutablePicoContainer picoContainer = getPicoContainer();
    ComponentConfigComponentAdapter adapter = (ComponentConfigComponentAdapter)picoContainer.unregisterComponent(componentKey);
    if (shouldBeRegistered) LOG.assertTrue(adapter != null);
    picoContainer.registerComponent(new ComponentConfigComponentAdapter(componentKey, componentImplementation, null, false));
  }

  @TestOnly
  public synchronized <T> T registerComponentInstance(@NotNull Class<T> componentKey, @NotNull T componentImplementation) {
    MutablePicoContainer picoContainer = getPicoContainer();
    ComponentAdapter adapter = picoContainer.getComponentAdapter(componentKey);
    LOG.assertTrue(adapter instanceof ComponentConfigComponentAdapter);
    ComponentConfigComponentAdapter componentAdapter = (ComponentConfigComponentAdapter)adapter;
    Object oldInstance = componentAdapter.myInitializedComponentInstance;
    // we don't update pluginId - method is test only
    componentAdapter.myInitializedComponentInstance = componentImplementation;
    //noinspection unchecked
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
    return getComponentInstancesOfType(baseClass, false);
  }

  @NotNull
  public final <T> List<T> getComponentInstancesOfType(@NotNull Class<T> baseClass, boolean createIfNotYet) {
    List<T> result = null;
    // we must use instances only from our adapter (could be service or extension point or something else)
    for (ComponentAdapter componentAdapter : ((DefaultPicoContainer)getPicoContainer()).getComponentAdapters()) {
      if (componentAdapter instanceof ComponentConfigComponentAdapter &&
          ReflectionUtil.isAssignable(baseClass, componentAdapter.getComponentImplementation())) {
        ComponentConfigComponentAdapter adapter = (ComponentConfigComponentAdapter)componentAdapter;
        //noinspection unchecked
        T instance = (T)(createIfNotYet ? adapter.getComponentInstance(myPicoContainer) : (T)adapter.myInitializedComponentInstance);
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
      throw new AssertionError("Already disposed: " + this);
    });
  }

  @NotNull
  protected MutablePicoContainer createPicoContainer() {
    return myParentComponentManager == null ? new DefaultPicoContainer() : new DefaultPicoContainer(myParentComponentManager.getPicoContainer());
  }

  protected boolean isComponentSuitable(@NotNull ComponentConfig componentConfig) {
    Map<String, String> options = componentConfig.options;
    return options == null || !Boolean.parseBoolean(options.get("internal")) || ApplicationManager.getApplication().isInternal();
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

  // used in upsource
  @NotNull
  public List<ComponentConfig> getMyComponentConfigsFromDescriptor(@NotNull IdeaPluginDescriptor plugin) {
    return plugin.getAppComponents();
  }

  protected void bootstrapPicoContainer(@NotNull String name) {
    MutablePicoContainer picoContainer = createPicoContainer();
    myPicoContainer = picoContainer;

    myMessageBus = MessageBusFactory.newMessageBus(name, myParentComponentManager == null ? null : myParentComponentManager.getMessageBus());
    if (myMessageBus instanceof MessageBusImpl) {
      ((MessageBusImpl) myMessageBus).setMessageDeliveryListener((topic, messageName, handler, duration) -> logMessageBusDelivery(topic, messageName, handler, duration));
    }
    picoContainer.registerComponentInstance(MessageBus.class, myMessageBus);
  }

  protected void logMessageBusDelivery(Topic topic, String messageName, Object handler, long durationNanos) {
    ClassLoader loader = handler.getClass().getClassLoader();
    String pluginId = loader instanceof PluginClassLoader ? ((PluginClassLoader) loader).getPluginIdString() : "com.intellij";
    StartUpMeasurer.addPluginCost(pluginId, "MessageBus", durationNanos);
  }

  protected final ComponentManager getParentComponentManager() {
    return myParentComponentManager;
  }

  @Nullable
  static PluginId getConfig(@NotNull ComponentAdapter adapter) {
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

  private void registerComponents(@NotNull ComponentConfig config, @NotNull PluginDescriptor pluginDescriptor) {
    ClassLoader loader = pluginDescriptor.getPluginClassLoader();
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
        picoContainer.registerComponent(new ComponentConfigComponentAdapter(interfaceClass, implementationClass, pluginDescriptor.getPluginId(), ws));
      }
    }
    catch (Throwable t) {
      handleInitComponentError(t, null, pluginDescriptor.getPluginId());
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
        String errorMessage = "Component name collision: " + componentName +
                              ' ' + (loadedComponent == null ? "null" : loadedComponent.getClass()) + " and " + instance.getClass();
        PluginException.logPluginError(LOG, errorMessage, null, instance.getClass());
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
    public Object getComponentInstance(@NotNull PicoContainer picoContainer) {
      return getComponentInstance(picoContainer, null);
    }

    Object getComponentInstance(@NotNull PicoContainer picoContainer, @Nullable ProgressIndicator indicator) {
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

          Activity activity = createMeasureActivity(picoContainer);
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

            if (indicator != null) {
              indicator.checkCanceled();
              setProgressDuringInit(indicator);
            }
            initializeComponent(instance, null);
            if (instance instanceof BaseComponent) {
              ((BaseComponent)instance).initComponent();
            }

            if (activity != null) {
              activity.end();
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

    @Nullable
    private Activity createMeasureActivity(@NotNull PicoContainer picoContainer) {
      Level level = DefaultPicoContainer.getActivityLevel(picoContainer);
      if (level == Level.APPLICATION || (level == Level.PROJECT && activityNamePrefix() != null)) {
        return ParallelActivity.COMPONENT.start(getComponentImplementation().getName(), level, myPluginId != null ? myPluginId.getIdString() : null);
      }
      return null;
    }

    @Override
    public String toString() {
      return "ComponentConfigAdapter[" + getComponentKey() + "]: implementation=" + getComponentImplementation() + ", plugin=" + myPluginId;
    }
  }
}