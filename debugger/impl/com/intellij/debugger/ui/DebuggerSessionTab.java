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
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.runners.RestartAction;
import com.intellij.execution.ui.*;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.execution.ui.layout.LayoutViewOptions;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithActions;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.WindowManagerImpl;
import com.intellij.ui.content.*;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class DebuggerSessionTab implements LogConsoleManager, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.DebuggerSessionTab");

  private static final Icon WATCH_RETURN_VALUES_ICON = IconLoader.getIcon("/debugger/watchLastReturnValue.png");
  private static final Icon AUTO_VARS_ICONS = IconLoader.getIcon("/debugger/autoVariablesMode.png");

  private final Project myProject;

  private final VariablesPanel myVariablesPanel;
  private final MainWatchPanel myWatchPanel;

  private ExecutionConsole myConsole;
  private ProgramRunner myRunner;
  private volatile DebuggerSession myDebuggerSession;

  private RunContentDescriptor myRunContentDescriptor;

  private final MyDebuggerStateManager myStateManager = new MyDebuggerStateManager();

  private Map<AdditionalTabComponent, Content> myAdditionalContent = new HashMap<AdditionalTabComponent, Content>();
  private Map<AdditionalTabComponent, ContentManagerListener> myContentListeners =
    new HashMap<AdditionalTabComponent, ContentManagerListener>();

  private final LogFilesManager myManager;
  private FramesPanel myFramesPanel;
  private RunnerLayoutUi myUi;
  private ExecutionEnvironment myEnvironment;
  private RunProfile myConfiguration;

  public static final String BREAKPOINT_CONDITION = "breakpoint";

  public DebuggerSessionTab(Project project, String sessionName) {
    myProject = project;
    myManager = new LogFilesManager(project, this);


    myUi = RunnerLayoutUi.Factory.getInstance(project).create("JavaDebugger", DebuggerBundle.message("title.generic.debug.dialog"), sessionName, this);

    myUi.getDefaults().initTabDefaults(0, "Debugger", null).
        initFocusContent(DebuggerContentInfo.FRAME_CONTENT, BREAKPOINT_CONDITION).
        initFocusContent(DebuggerContentInfo.CONSOLE_CONTENT, LayoutViewOptions.STARTUP);

    final DefaultActionGroup focus = new DefaultActionGroup();
    focus.add(ActionManager.getInstance().getAction("Debugger.FocusOnBreakpoint"));
    myUi.getOptions().setAdditionalFocusActions(focus);

    final DebuggerSettings debuggerSettings = DebuggerSettings.getInstance();
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      getContextManager().addListener(new DebuggerContextListener() {
        public void changeEvent(DebuggerContextImpl newContext, int event) {
          switch (event) {
            case DebuggerSession.EVENT_DETACHED:
              myUi.updateActionsNow();

              if (debuggerSettings.HIDE_DEBUGGER_ON_PROCESS_TERMINATION) {
                try {
                  ExecutionManager.getInstance(getProject()).getContentManager().hideRunContent(DefaultDebugExecutor.getDebugExecutorInstance(), myRunContentDescriptor);
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
    myUi.getOptions().setTopToolbar(stepping, ActionPlaces.DEBUGGER_TOOLBAR);


    myWatchPanel = new MainWatchPanel(getProject(), getContextManager());
    myFramesPanel = new FramesPanel(getProject(), getContextManager());


    final AlertIcon breakpointAlert = new AlertIcon(IconLoader.getIcon("/debugger/breakpointAlert.png"));

    Content watches = myUi.createContent(DebuggerContentInfo.WATCHES_CONTENT, myWatchPanel, XDebuggerBundle.message("debugger.session.tab.watches.title"),
                                         XDebuggerUIConstants.WATCHES_TAB_ICON, null);
    watches.setCloseable(false);
    watches.setAlertIcon(breakpointAlert);
    final DefaultActionGroup watchesGroup = new DefaultActionGroup();
    addAction(watchesGroup, DebuggerActions.NEW_WATCH);
    addAction(watchesGroup, DebuggerActions.ADD_TO_WATCH);
    addAction(watchesGroup, DebuggerActions.REMOVE_WATCH);
    watches.setActions(watchesGroup, ActionPlaces.DEBUGGER_TOOLBAR, myWatchPanel.getTree());
    myUi.addContent(watches, 0, PlaceInGrid.right, false);


    Content framesContent = myUi.createContent(DebuggerContentInfo.FRAME_CONTENT, myFramesPanel, XDebuggerBundle.message("debugger.session.tab.frames.title"),
                                               XDebuggerUIConstants.FRAMES_TAB_ICON, null);
    framesContent.setCloseable(false);
    framesContent.setAlertIcon(breakpointAlert);

    final DefaultActionGroup framesGroup = new DefaultActionGroup();

    addAction(framesGroup, DebuggerActions.POP_FRAME);
    CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    framesGroup.add(actionsManager.createPrevOccurenceAction(myFramesPanel.getOccurenceNavigator()));
    framesGroup.add(actionsManager.createNextOccurenceAction(myFramesPanel.getOccurenceNavigator()));

    framesContent.setActions(framesGroup, ActionPlaces.DEBUGGER_TOOLBAR, myFramesPanel.getFramesList());
    myUi.addContent(framesContent, 0, PlaceInGrid.left, false);

    myVariablesPanel = new VariablesPanel(myProject, myStateManager, this);
    myVariablesPanel.getFrameTree().setAutoVariablesMode(debuggerSettings.AUTO_VARIABLES_MODE);
    Content vars = myUi.createContent(DebuggerContentInfo.VARIABLES_CONTENT, myVariablesPanel, XDebuggerBundle.message("debugger.session.tab.variables.title"),
                                      XDebuggerUIConstants.VARIABLES_TAB_ICON, null);
    vars.setCloseable(false);
    vars.setAlertIcon(breakpointAlert);
    final DefaultActionGroup varsGroup = new DefaultActionGroup();
    addAction(varsGroup, DebuggerActions.EVALUATE_EXPRESSION);
    varsGroup.add(new WatchLastMethodReturnValueAction());
    varsGroup.add(new AutoVarsSwitchAction());
    vars.setActions(varsGroup, ActionPlaces.DEBUGGER_TOOLBAR, myVariablesPanel.getTree());
    myUi.addContent(vars, 0, PlaceInGrid.center, false);

    myUi.addListener(new ContentManagerAdapter() {
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
    }, this);
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


    myUi.removeContent(myUi.findContent(DebuggerContentInfo.CONSOLE_CONTENT), true);

    Content console = myUi.createContent(DebuggerContentInfo.CONSOLE_CONTENT, myConsole.getComponent(),
                                         XDebuggerBundle.message("debugger.session.tab.console.content.name"),
                                         XDebuggerUIConstants.CONSOLE_TAB_ICON, myConsole.getPreferredFocusableComponent());

    console.setCloseable(false);
    attachNotificationTo(console);

    final DefaultActionGroup consoleActions = new DefaultActionGroup();
    if (myConsole instanceof ConsoleView) {
      AnAction[] actions = ((ConsoleView)myConsole).createUpDownStacktraceActions();
      for (AnAction goaction : actions) {
        consoleActions.add(goaction);
      }
    }

    console.setActions(consoleActions, ActionPlaces.DEBUGGER_TOOLBAR, myConsole.getPreferredFocusableComponent());

    myUi.addContent(console, 1, PlaceInGrid.bottom, false);

    if (myConfiguration instanceof RunConfigurationBase && ((RunConfigurationBase)myConfiguration).needAdditionalConsole()) {
      myManager.initLogConsoles((RunConfigurationBase)myConfiguration, myRunContentDescriptor.getProcessHandler());
    }

    DefaultActionGroup group = new DefaultActionGroup();
    final Executor executor = DefaultDebugExecutor.getDebugExecutorInstance();
    RestartAction restarAction = new RestartAction(executor,
                                                   myRunner, myRunContentDescriptor.getProcessHandler(), XDebuggerUIConstants.DEBUG_AGAIN_ICON,
                                                   myRunContentDescriptor, myEnvironment);
    group.add(restarAction);
    restarAction.registerShortcut(myUi.getComponent());

    addActionToGroup(group, XDebuggerActions.RESUME);
    addActionToGroup(group, XDebuggerActions.PAUSE);
    //group.add(new KillProcessAction());
    addActionToGroup(group, IdeActions.ACTION_STOP_PROGRAM);

    group.addSeparator();

    addActionToGroup(group, XDebuggerActions.VIEW_BREAKPOINTS);
    addActionToGroup(group, XDebuggerActions.MUTE_BREAKPOINTS);

    group.addSeparator();
    addAction(group, DebuggerActions.EXPORT_THREADS);
    group.addSeparator();

    group.add(myUi.getOptions().getLayoutActions());

    group.addSeparator();

    group.add(new CloseAction(executor, myRunContentDescriptor, getProject()));
    group.add(new ContextHelpAction(executor.getHelpId()));

    myUi.getOptions().setLeftToolbar(group, ActionPlaces.DEBUGGER_TOOLBAR);


    return myRunContentDescriptor;
  }

  private void attachNotificationTo(final Content content) {
    if (myConsole instanceof ObservableConsoleView) {
      ObservableConsoleView observable = (ObservableConsoleView)myConsole;
      observable.addChangeListener(new ObservableConsoleView.ChangeListener() {
        public void contentAdded(final Collection<ConsoleViewContentType> types) {
          if (types.contains(ConsoleViewContentType.ERROR_OUTPUT) || types.contains(ConsoleViewContentType.NORMAL_OUTPUT)) {
            content.fireAlert();
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

    final LogConsole log = new LogConsole(myProject, new File(path), skippedContent, name, false) {
      public boolean isActive() {
        final Content logContent = content.get();
        return logContent != null && logContent.isSelected();
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
    myUi.addListener(l, this);
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
      myUi.removeListener(myContentListeners.remove(componentToRemove));
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
      ExecutionManager.getInstance(getProject()).getContentManager().toFrontRunContent(DefaultDebugExecutor.getDebugExecutorInstance(), myRunContentDescriptor);
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
                                              final ExecutionEnvironment env)
    throws ExecutionException {
    disposeSession();
    myDebuggerSession = session;
    myRunner = runner;
    myEnvironment = env;
    myConfiguration = env.getRunProfile();

    if (myConfiguration instanceof RunConfigurationBase) {
      myManager.registerFileMatcher((RunConfigurationBase)myConfiguration);
    }

    session.getContextManager().addListener(new DebuggerContextListener() {
      public void changeEvent(DebuggerContextImpl newContext, int event) {
        if (myUi.isDisposed()) return;

        attractFramesOnPause(event);

        myStateManager.fireStateChanged(newContext, event);
      }
    });
    return initUI(getDebugProcess().getExecutionResult());
  }

  private void attractFramesOnPause(final int event) {
    if (DebuggerSession.EVENT_PAUSE == event) {
      myUi.attractBy(BREAKPOINT_CONDITION);
    } else if (DebuggerSession.EVENT_RESUME == event) {
      myUi.clearAttractionBy(BREAKPOINT_CONDITION);
    }
  }

  public DebuggerSession getSession() {
    return myDebuggerSession;
  }

  public void addAdditionalTabComponent(final AdditionalTabComponent tabComponent, final String id) {
    addLogComponent(tabComponent);
  }

  private Content addLogComponent(final AdditionalTabComponent tabComponent) {
    @NonNls final String id = "Log-" + tabComponent.getTabTitle();
    final Content logContent = myUi.createContent(id, (ComponentWithActions)tabComponent, tabComponent.getTabTitle(),
                                                  IconLoader.getIcon("/fileTypes/text.png"), tabComponent.getPreferredFocusableComponent());
    logContent.setCloseable(false);
    logContent.setDescription(tabComponent.getTooltip());
    myAdditionalContent.put(tabComponent, logContent);
    myUi.addContent(logContent);
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
    myUi.removeContent(content, true);
  }

  public void showFramePanel() {
    myUi.selectAndFocus(myUi.findContent(DebuggerContentInfo.FRAME_CONTENT), false);
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

}
