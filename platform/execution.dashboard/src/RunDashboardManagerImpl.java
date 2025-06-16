// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard;

import com.google.common.collect.Sets;
import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.dashboard.*;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.services.ServiceEventListener;
import com.intellij.execution.services.ServiceViewDescriptor;
import com.intellij.execution.services.ServiceViewManager;
import com.intellij.execution.services.ServiceViewUIUtils;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.RunContentManagerImpl;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.impl.RunnerLayoutUiImpl;
import com.intellij.icons.AllIcons;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.AppUIExecutor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.platform.execution.dashboard.tree.RunConfigurationNode;
import com.intellij.platform.execution.dashboard.tree.RunDashboardStatusFilter;
import com.intellij.platform.execution.serviceView.ServiceViewManagerImpl;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.content.*;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.intellij.platform.execution.dashboard.RunDashboardServiceViewContributor.RUN_DASHBOARD_CONTENT_TOOLBAR;
// fixme might want to save the state on backend machine
@State(name = "RunDashboard", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class RunDashboardManagerImpl implements RunDashboardManager, PersistentStateComponent<RunDashboardManagerImpl.State> {
  private static final ExtensionPointName<RunDashboardCustomizer> CUSTOMIZER_EP_NAME =
    new ExtensionPointName<>("com.intellij.runDashboardCustomizer");
  private static final ExtensionPointName<RunDashboardDefaultTypesProvider> DEFAULT_TYPES_PROVIDER_EP_NAME =
    new ExtensionPointName<>("com.intellij.runDashboardDefaultTypesProvider");
  static final ExtensionPointName<RunDashboardGroupingRule> GROUPING_RULE_EP_NAME =
    new ExtensionPointName<>("com.intellij.runDashboardGroupingRule");

  private final Project myProject;
  private final ContentManager myContentManager;
  private final ContentManagerListener myServiceContentManagerListener;
  private State myState = new State();
  private final Set<String> myTypes = new HashSet<>();
  private final Set<RunConfiguration> myHiddenConfigurations = new HashSet<>();
  private final Set<RunConfiguration> myShownConfigurations = new HashSet<>();
  private final Map<RunConfiguration, RunDashboardRunConfigurationStatus> myConfigurationStatuses = new ConcurrentHashMap<>();
  private volatile List<List<RunDashboardService>> myServices = new SmartList<>();
  private final ReentrantReadWriteLock myServiceLock = new ReentrantReadWriteLock();
  private final RunDashboardStatusFilter myStatusFilter = new RunDashboardStatusFilter();
  private String myToolWindowId;
  private final Predicate<Content> myReuseCondition;
  private final AtomicBoolean myListenersInitialized = new AtomicBoolean();
  private RunDashboardComponentWrapper myContentWrapper;
  private JComponent myEmptyContent;
  private RunDashboardTypePanel myTypeContent;

  public RunDashboardManagerImpl(@NotNull Project project) {
    myProject = project;
    ContentFactory contentFactory = ContentFactory.getInstance();
    myContentManager = contentFactory.createContentManager(new PanelContentUI(), false, project);
    myServiceContentManagerListener = new ServiceContentManagerListener();
    myReuseCondition = this::canReuseContent;
    initExtensionPointListeners();

    myContentManager.addContentManagerListener(new ContentManagerListener() {
      @Override
      public void contentAdded(@NotNull ContentManagerEvent event) {
        initServiceContentListeners();
        myContentManager.removeContentManagerListener(this);
      }
    });
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void initExtensionPointListeners() {
    ExtensionPointListener dashboardUpdater = new ExtensionPointListener() {
      @Override
      public void extensionAdded(@NotNull Object extension, @NotNull PluginDescriptor pluginDescriptor) {
        updateDashboard(true);
      }

      @Override
      public void extensionRemoved(@NotNull Object extension, @NotNull PluginDescriptor pluginDescriptor) {
        myProject.getMessageBus().syncPublisher(ServiceEventListener.TOPIC).handle(
          ServiceEventListener.ServiceEvent.createUnloadSyncResetEvent(RunDashboardServiceViewContributor.class));
      }
    };
    CUSTOMIZER_EP_NAME.addExtensionPointListener(dashboardUpdater, myProject);
    GROUPING_RULE_EP_NAME.addExtensionPointListener(dashboardUpdater, myProject);

    DEFAULT_TYPES_PROVIDER_EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(RunDashboardDefaultTypesProvider extension, @NotNull PluginDescriptor pluginDescriptor) {
        Set<String> types = new HashSet<>(getTypes());
        types.addAll(extension.getDefaultTypeIds(myProject));
        setTypes(types);
      }

      @Override
      public void extensionRemoved(RunDashboardDefaultTypesProvider extension, @NotNull PluginDescriptor pluginDescriptor) {
        Set<String> types = new HashSet<>(getTypes());
        types.removeAll(extension.getDefaultTypeIds(myProject));
        setTypes(types);
        dashboardUpdater.extensionRemoved(extension, pluginDescriptor);
      }
    }, myProject);

    // todo backend and likely no frontend?? updates
    ConfigurationType.CONFIGURATION_TYPE_EP.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(ConfigurationType extension, @NotNull PluginDescriptor pluginDescriptor) {
        setTypes(new HashSet<>(getTypes()));
      }

      @Override
      public void extensionRemoved(ConfigurationType extension, @NotNull PluginDescriptor pluginDescriptor) {
        Set<String> types = new HashSet<>(getTypes());
        types.remove(extension.getId());
        setTypes(types);
        dashboardUpdater.extensionRemoved(extension, pluginDescriptor);
      }
    }, myProject);
  }

  private void initServiceContentListeners() {
    if (!myListenersInitialized.compareAndSet(false, true)) return;

    MessageBusConnection connection = myProject.getMessageBus().connect(myProject);
    // todo backend updates
    connection.subscribe(RunManagerListener.TOPIC, new RunManagerListener() {
      private volatile boolean myUpdateStarted;

      @Override
      public void runConfigurationAdded(@NotNull RunnerAndConfigurationSettings settings) {
        if (settings.isTemporary() && myState.excludedNewTypes.contains(settings.getType().getId())) {
          // Always include newly added temporary configurations.
          myShownConfigurations.add(settings.getConfiguration());
        }
        if (!myUpdateStarted) {
          syncConfigurations();
          updateDashboardIfNeeded(settings);
        }
      }

      @Override
      public void runConfigurationRemoved(@NotNull RunnerAndConfigurationSettings settings) {
        RunConfiguration configuration = settings.getConfiguration();
        myHiddenConfigurations.remove(configuration);
        myShownConfigurations.remove(configuration);
        myConfigurationStatuses.remove(configuration);
        if (!myUpdateStarted) {
          syncConfigurations();
          updateDashboardIfNeeded(settings);
        }
      }

      @Override
      public void runConfigurationChanged(@NotNull RunnerAndConfigurationSettings settings) {
        if (!myUpdateStarted) {
          updateDashboardIfNeeded(settings);
        }
      }

      @Override
      public void beginUpdate() {
        myUpdateStarted = true;
      }

      @Override
      public void endUpdate() {
        myUpdateStarted = false;
        syncConfigurations();
        updateDashboard(true);
      }
    });
    // todo backend updates
    connection.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
      @Override
      public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, final @NotNull ProcessHandler handler) {
        updateDashboardIfNeeded(env.getRunnerAndConfigurationSettings());
      }

      @Override
      public void processTerminated(@NotNull String executorId,
                                    @NotNull ExecutionEnvironment env,
                                    @NotNull ProcessHandler handler,
                                    int exitCode) {
        RunConfiguration configuration =
          env.getRunnerAndConfigurationSettings() != null ? env.getRunnerAndConfigurationSettings().getConfiguration() : null;
        if (configuration != null && isShowInDashboard(configuration)) {
          boolean stopped = exitCode == 0 || handler.getUserData(ProcessHandler.TERMINATION_REQUESTED) == Boolean.TRUE;
          RunDashboardRunConfigurationStatus status =
            stopped ? RunDashboardRunConfigurationStatus.STOPPED : RunDashboardRunConfigurationStatus.FAILED;
          myConfigurationStatuses.put(configuration, status);
        }

        updateDashboardIfNeeded(env.getRunnerAndConfigurationSettings());
      }
    });
    // todo backend and frontend updates
    connection.subscribe(DASHBOARD_TOPIC, new RunDashboardListener() {
      @Override
      public void configurationChanged(@NotNull RunConfiguration configuration, boolean withStructure) {
        updateDashboardIfNeeded(configuration, withStructure);
      }
    });

    //todo backend and frontend updates
    connection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      public void exitDumbMode() {
        updateDashboard(false);
      }
    });
    myContentManager.addContentManagerListener(myServiceContentManagerListener);
  }

  @Override
  public ContentManager getDashboardContentManager() {
    return myContentManager;
  }

  @Override
  public @NotNull String getToolWindowId() {
    if (myToolWindowId == null) {
      if (LightEdit.owns(myProject)) {
        myToolWindowId = ToolWindowId.SERVICES;
      }
      else {
        String toolWindowId = ServiceViewManager.getInstance(myProject).getToolWindowId(RunDashboardServiceViewContributor.class);
        myToolWindowId = toolWindowId != null ? toolWindowId : ToolWindowId.SERVICES;
      }
    }
    return myToolWindowId;
  }

  @Override
  public @NotNull Icon getToolWindowIcon() {
    return AllIcons.Toolwindows.ToolWindowServices;
  }

  @Override
  public List<RunDashboardService> getRunConfigurations() {
    myServiceLock.readLock().lock();
    try {
      return myServices.stream().flatMap(Collection::stream).collect(Collectors.toList());
    }
    finally {
      myServiceLock.readLock().unlock();
    }
  }

  private @Unmodifiable List<RunContentDescriptor> filterByContent(List<? extends RunContentDescriptor> descriptors) {
    return ContainerUtil.filter(descriptors, descriptor -> {
      Content content = descriptor.getAttachedContent();
      return content != null && content.getManager() == myContentManager;
    });
  }

  @Override
  public boolean isShowInDashboard(@NotNull RunConfiguration runConfiguration) {
    if (isShown(runConfiguration)) return true;

    RunConfiguration baseConfiguration = getBaseConfiguration(runConfiguration);
    if (baseConfiguration != null) {
      return isShown(baseConfiguration);
    }
    return false;
  }

  private boolean isShown(@NotNull RunConfiguration runConfiguration) {
    if (!myTypes.contains(runConfiguration.getType().getId())) return false;
    if (myState.excludedNewTypes.contains(runConfiguration.getType().getId())) {
      return myShownConfigurations.contains(runConfiguration);
    }
    else {
      return !myHiddenConfigurations.contains(runConfiguration);
    }
  }

  private static @Nullable RunConfiguration getBaseConfiguration(@NotNull RunConfiguration runConfiguration) {
    RunProfile runProfile = ExecutionManagerImpl.getDelegatedRunProfile(runConfiguration);
    return runProfile instanceof RunConfiguration ? (RunConfiguration)runProfile : null;
  }

  @Override
  public @NotNull @Unmodifiable Set<String> getTypes() {
    return Collections.unmodifiableSet(myTypes);
  }

  @Override
  public void setTypes(@NotNull Set<String> types) {
    Set<String> removed = new HashSet<>(Sets.difference(myTypes, types));
    Set<String> added = new HashSet<>(Sets.difference(types, myTypes));

    myTypes.clear();
    myTypes.addAll(types);
    if (!myTypes.isEmpty()) {
      initServiceContentListeners();
    }

    Set<String> enableByDefaultTypes = getEnableByDefaultTypes();
    myState.configurationTypes.clear();
    myState.configurationTypes.addAll(myTypes);
    myState.configurationTypes.removeAll(enableByDefaultTypes);
    myState.excludedTypes.clear();
    myState.excludedTypes.addAll(enableByDefaultTypes);
    myState.excludedTypes.removeAll(myTypes);

    myState.excludedNewTypes.retainAll(types);
    myHiddenConfigurations.removeIf(configuration -> !types.contains(configuration.getType().getId()));
    myShownConfigurations.removeIf(configuration -> !types.contains(configuration.getType().getId()));
    myConfigurationStatuses.entrySet()
      .removeIf(configuration -> !types.contains(configuration.getKey().getType().getId()));

    syncConfigurations();
    if (!removed.isEmpty()) {
      AppUIUtil.invokeOnEdt(() -> moveRemovedContent(getContainsTypeIdCondition(removed)), myProject.getDisposed());
    }
    if (!added.isEmpty()) {
      AppUIUtil.invokeOnEdt(() -> moveAddedContent(getContainsTypeIdCondition(added)), myProject.getDisposed());
    }
    updateDashboard(true);
  }

  private static Condition<? super RunnerAndConfigurationSettings> getContainsTypeIdCondition(Collection<String> types) {
    return settings -> {
      if (types.contains(settings.getType().getId())) return true;

      RunConfiguration baseConfiguration = getBaseConfiguration(settings.getConfiguration());
      if (baseConfiguration != null) {
        return types.contains(baseConfiguration.getType().getId());
      }
      return false;
    };
  }

  private void moveRemovedContent(Condition<? super RunnerAndConfigurationSettings> condition) {
    RunContentManagerImpl runContentManager = (RunContentManagerImpl)RunContentManager.getInstance(myProject);
    for (RunDashboardService service : getRunConfigurations()) {
      Content content = service.getContent();
      if (content == null || !condition.value(service.getSettings())) continue;

      RunContentDescriptor descriptor = RunContentManagerImpl.getRunContentDescriptorByContent(content);
      if (descriptor == null) continue;

      Executor executor = RunContentManagerImpl.getExecutorByContent(content);
      if (executor == null) continue;

      descriptor.setContentToolWindowId(null);
      updateContentToolbar(content, true);
      runContentManager.moveContent(executor, descriptor);
    }
  }

  private void moveAddedContent(Condition<? super RunnerAndConfigurationSettings> condition) {
    RunContentManagerImpl runContentManager = (RunContentManagerImpl)RunContentManager.getInstance(myProject);
    List<RunContentDescriptor> descriptors = ExecutionManager.getInstance(myProject).getRunningDescriptors(condition);
    for (RunContentDescriptor descriptor : descriptors) {
      Content content = descriptor.getAttachedContent();
      if (content == null) continue;

      Executor executor = RunContentManagerImpl.getExecutorByContent(content);
      if (executor == null) continue;

      descriptor.setContentToolWindowId(getToolWindowId());
      runContentManager.moveContent(executor, descriptor);
    }
  }

  public Set<RunConfiguration> getHiddenConfigurations() {
    Set<RunConfiguration> hiddenConfigurations = new HashSet<>(myHiddenConfigurations);
    for (String typeId : myState.excludedNewTypes) {
      hiddenConfigurations.addAll(getExcludedConfigurations(typeId, myShownConfigurations));
    }
    return hiddenConfigurations;
  }

  private Collection<RunConfiguration> getExcludedConfigurations(String typeId, Collection<RunConfiguration> toExclude) {
    ConfigurationType type = ConfigurationTypeUtil.findConfigurationType(typeId);
    if (type == null) return Collections.emptyList();

    List<RunConfiguration> configurations = RunManager.getInstance(myProject).getConfigurationsList(type);
    return ContainerUtil.filter(configurations, configuration -> !toExclude.contains(configuration));
  }

  public void hideConfigurations(Collection<? extends RunConfiguration> configurations) {
    for (RunConfiguration configuration : configurations) {
      if (myState.excludedNewTypes.contains(configuration.getType().getId())) {
        myShownConfigurations.remove(configuration);
      }
      else {
        myHiddenConfigurations.add(configuration);
      }
    }
    syncConfigurations();
    if (!configurations.isEmpty()) {
      moveRemovedContent(settings -> configurations.contains(settings.getConfiguration()) ||
                                     configurations.contains(getBaseConfiguration(settings.getConfiguration())));
    }
    if (myTypeContent != null) {
      myTypeContent.setType(myTypeContent.getType());
    }
    updateDashboard(true);
  }

  public void restoreConfigurations(Collection<? extends RunConfiguration> configurations) {
    for (RunConfiguration configuration : configurations) {
      if (myState.excludedNewTypes.contains(configuration.getType().getId())) {
        myShownConfigurations.add(configuration);
      }
      else {
        myHiddenConfigurations.remove(configuration);
      }
    }
    syncConfigurations();
    if (!configurations.isEmpty()) {
      moveAddedContent(settings -> configurations.contains(settings.getConfiguration()) ||
                                   configurations.contains(getBaseConfiguration(settings.getConfiguration())));
    }
    if (myTypeContent != null) {
      myTypeContent.setType(myTypeContent.getType());
    }
    updateDashboard(true);
  }

  public boolean isNewExcluded(String typeId) {
    return myState.excludedNewTypes.contains(typeId);
  }

  public void setNewExcluded(String typeId, boolean newExcluded) {
    if (newExcluded) {
      if (myState.excludedNewTypes.add(typeId)) {
        invert(typeId, myHiddenConfigurations, myShownConfigurations);
        updateDashboard(true);
      }
    }
    else {
      if (myState.excludedNewTypes.remove(typeId)) {
        invert(typeId, myShownConfigurations, myHiddenConfigurations);
        updateDashboard(true);
      }
    }
  }

  public void clearConfigurationStatus(@NotNull RunConfiguration configuration) {
    myConfigurationStatuses.remove(configuration);
    updateDashboardIfNeeded(configuration, false);
  }

  public @Nullable RunDashboardRunConfigurationStatus getPersistedStatus(@NotNull RunConfiguration configuration) {
    return myConfigurationStatuses.get(configuration);
  }

  private void invert(String typeId, Set<RunConfiguration> from, Set<RunConfiguration> to) {
    to.addAll(getExcludedConfigurations(typeId, from));
    from.removeIf(configuration -> configuration.getType().getId().equals(typeId));
  }

  public boolean isOpenRunningConfigInNewTab() {
    return myState.openRunningConfigInTab;
  }

  public void setOpenRunningConfigInNewTab(boolean value) {
    myState.openRunningConfigInTab = value;
  }

  static @NotNull List<RunDashboardCustomizer> getCustomizers(@NotNull RunnerAndConfigurationSettings settings,
                                                              @Nullable RunContentDescriptor descriptor) {
    List<RunDashboardCustomizer> customizers = new SmartList<>();
    for (RunDashboardCustomizer customizer : CUSTOMIZER_EP_NAME.getExtensions()) {
      if (customizer.isApplicable(settings, descriptor)) {
        customizers.add(customizer);
      }
    }
    return customizers;
  }

  private void updateDashboardIfNeeded(@Nullable RunnerAndConfigurationSettings settings) {
    if (settings != null) {
      updateDashboardIfNeeded(settings.getConfiguration(), true);
    }
  }

  private void updateDashboardIfNeeded(@NotNull RunConfiguration configuration, boolean withStructure) {
    if (isShowInDashboard(configuration) ||
        !filterByContent(getConfigurationDescriptors(configuration)).isEmpty()) {
      updateDashboard(withStructure);
    }
  }

  private List<RunContentDescriptor> getConfigurationDescriptors(@NotNull RunConfiguration configuration) {
    ExecutionManager instance = ExecutionManager.getInstance(myProject);
    if (!(instance instanceof ExecutionManagerImpl)) {
      return Collections.emptyList();
    }
    return ((ExecutionManagerImpl)instance).getDescriptors(s -> configuration.equals(s.getConfiguration()) ||
                                                                configuration.equals(getBaseConfiguration(s.getConfiguration())));
  }

  @Override
  public @NotNull Predicate<Content> getReuseCondition() {
    return myReuseCondition;
  }

  private boolean canReuseContent(Content content) {
    RunContentDescriptor descriptor = RunContentManagerImpl.getRunContentDescriptorByContent(content);
    if (descriptor == null) return false;

    ExecutionManagerImpl executionManager = ExecutionManagerImpl.getInstance(myProject);
    Set<RunnerAndConfigurationSettings> descriptorConfigurations = executionManager.getConfigurations(descriptor);
    if (descriptorConfigurations.isEmpty()) return true;

    Set<RunConfiguration> storedConfigurations = new HashSet<>(RunManager.getInstance(myProject).getAllConfigurationsList());

    return !ContainerUtil.exists(descriptorConfigurations, descriptorConfiguration -> {
      RunConfiguration configuration = descriptorConfiguration.getConfiguration();
      return isShowInDashboard(configuration) && storedConfigurations.contains(configuration);
    });
  }

  @Override
  public void updateDashboard(boolean withStructure) {
    myProject.getMessageBus().syncPublisher(ServiceEventListener.TOPIC).handle(
      ServiceEventListener.ServiceEvent.createResetEvent(RunDashboardServiceViewContributor.class));
  }

  private void syncConfigurations() {
    List<RunnerAndConfigurationSettings> settingsList =
      ContainerUtil.filter(RunManager.getInstance(myProject).getAllSettings(), settings -> {
        return isShowInDashboard(settings.getConfiguration());
      });
    List<List<RunDashboardService>> result = new ArrayList<>();
    myServiceLock.writeLock().lock();
    try {
      for (RunnerAndConfigurationSettings settings : settingsList) {
        List<RunDashboardService> syncedServices = getServices(settings);
        if (syncedServices == null) {
          syncedServices = new SmartList<>(new RunDashboardServiceImpl(settings, null));
        }
        result.add(syncedServices);
      }
      for (List<RunDashboardService> oldServices : myServices) {
        RunDashboardService oldService = oldServices.get(0);
        if (oldService.getContent() != null && !settingsList.contains(oldService.getSettings())) {
          if (!updateServiceSettings(result, oldServices)) {
            result.add(oldServices);
          }
        }
      }
      myServices = result;
    }
    finally {
      myServiceLock.writeLock().unlock();
    }
  }

  private @Nullable RunDashboardService addServiceContent(@NotNull Content content) {
    RunnerAndConfigurationSettings settings = findSettings(content);
    if (settings == null) return null;

    myServiceLock.writeLock().lock();
    try {
      return doAddServiceContent(settings, content);
    }
    finally {
      myServiceLock.writeLock().unlock();
    }
  }

  private void removeServiceContent(@NotNull Content content) {
    myServiceLock.writeLock().lock();
    try {
      RunDashboardService service = findService(content);
      if (service == null) return;

      doRemoveServiceContent(service);
    }
    finally {
      myServiceLock.writeLock().unlock();
      updateDashboard(true);
    }
  }

  private void updateServiceContent(@NotNull Content content) {
    RunnerAndConfigurationSettings settings = findSettings(content);
    if (settings == null) return;

    myServiceLock.writeLock().lock();
    try {
      RunDashboardService service = findService(content);
      if (service == null || service.getSettings().equals(settings)) return;

      doAddServiceContent(settings, content);
      doRemoveServiceContent(service);
    }
    finally {
      myServiceLock.writeLock().unlock();
    }
  }

  private @NotNull RunDashboardService doAddServiceContent(@NotNull RunnerAndConfigurationSettings settings, @NotNull Content content) {
    List<RunDashboardService> settingsServices = getServices(settings);
    if (settingsServices == null) {
      RunDashboardServiceImpl newService = new RunDashboardServiceImpl(settings, content);
      settingsServices = new SmartList<>(newService);
      myServices.add(settingsServices);
      return newService;
    }

    RunDashboardService service = settingsServices.get(0);
    if (service.getContent() == null && service instanceof RunDashboardServiceImpl mainService) {
      mainService.setContent(content);
      return mainService;
    }
    else {
      AdditionalRunDashboardService newService = new AdditionalRunDashboardService(settings, content);
      settingsServices.add(newService);
      return newService;
    }
  }

  private void doRemoveServiceContent(@NotNull RunDashboardService service) {
    RunnerAndConfigurationSettings contentSettings = service.getSettings();
    List<RunDashboardService> services = getServices(contentSettings);
    if (services == null) return;

    if (!isShowInDashboard(contentSettings.getConfiguration()) ||
        !RunManager.getInstance(myProject).getAllSettings().contains(contentSettings)) {
      myServices.remove(services);
      return;
    }

    if (service instanceof RunDashboardServiceImpl mainService) {
      Content content = services.size() > 1 ? services.remove(1).getContent() : null;
      mainService.setContent(content);
    }
    else {
      services.remove(service);
    }
  }

  private @Nullable RunDashboardService findService(@NotNull Content content) {
    myServiceLock.readLock().lock();
    try {
      for (List<RunDashboardService> services : myServices) {
        for (RunDashboardService service : services) {
          if (content.equals(service.getContent())) {
            return service;
          }
        }
      }
    }
    finally {
      myServiceLock.readLock().unlock();
      updateDashboard(true);
    }
    return null;
  }

  private @Nullable RunnerAndConfigurationSettings findSettings(@NotNull Content content) {
    RunContentDescriptor descriptor = RunContentManagerImpl.getRunContentDescriptorByContent(content);
    if (descriptor == null) return null;

    RunnerAndConfigurationSettings settings = findSettings(descriptor);
    if (settings == null) return null;

    RunConfiguration baseConfiguration = getBaseConfiguration(settings.getConfiguration());
    if (baseConfiguration != null) {
      RunnerAndConfigurationSettings baseSettings = RunManager.getInstance(myProject).findSettings(baseConfiguration);
      if (baseSettings != null) {
        return baseSettings;
      }
    }

    return settings;
  }

  private @Nullable RunnerAndConfigurationSettings findSettings(@NotNull RunContentDescriptor descriptor) {
    Set<ExecutionEnvironment> environments = ExecutionManagerImpl.getInstance(myProject).getExecutionEnvironments(descriptor);
    ExecutionEnvironment environment = ContainerUtil.getFirstItem(environments);
    if (environment == null) return null;

    RunnerAndConfigurationSettings settings = environment.getRunnerAndConfigurationSettings();
    if (settings != null) {
      return settings;
    }

    RunProfile runProfile = environment.getRunProfile();
    if (!(runProfile instanceof RunConfiguration runConfiguration)) return null;

    myServiceLock.readLock().lock();
    try {
      for (List<RunDashboardService> services : myServices) {
        for (RunDashboardService service : services) {
          if (runConfiguration.equals(service.getSettings().getConfiguration())) {
            return service.getSettings();
          }
        }
      }
    }
    finally {
      myServiceLock.readLock().unlock();
    }
    return new RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(myProject), runConfiguration);
  }

  private @Nullable List<RunDashboardService> getServices(@NotNull RunnerAndConfigurationSettings settings) {
    for (List<RunDashboardService> services : myServices) {
      if (services.get(0).getSettings().equals(settings)) {
        return services;
      }
    }
    return null;
  }

  private static boolean updateServiceSettings(List<? extends List<RunDashboardService>> newServiceList,
                                               List<RunDashboardService> oldServices) {
    RunDashboardService oldService = oldServices.get(0);
    RunnerAndConfigurationSettings oldSettings = oldService.getSettings();
    for (List<RunDashboardService> newServices : newServiceList) {
      RunnerAndConfigurationSettings newSettings = newServices.get(0).getSettings();
      if (newSettings.getType().equals(oldSettings.getType()) && newSettings.getName().equals(oldSettings.getName())) {
        newServices.remove(0);
        newServices.add(0, new RunDashboardServiceImpl(newSettings, oldService.getContent()));
        for (int i = 1; i < oldServices.size(); i++) {
          RunDashboardServiceImpl newService = new RunDashboardServiceImpl(newSettings, oldServices.get(i).getContent());
          newServices.add(newService);
        }
        return true;
      }
    }
    return false;
  }

  private static void updateContentToolbar(Content content, boolean visible) {
    RunContentDescriptor descriptor = RunContentManagerImpl.getRunContentDescriptorByContent(content);
    RunnerLayoutUiImpl ui = getRunnerLayoutUi(descriptor);
    if (ui != null) {
      if (!ServiceViewUIUtils.isNewServicesUIEnabled()) {
        ui.setLeftToolbarVisible(visible);
      }
      ui.setContentToolbarBefore(visible);
    }
    else {
      ActionToolbar toolbar = findActionToolbar(descriptor);
      if (toolbar != null) {
        toolbar.getComponent().setVisible(visible);
      }
    }
  }

  void setSelectedContent(@NotNull Content content) {
    ContentManager contentManager = content.getManager();
    if (contentManager == null || content == contentManager.getSelectedContent()) return;

    if (contentManager != myContentManager) {
      contentManager.setSelectedContent(content);
      return;
    }

    myContentManager.removeContentManagerListener(myServiceContentManagerListener);
    myContentManager.setSelectedContent(content);
    updateContentToolbar(content, false);
    myContentManager.addContentManagerListener(myServiceContentManagerListener);
  }

  void removeFromSelection(@NotNull Content content) {
    ContentManager contentManager = content.getManager();
    if (contentManager == null || content != contentManager.getSelectedContent()) return;

    if (contentManager != myContentManager) {
      contentManager.removeFromSelection(content);
      return;
    }

    myContentManager.removeContentManagerListener(myServiceContentManagerListener);
    myContentManager.removeFromSelection(content);
    myContentManager.addContentManagerListener(myServiceContentManagerListener);
  }

  public @NotNull RunDashboardStatusFilter getStatusFilter() {
    return myStatusFilter;
  }

  static @Nullable RunnerLayoutUiImpl getRunnerLayoutUi(@Nullable RunContentDescriptor descriptor) {
    if (descriptor == null) return null;

    RunnerLayoutUi layoutUi = descriptor.getRunnerLayoutUi();
    return layoutUi instanceof RunnerLayoutUiImpl ? (RunnerLayoutUiImpl)layoutUi : null;
  }

  static @Nullable ActionToolbar findActionToolbar(@Nullable RunContentDescriptor descriptor) {
    if (descriptor == null) return null;

    for (Component component : descriptor.getComponent().getComponents()) {
      if (component instanceof ActionToolbar) {
        return ((ActionToolbar)component);
      }
    }
    return null;
  }

  Set<String> getEnableByDefaultTypes() {
    Set<String> result = new HashSet<>();
    for (RunDashboardDefaultTypesProvider provider : DEFAULT_TYPES_PROVIDER_EP_NAME.getExtensionList()) {
      result.addAll(provider.getDefaultTypeIds(myProject));
    }
    return result;
  }

  JComponent getEmptyContent() {
    if (myEmptyContent == null) {
      JBPanelWithEmptyText textPanel = new JBPanelWithEmptyText()
        .withEmptyText(ExecutionBundle.message("run.dashboard.configurations.message"));
      textPanel.setFocusable(true);
      JComponent wrapped = UiDataProvider.wrapComponent(textPanel,
                                                        sink -> sink.set(PlatformDataKeys.TREE_EXPANDER_HIDE_ACTIONS_IF_NO_EXPANDER, true));
      JPanel mainPanel = new NonOpaquePanel(new BorderLayout());
      mainPanel.add(wrapped, BorderLayout.CENTER);
      setupToolbar(mainPanel, wrapped, myProject);
      myEmptyContent = mainPanel;
    }
    return myEmptyContent;
  }

  RunDashboardTypePanel getTypeContent() {
    if (myTypeContent == null) {
      myTypeContent = new RunDashboardTypePanel(myProject);
    }
    return myTypeContent;
  }

  static void setupToolbar(@NotNull JPanel mainPanel, @NotNull JComponent component, @NotNull Project project) {
    if (ServiceViewUIUtils.isNewServicesUIEnabled()) {
      if (ActionManager.getInstance().getAction(RUN_DASHBOARD_CONTENT_TOOLBAR) instanceof ActionGroup group) {
        group.registerCustomShortcutSet(component, project);
        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.SERVICES_TOOLBAR, group, true);
        toolbar.setTargetComponent(component);
        mainPanel.add(ServiceViewUIUtils.wrapServicesAligned(toolbar), BorderLayout.NORTH);
        int left = 0;
        int right = 0;
        Border border = toolbar.getComponent().getBorder();
        if (border != null) {
          Insets insets = border.getBorderInsets(toolbar.getComponent());
          left = insets.left;
          right = insets.right;
        }
        toolbar.getComponent().setBorder(JBUI.Borders.empty(1, left, 0, right));
        component.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP));
      }
    }
    ClientProperty.put(mainPanel, ServiceViewDescriptor.ACTION_HOLDER_KEY, Boolean.TRUE);
  }

  RunDashboardComponentWrapper getContentWrapper() {
    if (myContentWrapper == null) {
      myContentWrapper = new RunDashboardComponentWrapper(myProject);
      ClientProperty.put(myContentWrapper, ServiceViewDescriptor.ACTION_HOLDER_KEY, Boolean.TRUE);
    }
    return myContentWrapper;
  }

  @Override
  public @NotNull State getState() {
    myState.excludedNewTypes.retainAll(myTypes);

    myState.hiddenConfigurations.clear();
    for (RunConfiguration configuration : myHiddenConfigurations) {
      ConfigurationType type = configuration.getType();
      if (myTypes.contains(type.getId())) {
        Set<String> configurations = myState.hiddenConfigurations.get(type.getId());
        if (configurations == null) {
          configurations = new HashSet<>();
          myState.hiddenConfigurations.put(type.getId(), configurations);
        }
        configurations.add(configuration.getName());
      }
    }

    myState.shownConfigurations.clear();
    for (RunConfiguration configuration : myShownConfigurations) {
      ConfigurationType type = configuration.getType();
      if (myTypes.contains(type.getId())) {
        Set<String> configurations = myState.shownConfigurations.get(type.getId());
        if (configurations == null) {
          configurations = new HashSet<>();
          myState.shownConfigurations.put(type.getId(), configurations);
        }
        configurations.add(configuration.getName());
      }
    }

    myState.configurationStatuses.clear();
    for (Map.Entry<RunConfiguration, RunDashboardRunConfigurationStatus> entry : myConfigurationStatuses.entrySet()) {
      RunConfiguration configuration = entry.getKey();
      ConfigurationType type = configuration.getType();
      if (myTypes.contains(type.getId())) {
        Map<String, String> configurations = myState.configurationStatuses.get(type.getId());
        if (configurations == null) {
          configurations = new HashMap<>();
          myState.configurationStatuses.put(type.getId(), configurations);
        }
        configurations.put(configuration.getName(), entry.getValue().getId());
      }
    }

    // Maintain both sets so the project could be opened in an old version
    // with configurations hidden in a new one.
    for (String typeId : myState.excludedNewTypes) {
      Collection<RunConfiguration> hiddenConfigurations = getExcludedConfigurations(typeId, myShownConfigurations);
      myState.hiddenConfigurations.put(typeId, new HashSet<>(ContainerUtil.map(hiddenConfigurations, RunConfiguration::getName)));
    }
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
    myTypes.clear();
    myTypes.addAll(myState.configurationTypes);
    Set<String> enableByDefaultTypes = getEnableByDefaultTypes();
    enableByDefaultTypes.removeAll(myState.excludedTypes);
    myTypes.addAll(enableByDefaultTypes);

    myHiddenConfigurations.clear();
    myShownConfigurations.clear();
    myState.excludedNewTypes.retainAll(myTypes);
    if (!myTypes.isEmpty()) {
      loadStatuses();
      loadHiddenConfigurations();
      initTypes();
    }
  }

  private void loadHiddenConfigurations() {
    for (Map.Entry<String, Set<String>> entry : myState.hiddenConfigurations.entrySet()) {
      if (!myState.excludedNewTypes.contains(entry.getKey())) {
        loadConfigurations(entry.getKey(), entry.getValue(), myHiddenConfigurations);
      }
    }
    for (Map.Entry<String, Set<String>> entry : myState.shownConfigurations.entrySet()) {
      loadConfigurations(entry.getKey(), entry.getValue(), myShownConfigurations);
    }
  }

  private void loadConfigurations(String typeId, Set<String> configurationNames, Set<RunConfiguration> result) {
    ConfigurationType type = ConfigurationTypeUtil.findConfigurationType(typeId);
    if (type == null) return;

    List<RunConfiguration> configurations = RunManager.getInstance(myProject).getConfigurationsList(type);
    result.addAll(ContainerUtil.filter(configurations, configuration -> configurationNames.contains(configuration.getName())));
  }

  private void loadStatuses() {
    myState.configurationStatuses.forEach((typeId, statuses) -> {
      ConfigurationType type = ConfigurationTypeUtil.findConfigurationType(typeId);
      if (type == null) return;

      List<RunConfiguration> configurations = RunManager.getInstance(myProject).getConfigurationsList(type);
      statuses.forEach((name, statusId) -> {
        RunConfiguration configuration = ContainerUtil.find(configurations, it -> it.getName().equals(name));
        if (configuration == null) return;

        RunDashboardRunConfigurationStatus status = RunDashboardRunConfigurationStatus.getStatusById(statusId);
        if (status == null) return;

        myConfigurationStatuses.put(configuration, status);
      });
    });
  }

  private void initTypes() {
    syncConfigurations();
    initServiceContentListeners();
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      if (!myProject.isDisposed()) {
        updateDashboard(true);
      }
    });
  }

  @Override
  public void noStateLoaded() {
    myTypes.clear();
    if (myProject.isDefault()) {
      myTypes.addAll(getEnableByDefaultTypes());
    }
    else {
      myTypes.addAll(RunDashboardManager.getInstance(ProjectManager.getInstance().getDefaultProject()).getTypes());
    }
    if (!myTypes.isEmpty()) {
      initTypes();
    }
  }

  static final class State {
    public final Set<String> configurationTypes = new HashSet<>();
    public final Set<String> excludedTypes = new HashSet<>();
    public final Map<String, Set<String>> hiddenConfigurations = new HashMap<>();
    public final Map<String, Set<String>> shownConfigurations = new HashMap<>();
    public final Map<String, Map<String, String>> configurationStatuses = new HashMap<>();
    // Run configuration types for which new run configurations will be hidden
    // in the Services tool window by default.
    public final Set<String> excludedNewTypes = new HashSet<>();
    public boolean openRunningConfigInTab = false;
  }

  private static final class RunDashboardServiceImpl implements RunDashboardService {
    private final RunnerAndConfigurationSettings mySettings;
    private Content myContent;

    RunDashboardServiceImpl(@NotNull RunnerAndConfigurationSettings settings,
                            @Nullable Content content) {
      mySettings = settings;
      myContent = content;
    }

    @Override
    public @NotNull RunnerAndConfigurationSettings getSettings() {
      return mySettings;
    }

    @Override
    public @Nullable RunContentDescriptor getDescriptor() {
      Content content = myContent;
      return content == null ? null : RunContentManagerImpl.getRunContentDescriptorByContent(content);
    }

    @Override
    public @Nullable Content getContent() {
      return myContent;
    }

    void setContent(@Nullable Content content) {
      myContent = content;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      RunDashboardServiceImpl service = (RunDashboardServiceImpl)o;
      return mySettings.equals(service.mySettings);
    }

    @Override
    public int hashCode() {
      return mySettings.hashCode();
    }
  }

  private static final class AdditionalRunDashboardService implements RunDashboardService {
    private final RunnerAndConfigurationSettings mySettings;
    private final Content myContent;

    AdditionalRunDashboardService(@NotNull RunnerAndConfigurationSettings settings,
                                  @NotNull Content content) {
      mySettings = settings;
      myContent = content;
    }

    @Override
    public @NotNull RunnerAndConfigurationSettings getSettings() {
      return mySettings;
    }

    @Override
    public @Nullable RunContentDescriptor getDescriptor() {
      return RunContentManagerImpl.getRunContentDescriptorByContent(myContent);
    }

    @Override
    public @NotNull Content getContent() {
      return myContent;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      AdditionalRunDashboardService service = (AdditionalRunDashboardService)o;
      return mySettings.equals(service.mySettings) && Comparing.equal(myContent, service.myContent);
    }

    @Override
    public int hashCode() {
      int result = mySettings.hashCode();
      result = 31 * result + myContent.hashCode();
      return result;
    }
  }

  private final class ServiceContentManagerListener implements ContentManagerListener {
    private volatile Content myPreviousSelection = null;

    @Override
    public void selectionChanged(@NotNull ContentManagerEvent event) {
      boolean onAdd = event.getOperation() == ContentManagerEvent.ContentOperation.add;
      Content content = event.getContent();
      if (onAdd) {
        updateContentToolbar(content, false);
        updateServiceContent(content);
      }

      updateDashboard(true);

      if (onAdd) {
        RunDashboardService service = findService(content);
        if (service != null) {
          RunConfigurationNode node = createNode(service);
          RunnerAndConfigurationSettings settings = node.getConfigurationSettings();
          ((ServiceViewManagerImpl)ServiceViewManager.getInstance(myProject))
            .trackingSelect(node, RunDashboardServiceViewContributor.class,
                            settings.isActivateToolWindowBeforeRun(), settings.isFocusToolWindowBeforeRun())
            .onSuccess(selected -> {
              if (selected != Boolean.TRUE) {
                selectPreviousContent();
              }
            })
            .onError(t -> {
              selectPreviousContent();
            });
        }
      }
      else {
        myPreviousSelection = content;
      }
    }

    private void selectPreviousContent() {
      Content previousSelection = myPreviousSelection;
      if (previousSelection != null) {
        AppUIExecutor.onUiThread().expireWith(previousSelection).submit(() -> {
          setSelectedContent(previousSelection);
        });
      }
    }

    @Override
    public void contentAdded(@NotNull ContentManagerEvent event) {
      Content content = event.getContent();
      RunDashboardService service = addServiceContent(content);
      if (myState.openRunningConfigInTab) {
        if (service != null) {
          ServiceViewManager.getInstance(myProject).extract(createNode(service), RunDashboardServiceViewContributor.class);
        }
      }
    }

    @Override
    public void contentRemoved(@NotNull ContentManagerEvent event) {
      Content content = event.getContent();
      if (myPreviousSelection == content) {
        myPreviousSelection = null;
      }
      removeServiceContent(content);
    }

    private RunConfigurationNode createNode(RunDashboardService service) {
      return new RunConfigurationNode(myProject, service, getCustomizers(service.getSettings(), service.getDescriptor()));
    }
  }
}
