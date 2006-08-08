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
import com.intellij.debugger.ui.impl.ThreadsPanel;
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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.WindowManagerImpl;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.*;
import com.intellij.util.ui.tree.TreeModelAdapter;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
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

public class DebuggerSessionTab implements LogConsoleManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.DebuggerSessionTab");

  private static final Icon DEBUG_AGAIN_ICON = IconLoader.getIcon("/actions/startDebugger.png");

  private static final Icon CONSOLE_ICON = IconLoader.getIcon("/debugger/console.png");
  private static final Icon THREADS_ICON = IconLoader.getIcon("/debugger/threads.png");
  private static final Icon FRAME_ICON = IconLoader.getIcon("/debugger/frame.png");
  private static final Icon WATCHES_ICON = IconLoader.getIcon("/debugger/watches.png");

  private static Key<Key> CONTENT_KIND = Key.create("ContentKind");
  public static Key CONSOLE_CONTENT = Key.create("ConsoleContent");
  public static Key THREADS_CONTENT = Key.create("ThreadsContent");
  public static Key FRAME_CONTENT = Key.create("FrameContent");
  public static Key WATCHES_CONTENT = Key.create("WatchesContent");

  private final Project myProject;
  private final ContentManager myViewsContentManager;

  private JPanel myToolBarPanel;
  private ActionToolbar myFirstToolbar;
  private ActionToolbar mySecondToolbar;

  /**
   * 4 debugger views
   */
  private final JPanel myContentPanel;
  private final FramePanel myFramePanel;
  private final ThreadsPanel myThreadsPanel;
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
  private static final Key LOG_CONTENTS = Key.create("LogContent");

  private Map<Object, Content>  myAdditionalContent = new HashMap<Object, Content>();
  private final LogFilesManager myManager;

  public DebuggerSessionTab(Project project) {
    myProject = project;
    myManager = new LogFilesManager(project, this);
    myContentPanel = new JPanel(new BorderLayout());
    if(!ApplicationManager.getApplication().isUnitTestMode()) {
      getContextManager().addListener(new DebuggerContextListener() {
        public void changeEvent(DebuggerContextImpl newContext, int event) {
          switch(event) {
            case DebuggerSession.EVENT_DETACHED:
              DebuggerSettings settings = DebuggerSettings.getInstance();

              myFirstToolbar.updateActionsImmediately();
              mySecondToolbar.updateActionsImmediately();

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
                final Content frameView = findContent(FRAME_CONTENT);
                final Content watchView = findContent(WATCHES_CONTENT);
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

    myWatchPanel = new MainWatchPanel(getProject(), getContextManager()) {
      protected boolean shouldRebuildNow() {
        return myViewsContentManager.getSelectedContent().getComponent() == this;
      }
    };
    updateWatchTreeTab();

    myFramePanel = new FramePanel(getProject(), getContextManager()) {
      protected boolean shouldRebuildNow() {
        return myViewsContentManager.getSelectedContent().getComponent() == this;
      }
    };

    myThreadsPanel = new ThreadsPanel(getProject(), getContextManager()) {
      protected boolean shouldRebuildNow() {
        return myViewsContentManager.getSelectedContent().getComponent() == this;
      }
    };

    TabbedPaneContentUI ui = new TabbedPaneContentUI(SwingConstants.TOP);
    myViewsContentManager = PeerFactory.getInstance().getContentFactory().createContentManager(ui, false, getProject());

    Content content = PeerFactory.getInstance().getContentFactory()
      .createContent(myThreadsPanel, DebuggerBundle.message("debugger.session.tab.threads.title"), false);
    content.setIcon(THREADS_ICON);
    content.putUserData(CONTENT_KIND, THREADS_CONTENT);
    myViewsContentManager.addContent(content);

    content = PeerFactory.getInstance().getContentFactory().createContent(myFramePanel,
                                                                          DebuggerBundle.message("debugger.session.tab.frames.title"), false);
    content.setIcon(FRAME_ICON);
    content.putUserData(CONTENT_KIND, FRAME_CONTENT);
    myViewsContentManager.addContent(content);

    content = PeerFactory.getInstance().getContentFactory().createContent(myWatchPanel,
                                                                          DebuggerBundle.message("debugger.session.tab.watches.title"), false);
    content.setIcon(WATCHES_ICON);
    content.putUserData(CONTENT_KIND, WATCHES_CONTENT);
    myViewsContentManager.addContent(content);

    myViewsContentManager.addContentManagerListener(new ContentManagerAdapter() {
      public void selectionChanged(ContentManagerEvent event) {
        Content selectedContent = myViewsContentManager.getSelectedContent();
        if (selectedContent != null) {
          JComponent component = selectedContent.getComponent();
          if (component instanceof DebuggerPanel) {
            DebuggerPanel panel = (DebuggerPanel)component;
            if (panel.isNeedsRefresh()) {
              panel.rebuildWhenVisible();
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




    content = PeerFactory.getInstance().getContentFactory().createContent(myConsole.getComponent(), DebuggerBundle.message(
      "debugger.session.tab.console.content.name"), false);
    content.setIcon(CONSOLE_ICON);
    content.putUserData(CONTENT_KIND, CONSOLE_CONTENT);

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
    mySecondToolbar = createSecondToolbar();

    myToolBarPanel = new JPanel(new GridLayout(1, 2));
    myToolBarPanel.add(myFirstToolbar.getComponent());
    myToolBarPanel.add(mySecondToolbar.getComponent());
    myContentPanel.add(myToolBarPanel, BorderLayout.WEST);

    return myRunContentDescriptor;
  }

  private void initAdditionalTabs() {
    clearLogContents();
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
    base.createAdditionalTabComponents();
    for (Object key : base.getAdditionalTabKeys()) {
      final AdditionalTabComponent tabComponent = base.getAdditionalTabComponent(key);
      Content logContent = PeerFactory.getInstance().getContentFactory().createContent(tabComponent.getComponent(), tabComponent.getTabTitle(), false);
      logContent.putUserData(CONTENT_KIND, LOG_CONTENTS);
      myAdditionalContent.put(key, logContent);
      myViewsContentManager.addContent(logContent);
    }
  }

  public void addLogConsole(final String path, final boolean skipContent, final Project project, final String name, final RunConfigurationBase configuration) {
    final LogConsole log = new LogConsole(project, new File(path), skipContent, name){
      public boolean isActive() {
        final Content selectedContent = myViewsContentManager.getSelectedContent();
        if (selectedContent == null){
          stopRunning();
          return false;
        }
        return selectedContent.getComponent() == this;
      }
    };
    log.attachStopLogConsoleTrackingListener(myRunContentDescriptor.getProcessHandler());

    configuration.addAdditionalTab(path, log);
  }

  public void removeLogConsole(final String path) {
    myViewsContentManager.removeContent(myAdditionalContent.get(path));
    if (myConfiguration instanceof RunConfigurationBase){
      final AdditionalTabComponent logConsole = ((RunConfigurationBase)myConfiguration).getAdditionalTabComponent(path);
      logConsole.dispose();
    }
  }

  private void clearLogContents() {
    for (int i = 0; i < myViewsContentManager.getContentCount(); i++) {
      final Content content = myViewsContentManager.getContent(i);
      if (content.getUserData(CONTENT_KIND) == LOG_CONTENTS){
        myViewsContentManager.removeContent(content);
      }
    }
  }

  private void updateWatchTreeTab() {
    class MyContentUpdater extends TreeModelAdapter {
      public void updateContent() {
        Content content = findContent(WATCHES_CONTENT);
        if (content != null) {
          int count = myWatchPanel.getWatchTree().getWatchCount();
          String displayName = count > 0
                               ? DebuggerBundle.message("debugger.session.tab.watches.title.with.size", count)
                               : DebuggerBundle.message("debugger.session.tab.watches.title");
          content.setDisplayName(displayName);
        }
      }
      public void treeStructureChanged(TreeModelEvent event) {
        if(event.getPath().length <= 1) {
          updateContent();
        }
      }
    }
    MyContentUpdater updater = new MyContentUpdater();
    updater.updateContent();
    myWatchPanel.getWatchTree().getModel().addTreeModelListener(updater);
  }

  private static ActionToolbar createSecondToolbar() {
    ActionManager actionManager = ActionManager.getInstance();
    DefaultActionGroup group = new DefaultActionGroup();
    AnAction action = actionManager.getAction(DebuggerActions.STEP_OVER);
    if (action != null) group.add(action);
    action = actionManager.getAction(DebuggerActions.STEP_INTO);
    if (action != null) group.add(action);
    action = actionManager.getAction(DebuggerActions.STEP_OUT);
    if (action != null) group.add(action);
    action = actionManager.getAction(DebuggerActions.FORCE_STEP_INTO);
    if (action != null) group.add(action);
    action = actionManager.getAction(DebuggerActions.POP_FRAME);
    if (action != null) group.add(action);
    action = actionManager.getAction(DebuggerActions.RUN_TO_CURSOR);
    if (action != null) group.add(action);
    action = actionManager.getAction(DebuggerActions.VIEW_BREAKPOINTS);
    if (action != null) group.add(action);
    action = actionManager.getAction(DebuggerActions.MUTE_BREAKPOINTS);
    if (action != null) group.add(action);
    action = actionManager.getAction(DebuggerActions.TOGGLE_STEP_SUSPEND_POLICY);
    if (action != null) group.add(action);

    return ActionManager.getInstance().createActionToolbar(ActionPlaces.DEBUGGER_TOOLBAR, group, false);
  }

  private ActionToolbar createFirstToolbar(RunContentDescriptor contentDescriptor, JComponent component) {
    DefaultActionGroup group = new DefaultActionGroup();
    ActionManager actionManager = ActionManager.getInstance();

    // first toolbar

    RestartAction restarAction = new RestartAction(myRunner, myConfiguration, contentDescriptor.getProcessHandler(), DEBUG_AGAIN_ICON,
                                                   contentDescriptor, myRunnerSettings, myConfigurationSettings);
    group.add(restarAction);
    restarAction.registerShortcut(component);
    AnAction action = actionManager.getAction(DebuggerActions.RESUME);
    if (action != null) group.add(action);
    action = actionManager.getAction(DebuggerActions.PAUSE);
    if (action != null) group.add(action);
    AnAction stopAction = actionManager.getAction(IdeActions.ACTION_STOP_PROGRAM);
    if (action != null) group.add(stopAction);
    action = actionManager.getAction(DebuggerActions.EVALUATE_EXPRESSION);
    if (action != null) group.add(action);
    group.add(new CloseAction(myRunner, contentDescriptor, getProject()));
    group.add(CommonActionsFactory.getCommonActionsFactory().createContextHelpAction(myRunner.getInfo().getHelpId()));
    return ActionManager.getInstance().createActionToolbar(ActionPlaces.DEBUGGER_TOOLBAR, group, false);
  }

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
    myThreadsPanel.dispose();
    myFramePanel.dispose();
    myWatchPanel.dispose();
    myViewsContentManager.removeAllContents();
    myAdditionalContent.clear();
    if (myConfiguration instanceof RunConfigurationBase){
      final RunConfigurationBase configurationBase = ((RunConfigurationBase)myConfiguration);
      for (final Object key : configurationBase.getAdditionalTabKeys()) {
        configurationBase.getAdditionalTabComponent(key).dispose();
      }

      myManager.unregisterFileMatcher();

      configurationBase.clearAdditionalTabs();
    }
    myConsole = null;
  }

  private void disposeSession() {
    if(myDebuggerSession != null) {
      myDebuggerSession.dispose();
    }
  }

  private DebugProcessImpl getDebugProcess() {
    return myDebuggerSession != null ? myDebuggerSession.getProcess() : null;
  }

  public void reuse(DebuggerSessionTab reuseSession) {
    getDebugProcess().setSuspendPolicy(reuseSession.getDebugProcess().getSuspendPolicy());
    DebuggerTreeNodeImpl[] watches = reuseSession.getWatchPanel().getWatchTree().getWatches();

    for (DebuggerTreeNodeImpl watch : watches) {
      getWatchPanel().getWatchTree().addWatch((WatchItemDescriptor)watch.getDescriptor());
    }

    reuseSession.initAdditionalTabs();
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

  private class MyDebuggerStateManager extends DebuggerStateManager {
    public void fireStateChanged(DebuggerContextImpl newContext, int event) {
      super.fireStateChanged(newContext, event);
    }

    public DebuggerContextImpl getContext() {
      return myDebuggerSession.getContextManager().getContext();
    }

    public void setState(DebuggerContextImpl context, int state, int event, String description) {
      myDebuggerSession.getContextManager().setState(context, state, event, description);
    }
  }
}
