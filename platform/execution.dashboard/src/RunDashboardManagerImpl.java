// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard;

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
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.platform.execution.dashboard.splitApi.*;
import com.intellij.platform.execution.dashboard.splitApi.frontend.RunDashboardUiManagerImpl;
import com.intellij.ui.content.Content;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.flow.Flow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static com.intellij.execution.RunContentDescriptorIdImplKt.findContentValue;
import static com.intellij.execution.dashboard.RunDashboardCustomizer.CUSTOMIZER_EP_NAME;
import static com.intellij.execution.dashboard.RunDashboardServiceIdKt.findValue;
import static com.intellij.platform.kernel.ids.BackendGlobalIdsKt.storeValueGlobally;

// fixme might want to save the state on backend machine
@Service(Service.Level.PROJECT)
@State(name = "RunDashboard", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class RunDashboardManagerImpl implements RunDashboardManager, PersistentStateComponent<RunDashboardManagerImpl.State> {
  public static RunDashboardManagerImpl getInstance(Project project) {
    return project.getService(RunDashboardManagerImpl.class);
  }

  private static final ExtensionPointName<RunDashboardDefaultTypesProvider> DEFAULT_TYPES_PROVIDER_EP_NAME =
    new ExtensionPointName<>("com.intellij.runDashboardDefaultTypesProvider");

  private final Project myProject;
  private State myState = new State();
  private final Set<String> myTypes = new HashSet<>();
  private final Set<RunConfiguration> myHiddenConfigurations = new HashSet<>();
  private final Set<RunConfiguration> myShownConfigurations = new HashSet<>();
  private final Map<RunConfiguration, RunDashboardRunConfigurationStatus> myConfigurationStatuses = new ConcurrentHashMap<>();
  private volatile List<List<RunDashboardService>> myServices = new SmartList<>();
  private final BackendRunDashboardManagerState mySharedState;
  private final ReentrantReadWriteLock myServiceLock = new ReentrantReadWriteLock();
  private final AtomicBoolean myListenersInitialized = new AtomicBoolean();
  private final @NotNull CoroutineScope myScope;

  public RunDashboardManagerImpl(@NotNull Project project, @NotNull CoroutineScope coroutineScope) {
    myProject = project;
    myScope = coroutineScope;
    mySharedState = new BackendRunDashboardManagerState(myProject);
    initExtensionPointListeners();
  }

  @Override
  public boolean isInitialized() {
    return true;
  }

  @Override
  public void updateServiceRunContentDescriptor(@NotNull Content contentWithNewDescriptor, @NotNull RunContentDescriptor oldDescriptor) {
    RunContentDescriptorId oldDescriptorId = oldDescriptor.getId();
    if (oldDescriptorId == null) return;

    var newDescriptor = RunContentManagerImpl.getRunContentDescriptorByContent(contentWithNewDescriptor);

    var newContentId = newDescriptor == null ? null : newDescriptor.getId();
    if (newContentId instanceof  RunContentDescriptorIdImpl newContentIdImpl) {
      updateServiceRunContentDescriptor(oldDescriptorId, newContentIdImpl);
    }

    RunDashboardUiManagerImpl.getInstance(myProject).getDashboardContentManager().addContent(contentWithNewDescriptor);
  }

  @Override
  public void navigateToServiceOnRun(@NotNull RunContentDescriptorId descriptorId, Boolean focus){
    RunDashboardService service = findService(descriptorId);
    if (service == null) return;

    mySharedState.fireNavigateToServiceEvent(service.getUuid(), focus);
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
        updateDashboard(true);
      }
    };
    CUSTOMIZER_EP_NAME.addExtensionPointListener(dashboardUpdater, myProject);

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
    connection.subscribe(RunManagerListener.TOPIC, new RunManagerListener() {
      private final BackendRunDashboardUpdatesQueue synchronizationScheduler
        = new BackendRunDashboardUpdatesQueue(
          RunDashboardCoroutineScopeProvider.getInstance(myProject).createChildNamedScope("Backend run manager listener sync requests"),
          OverlappingTasksStrategy.SKIP_NEW);

      @Override
      public void runConfigurationAdded(@NotNull RunnerAndConfigurationSettings settings) {
        if (settings.isTemporary() && myState.excludedNewTypes.contains(settings.getType().getId())) {
          // Always include newly added temporary configurations.
          myShownConfigurations.add(settings.getConfiguration());
        }
        synchronizationScheduler.submit(() -> {
          syncConfigurations();
          updateDashboardIfNeeded(settings);
        });
      }

      @Override
      public void runConfigurationRemoved(@NotNull RunnerAndConfigurationSettings settings) {
        RunConfiguration configuration = settings.getConfiguration();
        myHiddenConfigurations.remove(configuration);
        myShownConfigurations.remove(configuration);
        myConfigurationStatuses.remove(configuration);
        synchronizationScheduler.submit(() -> {
          syncConfigurations();
          updateDashboardIfNeeded(settings);
        });
      }

      @Override
      public void runConfigurationChanged(@NotNull RunnerAndConfigurationSettings settings) {
        synchronizationScheduler.submit(() -> {
          updateDashboardIfNeeded(settings);
        });
      }

      @Override
      public void beginUpdate() { }

      @Override
      public void endUpdate() {
        synchronizationScheduler.submit(() -> {
          syncConfigurations();
          updateDashboard(true);
        });
      }
    });
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
    connection.subscribe(RunDashboardListener.DASHBOARD_TOPIC, new RunDashboardListener() {
      @Override
      public void configurationChanged(@NotNull RunConfiguration configuration, boolean withStructure) {
        updateDashboardIfNeeded(configuration, withStructure);
      }
    });

    connection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      public void exitDumbMode() {
        updateDashboard(false);
      }
    });
  }

  public @Nullable RunDashboardService findServiceById(RunDashboardServiceId id) {
    var valueFromGlobalStorage = findValue(id);
    if (valueFromGlobalStorage != null) {
      return valueFromGlobalStorage;
    }

    Logger.getInstance(RunDashboardManagerImpl.class)
      .warn("findServiceById failed to discover backend run dashboard service in global storage, falling back to manually managed collection");
    return ContainerUtil.find(getRunConfigurations(), service -> service.getUuid().equals(id));
  }

  public List<RunDashboardService> getRunConfigurations() {
    myServiceLock.readLock().lock();
    try {
      return myServices.stream().flatMap(Collection::stream).collect(Collectors.toList());
    }
    finally {
      myServiceLock.readLock().unlock();
    }
  }

  public Flow<RunDashboardSettingsDto> getSettingsDto() {
    return mySharedState.getSettings();
  }

  public Flow<List<RunDashboardServiceDto>> getServicesDto() {
    return mySharedState.getServices();
  }

  public Flow<ServiceStatusDto> getStatusesDto() {
    return mySharedState.getStatuses();
  }

  public Flow<ServiceCustomizationDto> getCustomizationsDto() {
    return mySharedState.getCustomizations();
  }

  public Flow<Set<String>> getExcludedTypesDto() {
    return mySharedState.getExcludedTypes();
  }

  public Flow<Set<String>> getConfigurationTypes() {
    return mySharedState.getConfigurationTypes();
  }

  public Flow<NavigateToServiceEvent> getNavigateToServiceEvents() {
    return mySharedState.getNavigateToServiceEvents();
  }

  public void runCallbackForLink(@NotNull String link, @NotNull RunDashboardServiceId serviceId) {
    Runnable callback = mySharedState.getLinkByServiceId(link, serviceId);
    if (callback != null) {
      callback.run();
    }
  }

  private @Unmodifiable List<RunContentDescriptor> filterByContent(List<? extends RunContentDescriptor> descriptors) {
    return ContainerUtil.filter(descriptors, descriptor -> {
      return ContainerUtil.find(getRunConfigurations(), service -> {
        var serviceDescriptor = service.getDescriptor();
        return serviceDescriptor != null && serviceDescriptor.getId().equals(descriptor.getId());
      }) != null;
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

  @Override
  public @NotNull Set<RunConfiguration> getHiddenConfigurations() {
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

  @Override
  public void hideConfigurations(@NotNull Collection<? extends RunConfiguration> configurations) {
    for (RunConfiguration configuration : configurations) {
      if (myState.excludedNewTypes.contains(configuration.getType().getId())) {
        myShownConfigurations.remove(configuration);
      }
      else {
        myHiddenConfigurations.add(configuration);
      }
    }
    syncConfigurations();
    // todo split: set signal to invoke reset RunDashboardUiManager.getInstance(project).getTypeContent().setType(myTypeContent.getType())
    updateDashboard(true);
  }

  @Override
  public void restoreConfigurations(@NotNull Collection<? extends RunConfiguration> configurations) {
    for (RunConfiguration configuration : configurations) {
      if (myState.excludedNewTypes.contains(configuration.getType().getId())) {
        myShownConfigurations.add(configuration);
      }
      else {
        myHiddenConfigurations.remove(configuration);
      }
    }
    syncConfigurations();
    // todo split: set signal to invoke reset RunDashboardUiManager.getInstance(project).getTypeContent().setType(myTypeContent.getType())
    updateDashboard(true);
  }

  @Override
  public boolean isNewExcluded(@NotNull String typeId) {
    return myState.excludedNewTypes.contains(typeId);
  }

  @Override
  public void setNewExcluded(@NotNull String typeId, boolean newExcluded) {
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

  @Override
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

  @Override
  public boolean isOpenRunningConfigInNewTab() {
    return myState.openRunningConfigInTab;
  }

  @Override
  public void setOpenRunningConfigInNewTab(boolean value) {
    myState.openRunningConfigInTab = value;
    mySharedState.setSettings(myState.openRunningConfigInTab);
  }

  public static @NotNull List<RunDashboardCustomizer> getCustomizers(@NotNull RunnerAndConfigurationSettings settings,
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
  public void updateDashboard(boolean withStructure) {
    for (RunDashboardService backendService : getRunConfigurations()) {
      mySharedState.fireStatusUpdated(backendService, getPersistedStatus(backendService.getConfigurationSettings().getConfiguration()));
      mySharedState.fireExcludedTypesUpdated(myState.excludedTypes);

      var applicableCustomizers = getCustomizers(backendService.getConfigurationSettings(), backendService.getDescriptor());
      if (applicableCustomizers.isEmpty()) continue;

      mySharedState.fireCustomizationUpdated(backendService, applicableCustomizers);
    }
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
          syncedServices = new SmartList<>(new RunDashboardServiceImpl(RunDashboardCoroutineScopeProvider.getInstance(myProject).getCs(),
                                                                       settings,
                                                                       null));
        }
        result.add(syncedServices);
      }
      for (List<RunDashboardService> oldServices : myServices) {
        RunDashboardService oldService = oldServices.getFirst();
        RunContentDescriptorId descriptorId = oldService.getDescriptorId();
        RunContentDescriptor descriptor = descriptorId == null ? null : getDescriptorById(descriptorId, myProject);
        if (descriptor != null && !settingsList.contains(oldService.getConfigurationSettings())) {
          if (!updateServiceSettings(myProject, result, oldServices)) {
            result.add(oldServices);
          }
        }
      }
      myServices = result;
    }
    finally {
      myServiceLock.writeLock().unlock();
      mySharedState.setServices(myServices);
      mySharedState.setConfigurationTypes(myState.configurationTypes);
    }
  }

  public @Nullable RunDashboardService updateServiceRunContentDescriptor(@NotNull RunContentDescriptorId oldDescriptorId,
                                                                         @NotNull RunContentDescriptorId descriptorId) {
    RunnerAndConfigurationSettings settings = findSettings(descriptorId);
    if (settings == null) return null;

    myServiceLock.writeLock().lock();
    try {
      RunDashboardService service = findService(oldDescriptorId);
      if (service != null) {
        doDetachServiceRunContentDescriptor(service);
      }

      return doAttachServiceRunContentDescriptor(settings, descriptorId);
    }
    finally {
      myServiceLock.writeLock().unlock();
      mySharedState.setServices(myServices);
      updateDashboard(true);
    }
  }

  public @Nullable RunDashboardService attachServiceRunContentDescriptor(@NotNull RunContentDescriptorId descriptorId) {
    RunnerAndConfigurationSettings settings = findSettings(descriptorId);
    if (settings == null) return null;

    myServiceLock.writeLock().lock();
    try {
      return doAttachServiceRunContentDescriptor(settings, descriptorId);
    }
    finally {
      myServiceLock.writeLock().unlock();
      mySharedState.setServices(myServices);
      updateDashboard(true);
    }
  }

  public void detachServiceRunContentDescriptor(@NotNull RunContentDescriptorId descriptorId) {
    myServiceLock.writeLock().lock();
    try {
      RunDashboardService service = findService(descriptorId);
      if (service == null) return;

      doDetachServiceRunContentDescriptor(service);
    }
    finally {
      myServiceLock.writeLock().unlock();
      mySharedState.setServices(myServices);
      updateDashboard(true);
    }
  }

  private @Nullable RunDashboardService doAttachServiceRunContentDescriptor(@NotNull RunnerAndConfigurationSettings settings,
                                                                            @NotNull RunContentDescriptorId descriptorId) {
    List<RunDashboardService> settingsServices = getServices(settings);
    if (settingsServices == null) {
      RunDashboardServiceImpl newService = new RunDashboardServiceImpl(RunDashboardCoroutineScopeProvider.getInstance(myProject).getCs(),
                                                                       settings,
                                                                       descriptorId);
      settingsServices = new SmartList<>(newService);
      myServices.add(settingsServices);
      return newService;
    }

    if (ContainerUtil.find(settingsServices, service -> descriptorId.equals(service.getDescriptorId())) != null) {
      return null;
    }

    RunDashboardService service = settingsServices.get(0);

    // purely to avoid thinking that frontend debugger might reuse content of the backend run
    var areDescriptorsWithSameExecutors = areSameOriginDescriptorsBeingExchanged(descriptorId, service);

    if ((!areDescriptorsWithSameExecutors || service.getDescriptorId() == null) && service instanceof RunDashboardServiceImpl mainService) {
      mainService.setDescriptorId(descriptorId);
      return mainService;
    }
    else {
      AdditionalRunDashboardService newService =
        new AdditionalRunDashboardService(settings, descriptorId, service.getUuid());
      settingsServices.add(newService);
      return newService;
    }
  }

  private static boolean areSameOriginDescriptorsBeingExchanged(@NotNull RunContentDescriptorId descriptorId, RunDashboardService service) {
    var existingId = service.getDescriptorId();
    var resolvedExistingDescriptor = existingId instanceof RunContentDescriptorIdImpl impl ?  findContentValue(impl) : null;
    var resolvedNewDescriptor = descriptorId instanceof RunContentDescriptorIdImpl impl ? findContentValue(impl) : null;
    var areDescriptorsWithSameExecutors =
      resolvedExistingDescriptor != null && resolvedNewDescriptor != null
      && resolvedExistingDescriptor.isHiddenContent() == resolvedNewDescriptor.isHiddenContent();
    return areDescriptorsWithSameExecutors;
  }

  private void doDetachServiceRunContentDescriptor(@NotNull RunDashboardService service) {
    RunnerAndConfigurationSettings contentSettings = service.getConfigurationSettings();
    List<RunDashboardService> services = getServices(contentSettings);
    if (services == null) return;

    if (!isShowInDashboard(contentSettings.getConfiguration()) ||
        !RunManager.getInstance(myProject).getAllSettings().contains(contentSettings)) {
      myServices.remove(services);
      return;
    }

    if (service instanceof RunDashboardServiceImpl mainService) {
      RunContentDescriptorId descriptorId = services.size() > 1 ? services.remove(1).getDescriptorId() : null;
      mainService.setDescriptorId(descriptorId);
    }
    else {
      services.remove(service);
    }
  }

  @Override
  @Nullable
  public RunDashboardService findService(@NotNull RunContentDescriptorId descriptorId) {
    myServiceLock.readLock().lock();
    try {
      for (List<RunDashboardService> services : myServices) {
        for (RunDashboardService service : services) {
          if (descriptorId.equals(service.getDescriptorId())) {
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

  public @Nullable RunnerAndConfigurationSettings findSettings(@NotNull RunContentDescriptorId descriptorId) {
    RunContentDescriptor descriptor = getDescriptorById(descriptorId, myProject);
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

  private static @Nullable RunContentDescriptor getDescriptorById(@NotNull RunContentDescriptorId descriptorId, @NotNull Project project) {
    RunContentDescriptor descriptor = null;
    if (descriptorId instanceof RunContentDescriptorIdImpl impl) {
      descriptor = findContentValue(impl);
    }
    return descriptor;
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
          if (runConfiguration.equals(service.getConfigurationSettings().getConfiguration())) {
            return service.getConfigurationSettings();
          }
        }
      }
    }
    finally {
      myServiceLock.readLock().unlock();
    }
    return new RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(myProject), runConfiguration);
  }

  public @Nullable List<RunDashboardService> getServices(@NotNull RunnerAndConfigurationSettings settings) {
    for (List<RunDashboardService> services : myServices) {
      if (services.get(0).getConfigurationSettings().equals(settings)) {
        return services;
      }
    }
    return null;
  }

  private static boolean updateServiceSettings(Project project, List<? extends List<RunDashboardService>> newServiceList,
                                               List<RunDashboardService> oldServices) {
    RunDashboardService oldService = oldServices.get(0);
    RunnerAndConfigurationSettings oldSettings = oldService.getConfigurationSettings();
    for (List<RunDashboardService> newServices : newServiceList) {
      RunnerAndConfigurationSettings newSettings = newServices.get(0).getConfigurationSettings();
      if (newSettings.getType().equals(oldSettings.getType()) && newSettings.getName().equals(oldSettings.getName())) {
        newServices.remove(0);
        newServices.add(0, new RunDashboardServiceImpl(oldService.getScope(), newSettings, oldService.getDescriptorId()));
        for (int i = 1; i < oldServices.size(); i++) {
          RunDashboardServiceImpl newService =
            new RunDashboardServiceImpl(oldService.getScope(), newSettings, oldServices.get(i).getDescriptorId());
          newServices.add(newService);
        }
        return true;
      }
    }
    return false;
  }

  @Override
  @NotNull
  public Set<String> getEnableByDefaultTypes() {
    Set<String> result = new HashSet<>();
    for (RunDashboardDefaultTypesProvider provider : DEFAULT_TYPES_PROVIDER_EP_NAME.getExtensionList()) {
      result.addAll(provider.getDefaultTypeIds(myProject));
    }
    return result;
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
      myTypes.addAll(getInstance(ProjectManager.getInstance().getDefaultProject()).getTypes());
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

  public static final class RunDashboardServiceImpl implements RunDashboardService {
    private final CoroutineScope myScope;
    private final RunnerAndConfigurationSettings mySettings;
    private RunContentDescriptorId myDescriptorId;
    private final RunDashboardServiceId myId;

    RunDashboardServiceImpl(@NotNull CoroutineScope scope,
                            @NotNull RunnerAndConfigurationSettings settings,
                            @Nullable RunContentDescriptorId descriptorId) {
      mySettings = settings;
      myDescriptorId = descriptorId;
      myScope = scope;
      myId = storeValueGlobally(scope, this, RunDashboardServiceIdType.INSTANCE);
    }

    @Override
    public @NotNull CoroutineScope getScope() {
      return myScope;
    }

    @Override
    public @NotNull RunDashboardServiceId getUuid() {
      return myId;
    }

    @Override
    public @NotNull RunnerAndConfigurationSettings getConfigurationSettings() {
      return mySettings;
    }

    @Override
    public @Nullable RunContentDescriptorId getDescriptorId() {
      return myDescriptorId;
    }

    @Override
    public Content getContent() {
      RunContentDescriptor descriptor = getDescriptor();
      return descriptor == null ? null : descriptor.getAttachedContent();
    }

    @Override
    public @Nullable RunContentDescriptor getDescriptor() {
      RunContentDescriptorId descriptorId = myDescriptorId;
      if (descriptorId == null) return null;

      return getDescriptorById(descriptorId, mySettings.getConfiguration().getProject());
    }

    void setDescriptorId(@Nullable RunContentDescriptorId descriptorId) {
      myDescriptorId = descriptorId;
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

  static final class AdditionalRunDashboardService implements RunDashboardService {
    private final RunnerAndConfigurationSettings mySettings;
    private final RunContentDescriptorId myDescriptorId;
    private final RunDashboardServiceId myId;

    AdditionalRunDashboardService(@NotNull RunnerAndConfigurationSettings settings,
                                  @NotNull RunContentDescriptorId descriptorId,
                                  @NotNull RunDashboardServiceId id) {
      mySettings = settings;
      myDescriptorId = descriptorId;
      myId = id;
    }

    @Override
    public @NotNull RunDashboardServiceId getUuid() {
      return myId;
    }

    @Override
    public CoroutineScope getScope() {
      return null;
    }

    @Override
    public @NotNull RunnerAndConfigurationSettings getConfigurationSettings() {
      return mySettings;
    }

    @Override
    public @NotNull RunContentDescriptorId getDescriptorId() {
      return myDescriptorId;
    }

    @Override
    public @Nullable RunContentDescriptor getDescriptor() {
      return getDescriptorById(myDescriptorId, mySettings.getConfiguration().getProject());
    }

    @Override
    public Content getContent() {
      return getDescriptor().getAttachedContent();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      AdditionalRunDashboardService service = (AdditionalRunDashboardService)o;
      return mySettings.equals(service.mySettings) && Comparing.equal(myDescriptorId, service.myDescriptorId);
    }

    @Override
    public int hashCode() {
      int result = mySettings.hashCode();
      result = 31 * result + myDescriptorId.hashCode();
      return result;
    }
  }
}
