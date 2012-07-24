/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.debugger.ui;

import com.intellij.debugger.DebugUIEnvironment;
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
import com.intellij.debugger.ui.impl.ThreadsPanel;
import com.intellij.debugger.ui.impl.VariablesPanel;
import com.intellij.debugger.ui.impl.WatchDebuggerTree;
import com.intellij.debugger.ui.impl.watch.*;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.filters.ExceptionFilters;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ExecutionConsoleEx;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.content.AlertIcon;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.tabs.PinToolwindowTabAction;
import com.intellij.unscramble.ThreadDumpPanel;
import com.intellij.unscramble.ThreadState;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.ui.DebuggerSessionTabBase;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.util.List;

public class DebuggerSessionTab extends DebuggerSessionTabBase implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.DebuggerSessionTab");

  private static final Icon WATCH_RETURN_VALUES_ICON = AllIcons.Debugger.WatchLastReturnValue;
  private static final Icon AUTO_VARS_ICONS = AllIcons.Debugger.AutoVariablesMode;

  private final VariablesPanel myVariablesPanel;
  private final MainWatchPanel myWatchPanel;

  private volatile DebuggerSession myDebuggerSession;

  private final MyDebuggerStateManager myStateManager = new MyDebuggerStateManager();

  private final FramesPanel myFramesPanel;
  private DebugUIEnvironment myEnvironment;

  private final ThreadsPanel myThreadsPanel;
  private static final String THREAD_DUMP_CONTENT_PREFIX = "Dump";
  private final Icon myIcon;

  public DebuggerSessionTab(final Project project, final String sessionName, @Nullable final Icon icon) {
    super(project, "JavaDebugger", sessionName);

    myIcon = icon;

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
    stepping.add(actionManager.getAction(DebuggerActions.POP_FRAME));
    stepping.addSeparator();
    stepping.add(actionManager.getAction(DebuggerActions.RUN_TO_CURSOR));
    stepping.addSeparator();
    stepping.add(actionManager.getAction(DebuggerActions.EVALUATE_EXPRESSION));
    myUi.getOptions().setTopToolbar(stepping, ActionPlaces.DEBUGGER_TOOLBAR);


    myWatchPanel = new MainWatchPanel(getProject(), getContextManager());
    myFramesPanel = new FramesPanel(getProject(), getContextManager());


    final AlertIcon breakpointAlert = new AlertIcon(AllIcons.Debugger.BreakpointAlert);

    // watches
    Content watches = myUi.createContent(DebuggerContentInfo.WATCHES_CONTENT, myWatchPanel, XDebuggerBundle.message("debugger.session.tab.watches.title"),
                                         XDebuggerUIConstants.WATCHES_TAB_ICON, null);
    watches.setCloseable(false);
    watches.setAlertIcon(breakpointAlert);
    myUi.addContent(watches, 0, PlaceInGrid.right, false);

    // frames
    Content framesContent = myUi.createContent(DebuggerContentInfo.FRAME_CONTENT, myFramesPanel, XDebuggerBundle.message("debugger.session.tab.frames.title"),
                                               XDebuggerUIConstants.FRAMES_TAB_ICON, myFramesPanel.getFramesList());
    framesContent.setCloseable(false);
    framesContent.setAlertIcon(breakpointAlert);

    myUi.addContent(framesContent, 0, PlaceInGrid.left, false);

    // variables
    myVariablesPanel = new VariablesPanel(getProject(), myStateManager, this);
    myVariablesPanel.getFrameTree().setAutoVariablesMode(debuggerSettings.AUTO_VARIABLES_MODE);
    Content vars = myUi.createContent(DebuggerContentInfo.VARIABLES_CONTENT, myVariablesPanel, XDebuggerBundle.message("debugger.session.tab.variables.title"),
                                      XDebuggerUIConstants.VARIABLES_TAB_ICON, null);
    vars.setCloseable(false);
    vars.setAlertIcon(breakpointAlert);
    myUi.addContent(vars, 0, PlaceInGrid.center, false);

    // threads
    myThreadsPanel = new ThreadsPanel(project, getContextManager());
    Content threadsContent = myUi.createContent(DebuggerContentInfo.THREADS_CONTENT, myThreadsPanel, XDebuggerBundle.message("debugger.session.tab.threads.title"), XDebuggerUIConstants.THREADS_TAB_ICON, null);
    threadsContent.setCloseable(false);
    //threadsContent.setAlertIcon(breakpointAlert);

    //final DefaultActionGroup threadsGroup = new DefaultActionGroup();
    //threadsContent.setActions(threadsGroup, ActionPlaces.DEBUGGER_TOOLBAR, threadsPanel.getThreadsTree());

    myUi.addContent(threadsContent, 0, PlaceInGrid.left, true);

    for (Content each : myUi.getContents()) {
      updateStatus(each);
    }

    myUi.addListener(new ContentManagerAdapter() {
      public void selectionChanged(ContentManagerEvent event) {
        updateStatus(event.getContent());
      }
    }, this);
  }

  private static void updateStatus(final Content content) {
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

  public MainWatchPanel getWatchPanel() {
    return myWatchPanel;
  }

  @Override
  public RunContentDescriptor getRunContentDescriptor() {
    return myRunContentDescriptor;
  }

  private RunContentDescriptor initUI(ExecutionResult executionResult) {

    myConsole = executionResult.getExecutionConsole();
    myRunContentDescriptor = new RunContentDescriptor(myConsole, executionResult.getProcessHandler(), myUi.getComponent(), getSessionName(), myIcon);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return myRunContentDescriptor;
    }


    myUi.removeContent(myUi.findContent(DebuggerContentInfo.CONSOLE_CONTENT), true);

    Content console = null;
    if (myConsole instanceof ExecutionConsoleEx) {
      ((ExecutionConsoleEx)myConsole).buildUi(myUi);
      console = myUi.findContent(DebuggerContentInfo.CONSOLE_CONTENT);
      if (console == null) {
        LOG.debug("Reuse console created with non-debug runner");
      }
    }
    if (console == null) {
      console = myUi.createContent(DebuggerContentInfo.CONSOLE_CONTENT, myConsole.getComponent(),
                                           XDebuggerBundle.message("debugger.session.tab.console.content.name"),
                                           XDebuggerUIConstants.CONSOLE_TAB_ICON, myConsole.getPreferredFocusableComponent());

      console.setCloseable(false);
      myUi.addContent(console, 1, PlaceInGrid.bottom, false);
    }
    attachNotificationTo(console);

    if (myConsole != null) {
      Disposer.register(this, myConsole);
    }

    final DefaultActionGroup consoleActions = new DefaultActionGroup();
    if (myConsole instanceof ConsoleView) {
      AnAction[] actions = ((ConsoleView)myConsole).createConsoleActions();
      for (AnAction goaction : actions) {
        consoleActions.add(goaction);
      }
    }
    console.setActions(consoleActions, ActionPlaces.DEBUGGER_TOOLBAR, myConsole.getPreferredFocusableComponent());

    myEnvironment.initLogs(myRunContentDescriptor, getLogManager());

    DefaultActionGroup group = new DefaultActionGroup();

    if (executionResult instanceof DefaultExecutionResult) {
      final AnAction[] actions = ((DefaultExecutionResult)executionResult).getRestartActions();
      if (actions != null) {
        group.addAll(actions);
        if (actions.length > 0) {
          group.addSeparator();
        }
      }
    }
    final AnAction[] profileActions = executionResult.getActions();
    group.addAll(profileActions);

    addActionToGroup(group, XDebuggerActions.RESUME);
    addActionToGroup(group, XDebuggerActions.PAUSE);
    addActionToGroup(group, IdeActions.ACTION_STOP_PROGRAM);
    if (executionResult instanceof DefaultExecutionResult) {
      group.addAll(((DefaultExecutionResult)executionResult).getAdditionalStopActions());
    }

    group.addSeparator();

    addActionToGroup(group, XDebuggerActions.VIEW_BREAKPOINTS);
    addActionToGroup(group, XDebuggerActions.MUTE_BREAKPOINTS);

    group.addSeparator();
    addAction(group, DebuggerActions.EXPORT_THREADS);
    addAction(group, DebuggerActions.DUMP_THREADS);
    group.addSeparator();

    group.add(myUi.getOptions().getLayoutActions());

    final AnAction[] commonSettings = myUi.getOptions().getSettingsActionsList();
    final AnAction commonSettingsList = myUi.getOptions().getSettingsActions();

    final DefaultActionGroup settings = new DefaultActionGroup("DebuggerSettings", true) {
      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setText(ActionsBundle.message("group.XDebugger.settings.text"));
        e.getPresentation().setIcon(commonSettingsList.getTemplatePresentation().getIcon());
      }

      @Override
      public boolean isDumbAware() {
        return true;
      }
    };
    for (AnAction each : commonSettings) {
      settings.add(each);
    }
    if (commonSettings.length > 0) {
      settings.addSeparator();
    }
    settings.add(new WatchLastMethodReturnValueAction());
    settings.add(new AutoVarsSwitchAction());
    settings.addSeparator();
    addActionToGroup(settings, XDebuggerActions.AUTO_TOOLTIP);

    group.add(settings);

    group.addSeparator();

    addActionToGroup(group, PinToolwindowTabAction.ACTION_NAME);

    myEnvironment.initActions(myRunContentDescriptor, group);

    myUi.getOptions().setLeftToolbar(group, ActionPlaces.DEBUGGER_TOOLBAR);

    return myRunContentDescriptor;
  }

  private static void addAction(DefaultActionGroup group, String actionId) {
    group.add(ActionManager.getInstance().getAction(actionId));
  }

  private static void addActionToGroup(final DefaultActionGroup group, final String actionId) {
    AnAction action = ActionManager.getInstance().getAction(actionId);
    if (action != null) group.add(action);
  }


  public void dispose() {
    disposeSession();
    myFramesPanel.dispose();
    myVariablesPanel.dispose();
    myWatchPanel.dispose();
    myThreadsPanel.dispose();
    myConsole = null;
    super.dispose();
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

  public String getSessionName() {
    return myEnvironment.getEnvironment().getSessionName();
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

  public RunContentDescriptor attachToSession(final DebuggerSession session, DebugUIEnvironment environment) throws ExecutionException {
    disposeSession();
    myDebuggerSession = session;
    myEnvironment = environment;

    session.getContextManager().addListener(new DebuggerContextListener() {
      public void changeEvent(DebuggerContextImpl newContext, int event) {
        if (!myUi.isDisposed()) {
          attractFramesOnPause(event);
          myStateManager.fireStateChanged(newContext, event);
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              if (!myUi.isDisposed()) {
                myUi.updateActionsNow();
              }
            }
          });
        }
      }
    });
    return initUI(session.getProcess().getExecutionResult());
  }

  private void attractFramesOnPause(final int event) {
    if (DebuggerSession.EVENT_PAUSE == event) {
      myUi.attractBy(XDebuggerUIConstants.LAYOUT_VIEW_BREAKPOINT_CONDITION);
    }
    else if (DebuggerSession.EVENT_RESUME == event) {
      myUi.clearAttractionBy(XDebuggerUIConstants.LAYOUT_VIEW_BREAKPOINT_CONDITION);
    }
  }

  public DebuggerSession getSession() {
    return myDebuggerSession;
  }

  public void showFramePanel() {
    myUi.selectAndFocus(myUi.findContent(DebuggerContentInfo.FRAME_CONTENT), true, false);
  }

  private int myThreadDumpsCount = 0;
  private int myCurrentThreadDumpId = 1;

  public void addThreadDump(List<ThreadState> threads) {
    final Project project = getProject();
    final TextConsoleBuilder consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
    List<Filter> filters = ExceptionFilters.getFilters(myDebuggerSession.getSearchScope());
    for (Filter filter : filters) {
      consoleBuilder.addFilter(filter);
    }
    final ConsoleView consoleView = consoleBuilder.getConsole();
    final DefaultActionGroup toolbarActions = new DefaultActionGroup();
    consoleView.allowHeavyFilters();
    final ThreadDumpPanel panel = new ThreadDumpPanel(project, consoleView, toolbarActions, threads);

    final Icon icon = null;
    final String id = createThreadDumpContentId();
    final Content content = myUi.createContent(id, panel, id, icon, null);
    content.setCloseable(true);
    content.setDescription("Thread Dump");
    myUi.addContent(content);
    myUi.selectAndFocus(content, true, true);
    myThreadDumpsCount += 1;
    myCurrentThreadDumpId += 1;
    Disposer.register(this, new Disposable() {
      public void dispose() {
        myUi.removeContent(content, true);
      }
    });
    Disposer.register(content, new Disposable() {
      public void dispose() {
        myThreadDumpsCount -= 1;
        if (myThreadDumpsCount == 0) {
          myCurrentThreadDumpId = 1;
        }
      }
    });
    myUi.selectAndFocus(content, true, false);
    if (threads.size() > 0) {
      panel.selectStackFrame(0);
    }
  }

  private String createThreadDumpContentId() {
    return THREAD_DUMP_CONTENT_PREFIX + " #" + myCurrentThreadDumpId;
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
      super("", DebuggerBundle.message("action.watch.method.return.value.description"), null);
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
