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
import com.intellij.debugger.ui.impl.MainWatchPanel;
import com.intellij.debugger.ui.impl.VariablesPanel;
import com.intellij.debugger.ui.impl.WatchDebuggerTree;
import com.intellij.debugger.ui.impl.watch.*;
import com.intellij.diagnostic.logging.AdditionalTabComponent;
import com.intellij.diagnostic.logging.LogConsole;
import com.intellij.diagnostic.logging.LogConsoleManager;
import com.intellij.diagnostic.logging.LogFilesManager;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.runners.RestartAction;
import com.intellij.execution.ui.*;
import com.intellij.execution.ui.layout.RunnerLayoutUi;
import com.intellij.execution.ui.layout.impl.RunnerLayout;
import com.intellij.execution.ui.layout.impl.View;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.WindowManagerImpl;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.*;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class DebuggerSessionTab implements LogConsoleManager, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.DebuggerSessionTab");

  private static final Icon DEBUG_AGAIN_ICON = IconLoader.getIcon("/actions/startDebugger.png");

  private static final Icon WATCHES_ICON = IconLoader.getIcon("/debugger/watches.png");
  private static final Icon WATCH_RETURN_VALUES_ICON = IconLoader.getIcon("/debugger/watchLastReturnValue.png");
  private static final Icon AUTO_VARS_ICONS = IconLoader.getIcon("/debugger/autoVariablesMode.png");

  private final Project myProject;

  private final VariablesPanel myVariablesPanel;
  private final MainWatchPanel myWatchPanel;

  private ExecutionConsole myConsole;
  private ProgramRunner myRunner;
  private RunProfile myConfiguration;
  private volatile DebuggerSession myDebuggerSession;

  private RunnerSettings myRunnerSettings;
  private ConfigurationPerRunnerSettings myConfigurationSettings;
  private RunContentDescriptor myRunContentDescriptor;

  private boolean myIsJustStarted = true;

  private final MyDebuggerStateManager myStateManager = new MyDebuggerStateManager();

  private Map<AdditionalTabComponent, Content> myAdditionalContent = new HashMap<AdditionalTabComponent, Content>();
  private Map<AdditionalTabComponent, ContentManagerListener> myContentListeners =
    new HashMap<AdditionalTabComponent, ContentManagerListener>();

  private final LogFilesManager myManager;
  private FramesPanel myFramesPanel;
  private RunnerLayoutUi myUi;

  public DebuggerSessionTab(Project project, String sessionName) {
    myProject = project;
    myManager = new LogFilesManager(project, this);


    myUi = new RunnerLayoutUi(project, this, DebuggerSettings.getInstance().getLayoutSettings().getLayout("JavaDebugger"), DebuggerBundle.message("title.generic.debug.dialog"), sessionName);

    myUi.addDefaultTab(0, "Debugger", null);

    final DebuggerSettings debuggerSettings = DebuggerSettings.getInstance();
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      getContextManager().addListener(new DebuggerContextListener() {
        public void changeEvent(DebuggerContextImpl newContext, int event) {
          switch (event) {
            case DebuggerSession.EVENT_DETACHED:
              myUi.updateActionsImmediately();

              if (debuggerSettings.HIDE_DEBUGGER_ON_PROCESS_TERMINATION) {
                try {
                  ExecutionManager.getInstance(getProject()).getContentManager().hideRunContent(myRunner.getInfo(), myRunContentDescriptor);
                }
                catch (NullPointerException e) {
                  //if we can get closeProcess after the project have been closed
                  LOG.debug(e);
                }
              }
              break;
          }
        }
      });
    }

    DefaultActionGroup stepping = new DefaultActionGroup();
    ActionManager actionManager = ActionManager.getInstance();
    stepping.add(actionManager.getAction(DebuggerActions.SHOW_EXECUTION_POINT));
    stepping.addSeparator();
    stepping.add(actionManager.getAction(DebuggerActions.STEP_OVER));
    stepping.add(actionManager.getAction(DebuggerActions.STEP_INTO));
    stepping.add(actionManager.getAction(DebuggerActions.FORCE_STEP_INTO));
    stepping.add(actionManager.getAction(DebuggerActions.STEP_OUT));
    stepping.addSeparator();
    stepping.add(actionManager.getAction(DebuggerActions.RUN_TO_CURSOR));
    myUi.setTopToolbar(stepping, ActionPlaces.DEBUGGER_TOOLBAR);


    myWatchPanel = new MainWatchPanel(getProject(), getContextManager());
    myFramesPanel = new FramesPanel(getProject(), getContextManager());


    Content watches = myUi.createContent(DebuggerContentInfo.WATCHES_CONTENT, myWatchPanel, DebuggerBundle.message("debugger.session.tab.watches.title"), WATCHES_ICON);
    final DefaultActionGroup watchesGroup = new DefaultActionGroup();
    addAction(watchesGroup, DebuggerActions.NEW_WATCH);
    addAction(watchesGroup, DebuggerActions.ADD_TO_WATCH);
    addAction(watchesGroup, DebuggerActions.REMOVE_WATCH);
    watches.setActions(watchesGroup, ActionPlaces.DEBUGGER_TOOLBAR, myWatchPanel.getTree());
    myUi.addContent(watches, 0, View.PlaceInGrid.right, false);


    Content framesContent = myUi.createContent(DebuggerContentInfo.FRAME_CONTENT, myFramesPanel, DebuggerBundle.message("debugger.session.tab.frames.title"),
                                    IconLoader.getIcon("/debugger/frame.png"));
    final DefaultActionGroup framesGroup = new DefaultActionGroup();

    addAction(framesGroup, DebuggerActions.POP_FRAME);
    CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    framesGroup.add(actionsManager.createPrevOccurenceAction(myFramesPanel.getOccurenceNavigator()));
    framesGroup.add(actionsManager.createNextOccurenceAction(myFramesPanel.getOccurenceNavigator()));

    framesContent.setActions(framesGroup, ActionPlaces.DEBUGGER_TOOLBAR, myFramesPanel.getFramesList());
    myUi.addContent(framesContent, 0, View.PlaceInGrid.left, false);

    myVariablesPanel = new VariablesPanel(myProject, myStateManager, this);
    myVariablesPanel.getFrameTree().setAutoVariablesMode(debuggerSettings.AUTO_VARIABLES_MODE);
    Content vars = myUi.createContent(DebuggerContentInfo.VARIABLES_CONTENT, myVariablesPanel, DebuggerBundle.message("debugger.session.tab.variables.title"),
                                  IconLoader.getIcon("/debugger/value.png"));
    final DefaultActionGroup varsGroup = new DefaultActionGroup();
    addAction(varsGroup, DebuggerActions.EVALUATE_EXPRESSION);
    varsGroup.add(new WatchLastMethodReturnValueAction());
    varsGroup.add(new AutoVarsSwitchAction());
    vars.setActions(varsGroup, ActionPlaces.DEBUGGER_TOOLBAR, myVariablesPanel.getTree());
    myUi.addContent(vars, 0, View.PlaceInGrid.center, false);

    myUi.getContentManager().addContentManagerListener(new ContentManagerAdapter() {
      public void selectionChanged(ContentManagerEvent event) {
        final Content content = event.getContent();
        if (content.getComponent() instanceof DebuggerView) {
          final DebuggerView view = (DebuggerView)content.getComponent();
          if (content.isSelected()) {
            view.setUpdateEnabled(true);
            if (view.isRefreshNeeded()) {
              view.rebuildIfVisible(DebuggerSession.EVENT_CONTEXT);
            }
          }
          else {
            view.setUpdateEnabled(false);
          }
        }
      }
    });


    myUi.setSelected(framesContent);
  }

  private static RunnerLayout getLayoutSettings() {
    return DebuggerSettings.getInstance().getLayoutSettings().getLayout("JavaDebugger");
  }

  private Project getProject() {
    return myProject;
  }

  public MainWatchPanel getWatchPanel() {
    return myWatchPanel;
  }

  private RunContentDescriptor initUI(ExecutionResult executionResult) {

    myConsole = executionResult.getExecutionConsole();
    myRunContentDescriptor = new RunContentDescriptor(myConsole, executionResult.getProcessHandler(), myUi.getComponent(), getSessionName());

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return myRunContentDescriptor;
    }


    myUi.removeContent(DebuggerContentInfo.CONSOLE_CONTENT, true);

    Content console = myUi.createContent(DebuggerContentInfo.CONSOLE_CONTENT, myConsole.getComponent(), DebuggerBundle.message("debugger.session.tab.console.content.name"),
                            IconLoader.getIcon("/debugger/console.png"),
                            myConsole.getPreferredFocusableComponent());

    attachNotificationTo(console);

    final DefaultActionGroup consoleActions = new DefaultActionGroup();
    if (myConsole instanceof ConsoleView) {
      AnAction[] actions = ((ConsoleView)myConsole).createUpDownStacktraceActions();
      for (AnAction goaction : actions) {
        consoleActions.add(goaction);
      }
    }

    console.setActions(consoleActions, ActionPlaces.DEBUGGER_TOOLBAR, myConsole.getPreferredFocusableComponent());

    final ContentManager contentManager = myUi.getContentManager();
    Content[] contents = contentManager.getContents();
    final Content[] selected = contentManager.getSelectedContents();
    contentManager.removeAllContents(false);

    myUi.addContent(console, 1, View.PlaceInGrid.bottom, false);
    for (Content each : contents) {
      contentManager.addContent(each);
    }
    for (Content each : selected) {
      contentManager.addSelectedContent(each);
    }


    if (myConfiguration instanceof RunConfigurationBase && ((RunConfigurationBase)myConfiguration).needAdditionalConsole()) {
      myManager.initLogConsoles((RunConfigurationBase)myConfiguration, myRunContentDescriptor.getProcessHandler());
    }

    DefaultActionGroup group = new DefaultActionGroup();
    RestartAction restarAction = new RestartAction(myRunner, myConfiguration, myRunContentDescriptor.getProcessHandler(), DEBUG_AGAIN_ICON,
                                                   myRunContentDescriptor, myRunnerSettings, myConfigurationSettings);
    group.add(restarAction);
    restarAction.registerShortcut(myUi.getComponent());

    addActionToGroup(group, DebuggerActions.RESUME);
    addActionToGroup(group, DebuggerActions.PAUSE);
    addActionToGroup(group, IdeActions.ACTION_STOP_PROGRAM);

    group.addSeparator();

    addActionToGroup(group, XDebuggerActions.VIEW_BREAKPOINTS);
    addActionToGroup(group, DebuggerActions.MUTE_BREAKPOINTS);

    group.addSeparator();
    addAction(group, DebuggerActions.EXPORT_THREADS);
    group.addSeparator();

    group.add(myUi.getLayoutActions());

    group.addSeparator();

    group.add(new CloseAction(myRunner.getInfo(), myRunContentDescriptor, getProject()));
    group.add(new ContextHelpAction(myRunner.getInfo().getHelpId()));

    myUi.setLeftToolbar(group, ActionPlaces.DEBUGGER_TOOLBAR);


    return myRunContentDescriptor;
  }

  private void attachNotificationTo(final Content content) {
    if (myConsole instanceof ObservableConsoleView) {
      ObservableConsoleView observable = (ObservableConsoleView)myConsole;
      final Content toNotify = content;
      observable.addChangeListener(new ObservableConsoleView.ChangeListener() {
        public void contentAdded(final Collection<ConsoleViewContentType> types) {
          if (types.contains(ConsoleViewContentType.ERROR_OUTPUT) || types.contains(ConsoleViewContentType.NORMAL_OUTPUT)) {
            toNotify.fireAlert();
          }
        }
      }, content);
    }
  }

  private static void addAction(DefaultActionGroup group, String actionId) {
    group.add(ActionManager.getInstance().getAction(actionId));
  }

  public void addLogConsole(final String name, final String path, final long skippedContent) {
    final Ref<Content> content = new Ref<Content>();

    final LogConsole log = new LogConsole(myProject, new File(path), skippedContent, name) {
      public boolean isActive() {
        return myUi.isActive(content.get());
      }
    };
    log.attachStopLogConsoleTrackingListener(myRunContentDescriptor.getProcessHandler());
    content.set(addLogComponent(log));
    final ContentManagerAdapter l = new ContentManagerAdapter() {
      public void selectionChanged(final ContentManagerEvent event) {
        log.activate();
      }
    };
    myContentListeners.put(log, l);
    myUi.getContentManager().addContentManagerListener(l);
    Disposer.register(this, new Disposable() {
      public void dispose() {
        myUi.getContentManager().removeContentManagerListener(myContentListeners.remove(log));
      }
    });
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
      myUi.getContentManager().removeContentManagerListener(myContentListeners.remove(componentToRemove));
      removeAdditionalTabComponent(componentToRemove);
    }
  }

  private static void addActionToGroup(final DefaultActionGroup group, final String actionId) {
    AnAction action = ActionManager.getInstance().getAction(actionId);
    if (action != null) group.add(action);
  }


  public void dispose() {
    disposeSession();
    myVariablesPanel.dispose();
    myWatchPanel.dispose();
    myManager.unregisterFileMatcher();
    myConsole = null;
  }

  private void disposeSession() {
    final DebuggerSession session = myDebuggerSession;
    myDebuggerSession = null;
    if (session != null) {
      session.dispose();
    }
  }

  @Nullable
  private DebugProcessImpl getDebugProcess() {
    final DebuggerSession session = myDebuggerSession;
    return session != null ? session.getProcess() : null;
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
    final DebuggerSession session = myDebuggerSession;
    if (session == null || session.getState() != DebuggerSession.STATE_PAUSED) {
      return null;
    }
    JTree tree = myVariablesPanel.getFrameTree();
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

  public RunContentDescriptor attachToSession(final DebuggerSession session,
                                              final ProgramRunner runner,
                                              final RunProfile runProfile,
                                              final RunnerSettings runnerSettings,
                                              final ConfigurationPerRunnerSettings configurationPerRunnerSettings)
    throws ExecutionException {
    disposeSession();
    myDebuggerSession = session;
    myRunner = runner;
    myRunnerSettings = runnerSettings;
    myConfigurationSettings = configurationPerRunnerSettings;
    myConfiguration = runProfile;

    if (myConfiguration instanceof RunConfigurationBase) {
      myManager.registerFileMatcher((RunConfigurationBase)myConfiguration);
    }

    session.getContextManager().addListener(new DebuggerContextListener() {
      public void changeEvent(DebuggerContextImpl newContext, int event) {
        myStateManager.fireStateChanged(newContext, event);
      }
    });
    return initUI(getDebugProcess().getExecutionResult());
  }

  public DebuggerSession getSession() {
    return myDebuggerSession;
  }

  public void addAdditionalTabComponent(final AdditionalTabComponent tabComponent) {
    addLogComponent(tabComponent);
  }

  private Content addLogComponent(final AdditionalTabComponent tabComponent) {
    Content logContent = createContent(tabComponent.getComponent(), tabComponent.getTabTitle(), IconLoader.getIcon("/fileTypes/text.png"), "Log-" + tabComponent.getTabTitle(),
                                       tabComponent.getPreferredFocusableComponent());
    logContent.setDescription(tabComponent.getTooltip());
    myAdditionalContent.put(tabComponent, logContent);
    myUi.getContentManager().addContent(logContent);
    Disposer.register(this, new Disposable() {
      public void dispose() {
        removeAdditionalTabComponent(tabComponent);
      }
    });

    return logContent;
  }

  public void removeAdditionalTabComponent(AdditionalTabComponent component) {
    component.dispose();
    final Content content = myAdditionalContent.remove(component);
    myUi.getContentManager().removeContent(content, true);
  }

  public void restoreLayout() {
    myUi.restoreLayout();
  }

  public void showFramePanel() {
    myUi.toFront(DebuggerContentInfo.FRAME_CONTENT);
  }

  private class MyDebuggerStateManager extends DebuggerStateManager {
    public void fireStateChanged(DebuggerContextImpl newContext, int event) {
      super.fireStateChanged(newContext, event);
    }

    public DebuggerContextImpl getContext() {
      final DebuggerSession session = myDebuggerSession;
      return session != null ? session.getContextManager().getContext() : DebuggerContextImpl.EMPTY_CONTEXT;
    }

    public void setState(DebuggerContextImpl context, int state, int event, String description) {
      final DebuggerSession session = myDebuggerSession;
      if (session != null) {
        session.getContextManager().setState(context, state, event, description);
      }
    }
  }

  private class AutoVarsSwitchAction extends ToggleAction {
    private volatile boolean myAutoModeEnabled;
    private static final String myAutoModeText = "Auto-Variables Mode";
    private static final String myDefaultModeText = "All-Variables Mode";

    public AutoVarsSwitchAction() {
      super("", "", AUTO_VARS_ICONS);
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
      myVariablesPanel.getFrameTree().setAutoVariablesMode(enabled);
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
        presentation.setText(process == null ? actionText : myTextUnavailable);
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

  private static Content createContent(JComponent component, String displayName, Icon icon, String kind, JComponent focusable) {
    final Content content = getContentFactory().createContent(component, displayName, false);
    content.putUserData(RunnerLayoutUi.CONTENT_TYPE, kind);
    content.putUserData(View.ID, kind);
    content.setIcon(icon);
    if (focusable != null) {
      content.setPreferredFocusableComponent(focusable);
    }
    return content;
  }

  private static ContentFactory getContentFactory() {
    return PeerFactory.getInstance().getContentFactory();
  }

  public static RunnerLayout getLayout() {
    return getLayoutSettings();
  }
}
