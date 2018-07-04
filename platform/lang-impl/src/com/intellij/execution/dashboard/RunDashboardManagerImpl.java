// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.dashboard;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.dashboard.tree.RunDashboardGrouper;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManagerImpl;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.content.*;
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
import java.util.stream.Collectors;

/**
 * @author konstantin.aleev
 */
@State(
  name = "RunDashboard",
  storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
public class RunDashboardManagerImpl implements RunDashboardManager, PersistentStateComponent<RunDashboardManagerImpl.State> {
  private static final float DEFAULT_CONTENT_PROPORTION = 0.3f;
  @NonNls private static final String HELP_ID = "run-dashboard.reference";

  @NotNull private final Project myProject;
  @NotNull private final ContentManager myContentManager;
  @NotNull private final ContentManagerListener myContentManagerListener;

  @NotNull private State myState = new State();

  @NotNull private final List<RunDashboardGrouper> myGroupers;
  @NotNull private final Condition<Content> myReuseCondition;
  @NotNull private final AtomicBoolean myListenersInitialized = new AtomicBoolean();
  private boolean myShowConfigurations = true;

  private RunDashboardContent myDashboardContent;
  private Content myToolWindowContent;
  private ContentManager myToolWindowContentManager;
  private ContentManagerListener myToolWindowContentManagerListener;
  private final Map<Content, Content> myDashboardToToolWindowContents = new HashMap<>();

  public RunDashboardManagerImpl(@NotNull final Project project) {
    myProject = project;

    ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
    ContentUI contentUI = new PanelContentUI();
    myContentManager = contentFactory.createContentManager(contentUI, false, project);
    myContentManagerListener = new DashboardContentManagerListener();
    myContentManager.addContentManagerListener(myContentManagerListener);
    myReuseCondition = this::canReuseContent;

    myGroupers = Arrays.stream(RunDashboardGroupingRule.EP_NAME.getExtensions())
      .map(RunDashboardGrouper::new)
      .collect(Collectors.toList());
  }

  private void initToolWindowContentListeners() {
    if (!myListenersInitialized.compareAndSet(false, true)) return;

    MessageBusConnection connection = myProject.getMessageBus().connect(myProject);
    connection.subscribe(RunManagerListener.TOPIC, new RunManagerListener() {
      private volatile boolean myUpdateStarted;

      @Override
      public void runConfigurationAdded(@NotNull RunnerAndConfigurationSettings settings) {
        if (!myUpdateStarted) {
          updateDashboardIfNeeded(settings);
        }
      }

      @Override
      public void runConfigurationRemoved(@NotNull RunnerAndConfigurationSettings settings) {
        if (!myUpdateStarted) {
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
      public void contentChanged(boolean withStructure) {
        updateDashboard(withStructure);
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
      public void selectionChanged(ContentManagerEvent event) {
        updateToolWindowContent();
        updateDashboard(true);
      }

      @Override
      public void contentRemoved(ContentManagerEvent event) {
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
    return ToolWindowId.RUN_DASHBOARD;
  }

  @Override
  public Icon getToolWindowIcon() {
    return AllIcons.Toolwindows.ToolWindowRun; // TODO [konstantin.aleev] provide new icon
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
  public List<Pair<RunnerAndConfigurationSettings, RunContentDescriptor>> getRunConfigurations() {
    List<Pair<RunnerAndConfigurationSettings, RunContentDescriptor>> result = new ArrayList<>();

    List<RunnerAndConfigurationSettings> configurations = RunManager.getInstance(myProject).getAllSettings().stream()
      .filter(settings -> myState.configurationTypes.contains(settings.getType().getId()))
      .collect(Collectors.toList());

    ExecutionManagerImpl executionManager = ExecutionManagerImpl.getInstance(myProject);
    configurations.forEach(configurationSettings -> {
      List<RunContentDescriptor> descriptors = filterByContent(executionManager.getDescriptors(
        settings -> Comparing.equal(settings.getConfiguration(), configurationSettings.getConfiguration())));
      if (descriptors.isEmpty() && isShowInDashboard(configurationSettings.getConfiguration())) {
        result.add(Pair.create(configurationSettings, null));
      }
      else {
        descriptors.forEach(descriptor -> result.add(Pair.create(configurationSettings, descriptor)));
      }
    });

    // It is possible that run configuration was deleted or moved out from dashboard,
    // but there is a content descriptor for such run configuration.
    // It should be shown in the dashboard tree.
    List<RunConfiguration> storedConfigurations = configurations.stream().map(RunnerAndConfigurationSettings::getConfiguration)
      .collect(Collectors.toList());
    List<RunContentDescriptor> notStoredDescriptors = filterByContent(executionManager.getDescriptors(settings ->
      !storedConfigurations.contains(settings.getConfiguration())));
    notStoredDescriptors.forEach(descriptor -> {
      Set<RunnerAndConfigurationSettings> settings = executionManager.getConfigurations(descriptor);
      settings.forEach(setting -> result.add(Pair.create(setting, descriptor)));
    });

    return result;
  }

  private List<RunContentDescriptor> filterByContent(List<RunContentDescriptor> descriptors) {
    return descriptors.stream()
      .filter(descriptor -> {
        Content content = descriptor.getAttachedContent();
        return content != null && content.getManager() == myContentManager;
      })
      .collect(Collectors.toList());
  }

  @Override
  public boolean isShowConfigurations() {
    return myShowConfigurations;
  }

  @Override
  public void setShowConfigurations(boolean value) {
    myShowConfigurations = value;
    updateToolWindowContent();
    updateDashboard(false);
  }

  @Override
  public float getContentProportion() {
    return myState.contentProportion;
  }

  @Override
  public RunDashboardAnimator getAnimator() {
    if (myDashboardContent == null) return null;

    return myDashboardContent.getAnimator();
  }

  @Override
  public boolean isShowInDashboard(@NotNull RunConfiguration runConfiguration) {
    if (myState.configurationTypes.contains(runConfiguration.getType().getId())) {
      RunDashboardContributor contributor = getContributor(runConfiguration.getType());
      return contributor == null || contributor.isShowInDashboard(runConfiguration);
    }

    return false;
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
    updateDashboard(true);
  }

  @Override
  @Nullable
  public RunDashboardContributor getContributor(@NotNull ConfigurationType type) {
    for (RunDashboardContributor contributor : RunDashboardContributor.EP_NAME.getExtensions()) {
      if (type.equals(contributor.getType())) {
        return contributor;
      }
    }
    return null;
  }

  private void updateDashboardIfNeeded(@Nullable RunnerAndConfigurationSettings settings) {
    if (settings != null && (getContributor(settings.getType()) != null || isShowInDashboard(settings.getConfiguration()))) {
      updateDashboard(true);
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
  public void updateDashboard(final boolean withStructure) {
    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
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
    AppUIUtil.invokeLaterIfProjectAlive(myProject, () -> {
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
          Content contentToSelect = myDashboardToToolWindowContents.get(myContentManager.getSelectedContent());
          if (contentToSelect != null) {
            myToolWindowContentManager.setSelectedContent(contentToSelect);
          }
          myToolWindowContentManager.addContentManagerListener(myToolWindowContentManagerListener);
        }
      }

      ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
      if (toolWindowManager == null) return;

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

  private class DashboardContentManagerListener extends ContentManagerAdapter {
    @Override
    public void contentAdded(ContentManagerEvent event) {
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
    public void contentRemoved(ContentManagerEvent event) {
      if (myShowConfigurations || myToolWindowContentManager == null) return;

      Content toolWindowContent = myDashboardToToolWindowContents.remove(event.getContent());
      if (toolWindowContent != null && toolWindowContent.getManager() != null) {
        myToolWindowContentManager.removeContentManagerListener(myToolWindowContentManagerListener);
        myToolWindowContentManager.removeContent(toolWindowContent, true);
        myToolWindowContentManager.addContentManagerListener(myToolWindowContentManagerListener);
      }
    }

    @Override
    public void selectionChanged(ContentManagerEvent event) {
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
    public void contentRemoveQuery(ContentManagerEvent event) {
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
    public void selectionChanged(ContentManagerEvent event) {
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
