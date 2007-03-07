package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerContextListener;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.impl.DebuggerPanel;
import com.intellij.debugger.ui.impl.FramePanel;
import com.intellij.debugger.ui.impl.MainWatchPanel;
import com.intellij.debugger.ui.impl.WatchDebuggerTree;
import com.intellij.debugger.ui.impl.watch.*;
import com.intellij.diagnostic.logging.AdditionalTabComponent;
import com.intellij.diagnostic.logging.LogConsole;
import com.intellij.diagnostic.logging.LogConsoleManager;
import com.intellij.diagnostic.logging.LogFilesManager;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.*;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.runners.JavaProgramRunner;
import com.intellij.execution.runners.RestartAction;
import com.intellij.execution.ui.CloseAction;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.actions.CommonActionsFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.WindowManagerImpl;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.*;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class DebuggerSessionTab implements LogConsoleManager, DebuggerContentInfo, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.DebuggerSessionTab");

  private static final Icon DEBUG_AGAIN_ICON = IconLoader.getIcon("/actions/startDebugger.png");

  private static final Icon WATCHES_ICON = IconLoader.getIcon("/debugger/watches.png");
  private static final Icon WATCH_RETURN_VALUES_ICON = IconLoader.getIcon("/debugger/watchReturnValues.png");

  private final Project myProject;
  private final ContentManager myViewsContentManager;

  private JPanel myToolBarPanel;
  private ActionToolbar myFirstToolbar;

  private final JPanel myContentPanel;
  private final FramePanel myFramePanel;
  private final MainWatchPanel myWatchPanel;

  private ExecutionConsole  myConsole;
  private JavaProgramRunner myRunner;
  private RunProfile        myConfiguration;
  private DebuggerSession   myDebuggerSession;

  private RunnerSettings myRunnerSettings;
  private ConfigurationPerRunnerSettings myConfigurationSettings;
  private RunContentDescriptor myRunContentDescriptor;

  private boolean myIsJustStarted = true;

  private final MyDebuggerStateManager myStateManager = new MyDebuggerStateManager();

  private Map<AdditionalTabComponent, Content>  myAdditionalContent = new HashMap<AdditionalTabComponent, Content>();
  private Map<AdditionalTabComponent, ContentManagerListener>  myContentListeners = new HashMap<AdditionalTabComponent, ContentManagerListener>();

  private final LogFilesManager myManager;
  private Content myFramesContent;
  private Content myVarsContent;
  private Content myWatchesContent;
  private DebuggerContentUI myContentUI;

  public DebuggerSessionTab(Project project, String sessionName) {
    myProject = project;
    myManager = new LogFilesManager(project, this);
    myContentPanel = new JPanel(new BorderLayout());
    final DebuggerSettings debuggerSettings = DebuggerSettings.getInstance();
    if(!ApplicationManager.getApplication().isUnitTestMode()) {
      getContextManager().addListener(new DebuggerContextListener() {
        public void changeEvent(DebuggerContextImpl newContext, int event) {
          switch(event) {
            case DebuggerSession.EVENT_DETACHED:
              DebuggerSettings settings = debuggerSettings;

              myFirstToolbar.updateActionsImmediately();

              if (settings.HIDE_DEBUGGER_ON_PROCESS_TERMINATION) {
                try {
                  ExecutionManager.getInstance(getProject()).getContentManager().hideRunContent(myRunner, myRunContentDescriptor);
                }
                catch (NullPointerException e) {
                  //if we can get closeProcess after the project have been closed
                  LOG.debug(e);
                }
              }
              break;

            case DebuggerSession.EVENT_PAUSE:
              if (myIsJustStarted) {
                final Content frameView = findContent(DebuggerContentInfo.FRAME_CONTENT);
                final Content watchView = findContent(DebuggerContentInfo.WATCHES_CONTENT);
                if (frameView != null) {
                  Content content = myViewsContentManager.getSelectedContent();
                  if (content == null || content.equals(frameView) || content.equals(watchView)) {
                    return;
                  }
                  showFramePanel();
                }
                myIsJustStarted = false;
              }
          }
        }
      });
    }
    myContentUI = new DebuggerContentUI(this, ActionManager.getInstance(), DebuggerBundle.message("title.generic.debug.dialog") + " - " + sessionName);
    myViewsContentManager = getContentFactory().
      createContentManager(myContentUI, false, getProject());

    myWatchPanel = new MainWatchPanel(getProject(), getContextManager());

    myFramePanel = new FramePanel(getProject(), getContextManager()) {
      protected boolean isUpdateEnabled() {
        return getRootPane() != null;
      }
    };
    myFramePanel.getFrameTree().setAutoVariablesMode(debuggerSettings.AUTO_VARIABLES_MODE);

    myWatchesContent = createContent(myWatchPanel, DebuggerBundle.message("debugger.session.tab.watches.title"), WATCHES_ICON, WATCHES_CONTENT);
    final DefaultActionGroup watchesGroup = new DefaultActionGroup();
    addAction(watchesGroup, DebuggerActions.NEW_WATCH);
    addAction(watchesGroup, DebuggerActions.ADD_TO_WATCH);
    addAction(watchesGroup, DebuggerActions.REMOVE_WATCH);
    myWatchesContent.setActions(watchesGroup, ActionPlaces.DEBUGGER_TOOLBAR);

    myViewsContentManager.addContent(myWatchesContent);

    myFramesContent = createContent(myFramePanel, DebuggerBundle.message("debugger.session.tab.frames.title"), IconLoader.getIcon("/debugger/frame.png"), FRAME_CONTENT);
    final DefaultActionGroup framesGroup = new DefaultActionGroup();

    addAction(framesGroup, DebuggerActions.SHOW_EXECUTION_POINT);
    framesGroup.addSeparator();
    addAction(framesGroup, DebuggerActions.STEP_OVER);
    addAction(framesGroup, DebuggerActions.STEP_INTO);
    addAction(framesGroup, DebuggerActions.STEP_OUT);
    addAction(framesGroup, DebuggerActions.FORCE_STEP_INTO);
    framesGroup.addSeparator();
    addAction(framesGroup, IdeActions.ACTION_PREVIOUS_OCCURENCE);
    addAction(framesGroup, IdeActions.ACTION_NEXT_OCCURENCE);
    addAction(framesGroup, DebuggerActions.POP_FRAME);
    addAction(framesGroup, DebuggerActions.RUN_TO_CURSOR);
    myFramesContent.setActions(framesGroup, ActionPlaces.DEBUGGER_TOOLBAR);

    myViewsContentManager.addContent(myFramesContent);

    myVarsContent = createContent(myFramePanel.getVarsPanel(), DebuggerBundle.message("debugger.session.tab.variables.title"), IconLoader.getIcon("/debugger/value.png"), VARIABLES_CONTENT);
    final DefaultActionGroup varsGroup = new DefaultActionGroup();
    addAction(varsGroup, DebuggerActions.EVALUATE_EXPRESSION);
    varsGroup.add(new WatchLastMethodReturnValueAction());
    varsGroup.add(new AutoVarsSwitchAction());
    myVarsContent.setActions(varsGroup, ActionPlaces.DEBUGGER_TOOLBAR);
    myViewsContentManager.addContent(myVarsContent);

    myViewsContentManager.addContentManagerListener(new ContentManagerAdapter() {
      public void selectionChanged(ContentManagerEvent event) {
        Content selectedContent = myViewsContentManager.getSelectedContent();
        if (selectedContent != null) {
          JComponent component = selectedContent.getComponent();
          if (component instanceof DebuggerPanel) {
            DebuggerPanel panel = (DebuggerPanel)component;
            if (panel.isRefreshNeeded()) {
              panel.rebuildIfVisible(DebuggerSession.EVENT_CONTEXT);
            }
          }
        }
      }
    });

    myContentPanel.add(myViewsContentManager.getComponent(), BorderLayout.CENTER);
  }

  public void showFramePanel() {
    final Content content = findContent(FRAME_CONTENT);
    if (content != null) {
      myViewsContentManager.setSelectedContent(content);
    }
  }

  private Project getProject() {
    return myProject;
  }

  public MainWatchPanel getWatchPanel() {
    return myWatchPanel;
  }

  private RunContentDescriptor initUI(ExecutionResult executionResult) {

    myConsole = executionResult.getExecutionConsole();
    myRunContentDescriptor = new RunContentDescriptor(myConsole, executionResult.getProcessHandler(), myContentPanel, getSessionName());

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return myRunContentDescriptor;
    }

    Content content = findContent(CONSOLE_CONTENT);
    if(content != null) {
      myViewsContentManager.removeContent(content);
    }

    content = createContent(myConsole.getComponent(), DebuggerBundle.message(
      "debugger.session.tab.console.content.name"), IconLoader.getIcon("/debugger/console.png"), CONSOLE_CONTENT);

    final DefaultActionGroup consoleActions = new DefaultActionGroup();
    addAction(consoleActions, DebuggerActions.EXPORT_THREADS);
    content.setActions(consoleActions, ActionPlaces.DEBUGGER_TOOLBAR);

    Content[] contents = myViewsContentManager.getContents();
    myViewsContentManager.removeAllContents();

    myViewsContentManager.addContent(content);
    for (Content content1 : contents) {
      myViewsContentManager.addContent(content1);
    }

    if (myConfiguration instanceof RunConfigurationBase && !(myConfiguration instanceof JUnitConfiguration)){
      initAdditionalTabs();
    }

    if(myToolBarPanel != null) {
      myContentPanel.remove(myToolBarPanel);
    }

    myFirstToolbar  = createFirstToolbar(myRunContentDescriptor, myContentPanel);

    myToolBarPanel = new JPanel(new GridLayout(1, 1));
    myToolBarPanel.add(myFirstToolbar.getComponent());
    myContentPanel.add(myToolBarPanel, BorderLayout.WEST);

    return myRunContentDescriptor;
  }

  private void addAction(DefaultActionGroup group, String actionId) {
    group.add(ActionManager.getInstance().getAction(actionId));
  }

  private void initAdditionalTabs() {
    RunConfigurationBase base = (RunConfigurationBase)myConfiguration;
    final ArrayList<LogFileOptions> logFiles = base.getAllLogFiles();
    for (LogFileOptions logFile : logFiles) {
      if (logFile.isEnabled()) {
        final Set<String> paths = logFile.getPaths();
        for (String path : paths) {
          addLogConsole(path, logFile.isSkipContent(), myProject, logFile.getName(), (RunConfigurationBase)myConfiguration);
        }
      }
    }
    base.createAdditionalTabComponents(this, myRunContentDescriptor.getProcessHandler());
  }

  public void addLogConsole(final String path, final boolean skipContent, final Project project, final String name, final RunConfigurationBase configuration) {
    final LogConsole log = new LogConsole(project, new File(path), skipContent, name){
      public boolean isActive() {
        final Content selectedContent = myViewsContentManager.getSelectedContent();
        if (selectedContent == null) {
          return false;
        }
        return selectedContent.getComponent() == this;
      }
    };
    log.attachStopLogConsoleTrackingListener(myRunContentDescriptor.getProcessHandler());
    addAdditionalTabComponent(log);
    final ContentManagerAdapter l = new ContentManagerAdapter() {
      public void selectionChanged(final ContentManagerEvent event) {
        log.activate();
      }
    };
    myContentListeners.put(log, l);
    myViewsContentManager.addContentManagerListener(l);
  }

  public void removeLogConsole(final String path) {
    LogConsole componentToRemove = null;
    for (AdditionalTabComponent tabComponent : myAdditionalContent.keySet()) {
      if (tabComponent instanceof LogConsole) {
        final LogConsole console = (LogConsole)tabComponent;
        if (Comparing.strEqual(console.getPath(), path)) {
          componentToRemove = console;
          break;
        }
      }
    }
    if (componentToRemove != null) {
      myViewsContentManager.removeContentManagerListener(myContentListeners.remove(componentToRemove));
      removeAdditionalTabComponent(componentToRemove);
    }
  }

  private static void addActionToGroup(final DefaultActionGroup group, final String actionId) {
    AnAction action = ActionManager.getInstance().getAction(actionId);
    if (action != null) group.add(action);
  }

  private ActionToolbar createFirstToolbar(RunContentDescriptor contentDescriptor, JComponent component) {
    DefaultActionGroup group = new DefaultActionGroup();
    RestartAction restarAction = new RestartAction(myRunner, myConfiguration, contentDescriptor.getProcessHandler(), DEBUG_AGAIN_ICON,
                                                   contentDescriptor, myRunnerSettings, myConfigurationSettings);
    group.add(restarAction);
    restarAction.registerShortcut(component);

    addActionToGroup(group, DebuggerActions.RESUME);
    addActionToGroup(group, DebuggerActions.PAUSE);
    addActionToGroup(group, IdeActions.ACTION_STOP_PROGRAM);

    group.addSeparator();

    addActionToGroup(group, DebuggerActions.VIEW_BREAKPOINTS);
    addActionToGroup(group, DebuggerActions.MUTE_BREAKPOINTS);

    group.addSeparator();
    addActionToGroup(group, DebuggerActions.RESTORE_LAYOUT);

    group.addSeparator();

    group.add(new CloseAction(myRunner, contentDescriptor, getProject()));

    group.add(CommonActionsFactory.getCommonActionsFactory().createContextHelpAction(myRunner.getInfo().getHelpId()));

    return ActionManager.getInstance().createActionToolbar(ActionPlaces.DEBUGGER_TOOLBAR, group, false);
  }

  @Nullable
  private Content findContent(Key key) {
    if (myViewsContentManager != null) {
      Content[] contents = myViewsContentManager.getContents();
      for (Content content : contents) {
        Key kind = content.getUserData(CONTENT_KIND);
        if (key.equals(kind)) {
          return content;
        }
      }
    }
    return null;
  }

  public void dispose() {
    disposeSession();
    myFramePanel.dispose();
    myWatchPanel.dispose();
    myViewsContentManager.removeAllContents();
    for (AdditionalTabComponent tabComponent : myAdditionalContent.keySet()) {
      tabComponent.dispose();
    }
    myAdditionalContent.clear();
    myManager.unregisterFileMatcher();
    myConsole = null;
  }

  private void disposeSession() {
    if(myDebuggerSession != null) {
      myDebuggerSession.dispose();
    }
  }

  @Nullable
  private DebugProcessImpl getDebugProcess() {
    return myDebuggerSession != null ? myDebuggerSession.getProcess() : null;
  }

  public void reuse(DebuggerSessionTab reuseSession) {
    DebuggerTreeNodeImpl[] watches = reuseSession.getWatchPanel().getWatchTree().getWatches();

    final WatchDebuggerTree watchTree = getWatchPanel().getWatchTree();
    for (DebuggerTreeNodeImpl watch : watches) {
      watchTree.addWatch((WatchItemDescriptor)watch.getDescriptor());
    }
  }

  protected void toFront() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      ((WindowManagerImpl)WindowManager.getInstance()).getFrame(getProject()).toFront();
      ExecutionManager.getInstance(getProject()).getContentManager().toFrontRunContent(myRunner, myRunContentDescriptor);
    }
  }

  public String getSessionName() {
    return myConfiguration.getName();
  }

  public DebuggerStateManager getContextManager() {
    return myStateManager;
  }

  @Nullable
  public TextWithImports getSelectedExpression() {
    if (myDebuggerSession.getState() != DebuggerSession.STATE_PAUSED) {
      return null;
    }
    JTree tree = myFramePanel.getFrameTree();
    if (tree == null || !tree.hasFocus()) {
      tree = myWatchPanel.getWatchTree();
      if (tree == null || !tree.hasFocus()) {
        return null;
      }
    }
    TreePath path = tree.getSelectionPath();
    if (path == null) {
      return null;
    }
    DebuggerTreeNodeImpl node = (DebuggerTreeNodeImpl)path.getLastPathComponent();
    if (node == null) {
      return null;
    }
    NodeDescriptorImpl descriptor = node.getDescriptor();
    if (!(descriptor instanceof ValueDescriptorImpl)) {
      return null;
    }
    if (descriptor instanceof WatchItemDescriptor) {
      return ((WatchItemDescriptor)descriptor).getEvaluationText();
    }
    try {
      return DebuggerTreeNodeExpression.createEvaluationText(node, getContextManager().getContext());
    }
    catch (EvaluateException e) {
      return null;
    }
  }

  public RunContentDescriptor attachToSession(
    final DebuggerSession session,
    final JavaProgramRunner runner,
    final RunProfile runProfile,
    final RunnerSettings runnerSettings,
    final ConfigurationPerRunnerSettings configurationPerRunnerSettings) throws ExecutionException {
    disposeSession();
    myDebuggerSession = session;
    myRunner = runner;
    myRunnerSettings = runnerSettings;
    myConfigurationSettings  = configurationPerRunnerSettings;
    myConfiguration = runProfile;

    if (myConfiguration instanceof RunConfigurationBase) {
      myManager.registerFileMatcher((RunConfigurationBase)myConfiguration);
    }

    myDebuggerSession.getContextManager().addListener(new DebuggerContextListener() {
      public void changeEvent(DebuggerContextImpl newContext, int event) {
        myStateManager.fireStateChanged(newContext, event);
      }
    });
    return initUI(getDebugProcess().getExecutionResult());
  }

  public DebuggerSession getSession() {
    return myDebuggerSession;
  }

  public void addAdditionalTabComponent(AdditionalTabComponent tabComponent) {
    Content logContent = createContent(tabComponent.getComponent(), tabComponent.getTabTitle(), null, CONSOLE_CONTENT);
    logContent.setDescription(tabComponent.getTooltip());
    myAdditionalContent.put(tabComponent, logContent);
    myViewsContentManager.addContent(logContent);
  }

  public void removeAdditionalTabComponent(AdditionalTabComponent component) {
    component.dispose();
    final Content content = myAdditionalContent.remove(component);
    myViewsContentManager.removeContent(content);
  }

  public void restoreLayout() {
    myContentUI.restoreLayout();
  }

  private class MyDebuggerStateManager extends DebuggerStateManager {
    public void fireStateChanged(DebuggerContextImpl newContext, int event) {
      super.fireStateChanged(newContext, event);
    }

    public DebuggerContextImpl getContext() {
      return myDebuggerSession != null? myDebuggerSession.getContextManager().getContext() : DebuggerContextImpl.EMPTY_CONTEXT;
    }

    public void setState(DebuggerContextImpl context, int state, int event, String description) {
      myDebuggerSession.getContextManager().setState(context, state, event, description);
    }
  }

  private class AutoVarsSwitchAction extends ToggleAction {
    private volatile boolean myAutoModeEnabled;
    private final String myAutoModeText = "Auto-Variables Mode";
    private final String myDefaultModeText = "All-Variables Mode";

    public AutoVarsSwitchAction() {
      super("", "", WATCHES_ICON);
      myAutoModeEnabled = DebuggerSettings.getInstance().AUTO_VARIABLES_MODE;
    }

    public void update(final AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      final boolean autoModeEnabled = (Boolean)presentation.getClientProperty(SELECTED_PROPERTY);
      presentation.setText(autoModeEnabled ? myDefaultModeText : myAutoModeText);
    }

    public boolean isSelected(AnActionEvent e) {
      return myAutoModeEnabled;
    }

    public void setSelected(AnActionEvent e, boolean enabled) {
      myAutoModeEnabled = enabled;
      DebuggerSettings.getInstance().AUTO_VARIABLES_MODE = enabled;
      myFramePanel.getFrameTree().setAutoVariablesMode(enabled);
    }
  }

  private class WatchLastMethodReturnValueAction extends ToggleAction {
    private volatile boolean myWatchesReturnValues;
    private final String myTextEnable;
    private final String myTextUnavailable;
    private final String myMyTextDisable;

    public WatchLastMethodReturnValueAction() {
      super("", DebuggerBundle.message("action.watch.method.return.value.description"), WATCH_RETURN_VALUES_ICON);
      myWatchesReturnValues = DebuggerSettings.getInstance().WATCH_RETURN_VALUES;
      myTextEnable = DebuggerBundle.message("action.watches.method.return.value.enable");
      myMyTextDisable = DebuggerBundle.message("action.watches.method.return.value.disable");
      myTextUnavailable = DebuggerBundle.message("action.watches.method.return.value.unavailable.reason");
    }

    public void update(final AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      final boolean watchValues = (Boolean)presentation.getClientProperty(SELECTED_PROPERTY);
      final DebugProcessImpl process = getDebugProcess();
      final String actionText = watchValues ? myMyTextDisable : myTextEnable;
      if (process != null && process.canGetMethodReturnValue()) {
        presentation.setEnabled(true);
        presentation.setText(actionText);
      }
      else {
        presentation.setEnabled(false);
        presentation.setText(process == null? actionText : myTextUnavailable);
      }
    }

    public boolean isSelected(AnActionEvent e) {
      return myWatchesReturnValues;
    }

    public void setSelected(AnActionEvent e, boolean watch) {
      myWatchesReturnValues = watch;
      DebuggerSettings.getInstance().WATCH_RETURN_VALUES = watch;
      final DebugProcessImpl process = getDebugProcess();
      if (process != null) {
        process.setWatchMethodReturnValuesEnabled(watch);
      }
    }
  }

  private Content createContent(JComponent component, String displayName, Icon icon, Key kind) {
    final Content content = getContentFactory().createContent(component, displayName, false);
    content.putUserData(CONTENT_KIND, kind);
    content.setIcon(icon);
    return content;
  }

  private ContentFactory getContentFactory() {
    return PeerFactory.getInstance().getContentFactory();
  }
}
