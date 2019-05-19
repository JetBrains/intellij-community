// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.dashboard;

import com.intellij.execution.*;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.dashboard.tree.RunConfigurationNode;
import com.intellij.execution.dashboard.tree.RunDashboardGrouper;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.services.ServiceEventListener;
import com.intellij.execution.services.ServiceViewManager;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManagerImpl;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.impl.RunnerLayoutUiImpl;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.content.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * @author konstantin.aleev
 */
@State(
  name = "RunDashboard",
  storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
public class RunDashboardManagerImpl implements RunDashboardManager, PersistentStateComponent<RunDashboardManagerImpl.State> {
  private static final ExtensionPointName<RunDashboardCustomizer> EP_NAME =
    ExtensionPointName.create("com.intellij.runDashboardCustomizer");
  private static final float DEFAULT_CONTENT_PROPORTION = 0.3f;
  @NonNls private static final String HELP_ID = "run-dashboard.reference";

  private final Project myProject;
  private final ContentManager myContentManager;
  private final ContentManagerListener myContentManagerListener;

  private State myState = new State();

  private volatile List<List<RunDashboardServiceImpl>> myServices = Collections.emptyList();
  private final ReentrantReadWriteLock myServiceLock = new ReentrantReadWriteLock();
  private final List<RunDashboardGrouper> myGroupers;
  private final Condition<Content> myReuseCondition;
  private final AtomicBoolean myListenersInitialized = new AtomicBoolean();
  private boolean myShowConfigurations = true;

  private RunDashboardContent myDashboardContent;
  private Content myToolWindowContent;
  private ContentManager myToolWindowContentManager;
  private ContentManagerListener myToolWindowContentManagerListener;
  private final Map<Content, Content> myDashboardToToolWindowContents = new HashMap<>();

  public RunDashboardManagerImpl(@NotNull final Project project) {
    myProject = project;

    ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
    myContentManager = contentFactory.createContentManager(new PanelContentUI(), false, project);
    myContentManagerListener = new DashboardContentManagerListener();
    myContentManager.addContentManagerListener(myContentManagerListener);
    myReuseCondition = this::canReuseContent;

    myGroupers = ContainerUtil.map(RunDashboardGroupingRule.EP_NAME.getExtensions(), RunDashboardGrouper::new);
  }

  private void initToolWindowContentListeners() {
    if (!myListenersInitialized.compareAndSet(false, true)) return;

    MessageBusConnection connection = myProject.getMessageBus().connect(myProject);
    connection.subscribe(RunManagerListener.TOPIC, new RunManagerListener() {
      private volatile boolean myUpdateStarted;

      @Override
      public void runConfigurationAdded(@NotNull RunnerAndConfigurationSettings settings) {
        if (!myUpdateStarted) {
          syncConfigurations();
          updateDashboardIfNeeded(settings);
        }
      }

      @Override
      public void runConfigurationRemoved(@NotNull RunnerAndConfigurationSettings settings) {
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
    connection.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
      @Override
      public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, final @NotNull ProcessHandler handler) {
        updateToolWindowContent();
        updateDashboardIfNeeded(env.getRunnerAndConfigurationSettings());
      }

      @Override
      public void processTerminated(@NotNull String executorId,
                                    @NotNull ExecutionEnvironment env,
                                    @NotNull ProcessHandler handler,
                                    int exitCode) {
        updateToolWindowContent();
        updateDashboardIfNeeded(env.getRunnerAndConfigurationSettings());
      }
    });
    connection.subscribe(RunDashboardManager.DASHBOARD_TOPIC, new RunDashboardListener() {
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
    myContentManager.addContentManagerListener(new ContentManagerAdapter() {
      @Override
      public void selectionChanged(@NotNull ContentManagerEvent event) {
        boolean onAdd = event.getOperation() == ContentManagerEvent.ContentOperation.add;
        Content content = event.getContent();
        if (onAdd) {
          RunnerLayoutUiImpl ui = getRunnerLayoutUi(RunContentManagerImpl.getRunContentDescriptorByContent(content));
          if (ui != null) {
            ui.setLeftToolbarVisible(false);
            ui.setContentToolbarBefore(false);
          }
        }

        updateToolWindowContent();
        updateDashboard(true);

        if (Registry.is("ide.service.view") && onAdd) {
          RunContentDescriptor descriptor = RunContentManagerImpl.getRunContentDescriptorByContent(content);
          Set<RunnerAndConfigurationSettings> settingsSet = ExecutionManagerImpl.getInstance(myProject).getConfigurations(descriptor);
          RunnerAndConfigurationSettings settings = ContainerUtil.getFirstItem(settingsSet);
          if (settings != null) {
            RunDashboardServiceImpl service = new RunDashboardServiceImpl(settings);
            service.setContent(content);
            RunConfigurationNode node = new RunConfigurationNode(myProject, service,
                                                                 getCustomizers(settings, descriptor));
            ServiceViewManager.getInstance(myProject).select(node, RunConfigurationsServiceViewContributor.class, true, false);
          }
        }
      }

      @Override
      public void contentAdded(@NotNull ContentManagerEvent event) {
        addServiceContent(event.getContent());
      }

      @Override
      public void contentRemoved(@NotNull ContentManagerEvent event) {
        if (myContentManager.getContentCount() == 0 && !isShowConfigurations()) {
          setShowConfigurations(true);
        }
      }
    });
  }

  @Override
  public ContentManager getDashboardContentManager() {
    return myContentManager;
  }

  @Override
  public String getToolWindowId() {
    return Registry.is("ide.service.view") ? ToolWindowId.SERVICES : ToolWindowId.RUN_DASHBOARD;
  }

  @Override
  public Icon getToolWindowIcon() {
    return Registry.is("ide.service.view")
           ? AllIcons.Toolwindows.ToolWindowServices
           : AllIcons.Toolwindows.ToolWindowRun; // TODO [konstantin.aleev] provide new icon
  }

  @Override
  public String getToolWindowContextHelpId() {
    return HELP_ID;
  }

  @Override
  public boolean isToolWindowAvailable() {
    return hasContent();
  }

  @Override
  public void createToolWindowContent(@NotNull ToolWindow toolWindow) {
    myDashboardContent = new RunDashboardContent(myProject, myContentManager, myGroupers);
    myToolWindowContent = ContentFactory.SERVICE.getInstance().createContent(myDashboardContent, null, false);
    myToolWindowContent.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);
    myToolWindowContent.setHelpId(getToolWindowContextHelpId());
    myToolWindowContent.setCloseable(false);
    Disposer.register(myToolWindowContent, myDashboardContent);
    Disposer.register(myToolWindowContent, () -> {
      myDashboardContent = null;
      myToolWindowContent = null;
      myToolWindowContentManager.removeContentManagerListener(myToolWindowContentManagerListener);
      myToolWindowContentManager = null;
      myToolWindowContentManagerListener = null;
      myDashboardToToolWindowContents.clear();
    });

    myToolWindowContentManager = toolWindow.getContentManager();
    myToolWindowContentManager.addContent(myToolWindowContent);

    myToolWindowContentManagerListener = new ToolWindowContentManagerListener();
    myToolWindowContentManager.addContentManagerListener(myToolWindowContentManagerListener);
  }

  @Override
  public List<RunDashboardService> getRunConfigurations() {
    myServiceLock.readLock().lock();
    try {
      return myServices.stream().flatMap(s -> s.stream()).collect(Collectors.toList());
    }
    finally {
      myServiceLock.readLock().unlock();
    }
  }

  private List<RunContentDescriptor> filterByContent(List<RunContentDescriptor> descriptors) {
    return ContainerUtil.filter(descriptors, descriptor -> {
      Content content = descriptor.getAttachedContent();
      return content != null && content.getManager() == myContentManager;
    });
  }

  @Override
  public boolean isShowConfigurations() {
    return myShowConfigurations;
  }

  @Override
  public void setShowConfigurations(boolean value) {
    myShowConfigurations = value;

    // Ensure dashboard tree gets focus before tool window content update.
    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    if (toolWindowManager == null) return;
    toolWindowManager.invokeLater(() -> {
      if (myProject.isDisposed()) {
        return;
      }
      if (myToolWindowContentManager != null) {
        Content content = myToolWindowContentManager.getSelectedContent();
        if (content != null && content.equals(myToolWindowContent)) {
          myToolWindowContentManager.setSelectedContent(content, true);
        }
      }
    });
    // Hide or show dashboard tree at first in order to get focus events on tree component which will be added/removed from tool window.
    updateDashboard(false);
    // Add or remove dashboard tree content from tool window.
    updateToolWindowContent();
  }

  @Override
  public float getContentProportion() {
    return myState.contentProportion;
  }

  @Override
  public boolean isShowInDashboard(@NotNull RunConfiguration runConfiguration) {
    return myState.configurationTypes.contains(runConfiguration.getType().getId());
  }

  @Override
  @NotNull
  public Set<String> getTypes() {
    return Collections.unmodifiableSet(myState.configurationTypes);
  }

  @Override
  public void setTypes(@NotNull Set<String> types) {
    myState.configurationTypes.clear();
    myState.configurationTypes.addAll(types);
    if (!myState.configurationTypes.isEmpty()) {
      initToolWindowContentListeners();
    }
    syncConfigurations();
    updateDashboard(true);
  }

  @Override
  @NotNull
  public List<RunDashboardCustomizer> getCustomizers(@NotNull RunnerAndConfigurationSettings settings,
                                                     @Nullable RunContentDescriptor descriptor) {
    List<RunDashboardCustomizer> customizers = ContainerUtil.newSmartList();
    for (RunDashboardCustomizer customizer : EP_NAME.getExtensions()) {
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
        !filterByContent(ExecutionManagerImpl.getInstance(myProject).getDescriptors(s -> configuration.equals(s.getConfiguration())))
          .isEmpty()) {
      updateDashboard(withStructure);
    }
  }

  @NotNull
  @Override
  public Condition<Content> getReuseCondition() {
    return myReuseCondition;
  }

  private boolean canReuseContent(Content content) {
    RunContentDescriptor descriptor = RunContentManagerImpl.getRunContentDescriptorByContent(content);
    if (descriptor == null) return false;

    ExecutionManagerImpl executionManager = ExecutionManagerImpl.getInstance(myProject);
    Set<RunnerAndConfigurationSettings> descriptorConfigurations = executionManager.getConfigurations(descriptor);
    if (descriptorConfigurations.isEmpty()) return true;

    Set<RunConfiguration> storedConfigurations = new HashSet<>(RunManager.getInstance(myProject).getAllConfigurationsList());

    return descriptorConfigurations.stream().noneMatch(descriptorConfiguration -> {
      RunConfiguration configuration = descriptorConfiguration.getConfiguration();
      return isShowInDashboard(configuration) && storedConfigurations.contains(configuration);
    });
  }

  @Override
  public void updateDashboard(boolean withStructure) {
    myProject.getMessageBus().syncPublisher(ServiceEventListener.TOPIC).handle(
      new ServiceEventListener.ServiceEvent(
        RunConfigurationsServiceViewContributor.class
      ));

    if (Registry.is("ide.service.view")) return;

    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    if (toolWindowManager == null) return;

    toolWindowManager.invokeLater(() -> {
      if (myProject.isDisposed()) {
        return;
      }

      if (withStructure) {
        boolean available = hasContent();
        ToolWindow toolWindow = toolWindowManager.getToolWindow(getToolWindowId());
        if (toolWindow == null) {
          if (!myState.configurationTypes.isEmpty() || available) {
            toolWindow = createToolWindow(toolWindowManager, available);
          }
          if (available) {
            toolWindow.show(null);
          }
          return;
        }

        boolean doShow = !toolWindow.isAvailable() && available;
        toolWindow.setAvailable(available, null);
        if (doShow) {
          toolWindow.show(null);
        }
      }

      if (myDashboardContent != null) {
        myDashboardContent.updateContent(withStructure);
      }
    });
  }

  private ToolWindow createToolWindow(ToolWindowManager toolWindowManager, boolean available) {
    ToolWindow toolWindow = toolWindowManager.registerToolWindow(getToolWindowId(), true, ToolWindowAnchor.BOTTOM,
                                                                 myProject, true);
    toolWindow.setIcon(getToolWindowIcon());
    toolWindow.setAvailable(available, null);
    createToolWindowContent(toolWindow);
    return toolWindow;
  }

  private boolean hasContent() {
    return !getRunConfigurations().isEmpty();
  }

  private void updateToolWindowContent() {
    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    if (toolWindowManager == null) return;

    toolWindowManager.invokeLater(() -> {
      if (myProject.isDisposed()) {
        return;
      }

      if (myToolWindowContent == null || myToolWindowContentManager == null ||
          myToolWindowContentManagerListener == null) {
        return;
      }

      boolean containsConfigurationsContent = false;
      for (Content content : myToolWindowContentManager.getContents()) {
        if (myToolWindowContent.equals(content)) {
          containsConfigurationsContent = true;
          break;
        }
      }

      if (myShowConfigurations) {
        if (!containsConfigurationsContent) {
          myToolWindowContentManager.removeContentManagerListener(myToolWindowContentManagerListener);
          myDashboardToToolWindowContents.clear();
          myToolWindowContentManager.removeAllContents(true);
          myToolWindowContentManager.addContent(myToolWindowContent);
          myToolWindowContentManager.addContentManagerListener(myToolWindowContentManagerListener);
        }
        updateToolWindowContentTabHeader(myContentManager.getSelectedContent());
      }
      else {
        if (containsConfigurationsContent) {
          myToolWindowContentManager.removeContentManagerListener(myToolWindowContentManagerListener);
          myToolWindowContentManager.removeContent(myToolWindowContent, false);
          for (Content dashboardContent : myContentManager.getContents()) {
            addToolWindowContent(dashboardContent);
          }
          Content dashboardSelectedContent = myContentManager.getSelectedContent();
          if (dashboardSelectedContent == null && myContentManager.getContentCount() > 0) {
            dashboardSelectedContent = myContentManager.getContent(0);
            if (dashboardSelectedContent != null) {
              myContentManager.setSelectedContent(dashboardSelectedContent);
            }
          }
          Content contentToSelect = myDashboardToToolWindowContents.get(dashboardSelectedContent);
          if (contentToSelect != null) {
            myToolWindowContentManager.setSelectedContent(contentToSelect, true);
          }
          myToolWindowContentManager.addContentManagerListener(myToolWindowContentManagerListener);
        }
      }

      ToolWindow toolWindow = toolWindowManager.getToolWindow(getToolWindowId());
      if (toolWindow instanceof ToolWindowImpl) {
        ToolWindowContentUi contentUi = ((ToolWindowImpl)toolWindow).getContentUI();
        contentUi.revalidate();
        contentUi.repaint();
      }
    });
  }

  private void addToolWindowContent(Content dashboardContent) {
    if (myToolWindowContentManager == null) return;

    Content toolWindowContent =
      ContentFactory.SERVICE.getInstance().createContent(myDashboardContent, dashboardContent.getDisplayName(), false);
    toolWindowContent.setIcon(dashboardContent.getIcon());
    PropertyChangeListener propertyChangeListener = new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        final String property = evt.getPropertyName();
        if (Content.PROP_DISPLAY_NAME.equals(property)) {
          toolWindowContent.setDisplayName(dashboardContent.getDisplayName());
        }
        else if (Content.PROP_ICON.equals(property)) {
          toolWindowContent.setIcon(dashboardContent.getIcon());
        }
      }
    };
    Disposer.register(toolWindowContent, () -> dashboardContent.removePropertyChangeListener(propertyChangeListener));
    dashboardContent.addPropertyChangeListener(propertyChangeListener);
    toolWindowContent.setShouldDisposeContent(false);
    toolWindowContent.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);
    toolWindowContent.setHelpId(getToolWindowContextHelpId());
    myToolWindowContentManager.addContent(toolWindowContent);
    myDashboardToToolWindowContents.put(dashboardContent, toolWindowContent);
  }

  private void updateToolWindowContentTabHeader(@Nullable Content content) {
    if (content != null) {
      myToolWindowContent.setDisplayName(content.getDisplayName());
      myToolWindowContent.setIcon(content.getIcon());
      myToolWindowContent.setCloseable(true);
    }
    else {
      myToolWindowContent.setDisplayName(null);
      myToolWindowContent.setIcon(null);
      myToolWindowContent.setCloseable(false);
    }
  }

  private void syncConfigurations() {
    List<RunnerAndConfigurationSettings> settingsList = ContainerUtil
      .filter(RunManager.getInstance(myProject).getAllSettings(),
              settings -> isShowInDashboard(settings.getConfiguration()));
    List<List<RunDashboardServiceImpl>> result = new ArrayList<>();
    myServiceLock.writeLock().lock();
    try {
      for (RunnerAndConfigurationSettings settings : settingsList) {
        List<RunDashboardServiceImpl> syncedServices = getServices(settings);
        if (syncedServices == null) {
          syncedServices = ContainerUtil.newSmartList(new RunDashboardServiceImpl(settings));
        }
        result.add(syncedServices);
      }
      for (List<RunDashboardServiceImpl> settingServices : myServices) {
        RunDashboardService service = settingServices.get(0);
        if (service.getContent() != null && !settingsList.contains(service.getSettings())) {
          result.add(settingServices);
        }
      }
      myServices = result;
    }
    finally {
      myServiceLock.writeLock().unlock();
    }
  }

  private void addServiceContent(@NotNull Content content) {
    RunContentDescriptor descriptor = RunContentManagerImpl.getRunContentDescriptorByContent(content);
    Set<RunnerAndConfigurationSettings> settingsSet = ExecutionManagerImpl.getInstance(myProject).getConfigurations(descriptor);
    RunnerAndConfigurationSettings settings = ContainerUtil.getFirstItem(settingsSet);
    if (settings == null) return;

    myServiceLock.writeLock().lock();
    try {
      List<RunDashboardServiceImpl> settingsServices = getServices(settings);
      if (settingsServices == null) return;

      RunDashboardServiceImpl service = settingsServices.get(0);
      RunDashboardServiceImpl newService;
      if (service.getContent() == null) {
        newService = service;
      }
      else {
        newService = new RunDashboardServiceImpl(settings);
        settingsServices.add(newService);
      }
      newService.setContent(content);
      Disposer.register(content, () -> {
        newService.setContent(null);
        myServiceLock.writeLock().lock();
        try {
          List<RunDashboardServiceImpl> services = getServices(settings);
          if (services == null) return;

          if (services.size() > 1) {
            services.remove(newService);
          }
          else if (!isShowInDashboard(settings.getConfiguration()) ||
                   !RunManager.getInstance(myProject).getAllSettings().contains(settings)) {
            myServices.remove(services);
          }
        }
        finally {
          myServiceLock.writeLock().unlock();
          updateDashboard(true);
        }
      });
    }
    finally {
      myServiceLock.writeLock().unlock();
    }
  }

  @Nullable
  private List<RunDashboardServiceImpl> getServices(@NotNull RunnerAndConfigurationSettings settings) {
    for (List<RunDashboardServiceImpl> services : myServices) {
      if (services.get(0).getSettings().equals(settings)) {
        return services;
      }
    }
    return null;
  }

  @Nullable
  static RunnerLayoutUiImpl getRunnerLayoutUi(@Nullable RunContentDescriptor descriptor) {
    if (descriptor == null) return null;

    RunnerLayoutUi layoutUi = descriptor.getRunnerLayoutUi();
    return layoutUi instanceof RunnerLayoutUiImpl ? (RunnerLayoutUiImpl)layoutUi : null;
  }

  @Nullable
  @Override
  public State getState() {
    List<RuleState> ruleStates = myState.ruleStates;
    ruleStates.clear();
    for (RunDashboardGrouper grouper : myGroupers) {
      if (!grouper.getRule().isAlwaysEnabled()) {
        ruleStates.add(new RuleState(grouper.getRule().getName(), grouper.isEnabled()));
      }
    }
    if (myDashboardContent != null) {
      myState.contentProportion = myDashboardContent.getContentProportion();
    }
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
    if (!myState.configurationTypes.isEmpty()) {
      initToolWindowContentListeners();
    }
    for (RuleState ruleState : state.ruleStates) {
      for (RunDashboardGrouper grouper : myGroupers) {
        if (grouper.getRule().getName().equals(ruleState.name) && !grouper.getRule().isAlwaysEnabled()) {
          grouper.setEnabled(ruleState.enabled);
          break;
        }
      }
    }
    syncConfigurations();
  }

  static class State {
    public final Set<String> configurationTypes = new THashSet<>();
    public final List<RuleState> ruleStates = new ArrayList<>();
    public float contentProportion = DEFAULT_CONTENT_PROPORTION;
  }

  private static class RuleState {
    public String name;
    public boolean enabled = true;

    @SuppressWarnings("UnusedDeclaration")
    RuleState() {
    }

    RuleState(String name, boolean enabled) {
      this.name = name;
      this.enabled = enabled;
    }
  }

  private static class RunDashboardServiceImpl implements RunDashboardService {
    private final RunnerAndConfigurationSettings mySettings;
    private volatile Content myContent;

    RunDashboardServiceImpl(@NotNull RunnerAndConfigurationSettings settings) {
      mySettings = settings;
    }

    @NotNull
    @Override
    public RunnerAndConfigurationSettings getSettings() {
      return mySettings;
    }

    @Nullable
    @Override
    public RunContentDescriptor getDescriptor() {
      Content content = myContent;
      return content == null ? null : RunContentManagerImpl.getRunContentDescriptorByContent(content);
    }

    @Nullable
    @Override
    public Content getContent() {
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
      return mySettings.equals(service.mySettings) && Comparing.equal(myContent, service.myContent);
    }

    @Override
    public int hashCode() {
      int result = mySettings.hashCode();
      result = 31 * result + (myContent != null ? myContent.hashCode() : 0);
      return result;
    }
  }

  private class DashboardContentManagerListener extends ContentManagerAdapter {
    @Override
    public void contentAdded(@NotNull ContentManagerEvent event) {
      if (myShowConfigurations || myToolWindowContentManager == null) return;

      Content toolWindowContent = myDashboardToToolWindowContents.get(event.getContent());
      if (toolWindowContent == null) {
        addToolWindowContent(event.getContent());
      }
      else {
        if (!myToolWindowContentManager.isSelected(toolWindowContent)) {
          myToolWindowContentManager.setSelectedContent(toolWindowContent);
        }
      }
    }

    @Override
    public void contentRemoved(@NotNull ContentManagerEvent event) {
      if (myShowConfigurations || myToolWindowContentManager == null) return;

      Content toolWindowContent = myDashboardToToolWindowContents.remove(event.getContent());
      if (toolWindowContent != null && toolWindowContent.getManager() != null) {
        myToolWindowContentManager.removeContentManagerListener(myToolWindowContentManagerListener);
        myToolWindowContentManager.removeContent(toolWindowContent, true);
        myToolWindowContentManager.addContentManagerListener(myToolWindowContentManagerListener);
      }
    }

    @Override
    public void selectionChanged(@NotNull ContentManagerEvent event) {
      if (event.getOperation() == ContentManagerEvent.ContentOperation.add) {
        contentAdded(event);
      }

      if (myToolWindowContentManager == null || myToolWindowContent == null || !myShowConfigurations) return;

      Content content = event.getOperation() == ContentManagerEvent.ContentOperation.add ? event.getContent() : null;
      updateToolWindowContentTabHeader(content);
    }
  }

  private class ToolWindowContentManagerListener extends ContentManagerAdapter {
    @Override
    public void contentRemoveQuery(@NotNull ContentManagerEvent event) {
      if (event.getContent().equals(myToolWindowContent)) {
        Content content = myContentManager.getSelectedContent();
        if (content != null) {
          myContentManager.removeContent(content, true);
        }
        event.consume();
        return;
      }

      Content dashboardContent = getDashboardContent(event.getContent());
      if (dashboardContent == null || dashboardContent.getManager() == null) return;

      myDashboardToToolWindowContents.remove(dashboardContent);
      if (!myContentManager.removeContent(dashboardContent, true)) {
        event.consume();
        myDashboardToToolWindowContents.put(dashboardContent, event.getContent());
      }
    }

    @Override
    public void selectionChanged(@NotNull ContentManagerEvent event) {
      if (event.getContent().equals(myToolWindowContent)) return;

      if (event.getOperation() != ContentManagerEvent.ContentOperation.add) return;

      Content dashboardContent = getDashboardContent(event.getContent());
      if (dashboardContent == null || dashboardContent.getManager() == null || myContentManager.isSelected(dashboardContent)) return;

      myContentManager.removeContentManagerListener(myContentManagerListener);
      myContentManager.setSelectedContent(dashboardContent);
      myContentManager.addContentManagerListener(myContentManagerListener);
    }

    private Content getDashboardContent(Content content) {
      for (Map.Entry<Content, Content> entry : myDashboardToToolWindowContents.entrySet()) {
        if (entry.getValue().equals(content)) {
          return entry.getKey();
        }
      }
      return null;
    }
  }
}
