/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.debugger.engine;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerContextListener;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.DebuggerContentInfo;
import com.intellij.debugger.ui.impl.ThreadsPanel;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.ui.XDebugTabLayouter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author egor
 */
public class JavaDebugProcess extends XDebugProcess {
  private final DebuggerSession myJavaSession;
  private final JavaDebuggerEditorsProvider myEditorsProvider;
  private final XBreakpointHandler<?>[] myBreakpointHandlers;
  private final MyDebuggerStateManager myStateManager = new MyDebuggerStateManager();
  private final NodeManagerImpl myNodeManager;

  public JavaDebugProcess(@NotNull XDebugSession session, DebuggerSession javaSession) {
    super(session);
    myJavaSession = javaSession;
    myEditorsProvider = new JavaDebuggerEditorsProvider();
    DebugProcessImpl process = javaSession.getProcess();
    myBreakpointHandlers = new XBreakpointHandler[]{
      new JavaBreakpointHandler.JavaLineBreakpointHandler(process),
      new JavaBreakpointHandler.JavaExceptionBreakpointHandler(process),
      new JavaBreakpointHandler.JavaFieldBreakpointHandler(process),
      new JavaBreakpointHandler.JavaMethodBreakpointHandler(process),
      new JavaBreakpointHandler.JavaWildcardBreakpointHandler(process),
    };
    process.addDebugProcessListener(new DebugProcessAdapter() {
      @Override
      public void paused(final SuspendContext suspendContext) {
        //DebugProcessImpl process = (DebugProcessImpl)suspendContext.getDebugProcess();
        //JdiSuspendContext context = new JdiSuspendContext(process, true);
        //getSession().positionReached(new JavaSuspendContext(context, PositionManagerImpl.DEBUGGER_VIEW_SUPPORT, null));
        ((SuspendContextImpl)suspendContext).initExecutionStacks();
        getSession().positionReached((XSuspendContext)suspendContext);
      }
    });

    myJavaSession.getContextManager().addListener(new DebuggerContextListener() {
      @Override
      public void changeEvent(DebuggerContextImpl newContext, int event) {
        myStateManager.fireStateChanged(newContext, event);
      }
    });
    myNodeManager = new NodeManagerImpl(session.getProject(), null) {
      @Override
      public DebuggerTreeNodeImpl createNode(final NodeDescriptor descriptor, EvaluationContext evaluationContext) {
        ((NodeDescriptorImpl)descriptor).setContext((EvaluationContextImpl)evaluationContext);
        final DebuggerTreeNodeImpl node = new DebuggerTreeNodeImpl(null, descriptor);
        ((NodeDescriptorImpl)descriptor).updateRepresentation((EvaluationContextImpl)evaluationContext, DescriptorLabelListener.DUMMY_LISTENER);
        return node;
      }
    };
  }

  @NotNull
  @Override
  public XDebuggerEditorsProvider getEditorsProvider() {
    return myEditorsProvider;
  }

  @Override
  public void startStepOver() {
    myJavaSession.stepOver(false);
  }

  @Override
  public void startStepInto() {
    myJavaSession.stepInto(false, null);
  }

  @Override
  public void startStepOut() {
    myJavaSession.stepOut();
  }

  @Override
  public void stop() {
  }

  @Override
  public void resume() {
    myJavaSession.resume();
  }

  @Override
  public void runToPosition(@NotNull XSourcePosition position) {
    Document document = FileDocumentManager.getInstance().getDocument(position.getFile());
    myJavaSession.runToCursor(document, position.getLine(), false);
  }

  @NotNull
  @Override
  public XBreakpointHandler<?>[] getBreakpointHandlers() {
    return myBreakpointHandlers;
  }

  @Override
  public boolean checkCanInitBreakpoints() {
    return false;
  }

  @Nullable
  @Override
  protected ProcessHandler doGetProcessHandler() {
    return myJavaSession.getProcess().getExecutionResult().getProcessHandler();
  }

  @NotNull
  @Override
  public ExecutionConsole createConsole() {
    return myJavaSession.getProcess().getExecutionResult().getExecutionConsole();
  }

  @NotNull
  @Override
  public XDebugTabLayouter createTabLayouter() {
    return new XDebugTabLayouter() {
      @Override
      public void registerAdditionalContent(@NotNull RunnerLayoutUi ui) {
        final ThreadsPanel panel = new ThreadsPanel(myJavaSession.getProject(), myStateManager);
        final Content threadsContent = ui.createContent(
          DebuggerContentInfo.THREADS_CONTENT, panel, XDebuggerBundle.message("debugger.session.tab.threads.title"),
          AllIcons.Debugger.Threads, null);
        threadsContent.setCloseable(false);
        ui.addContent(threadsContent, 0, PlaceInGrid.left, true);
        ui.addListener(new ContentManagerAdapter() {
          @Override
          public void selectionChanged(ContentManagerEvent event) {
            if (event.getContent() == threadsContent) {
              if (threadsContent.isSelected()) {
                panel.setUpdateEnabled(true);
                if (panel.isRefreshNeeded()) {
                  panel.rebuildIfVisible(DebuggerSession.EVENT_CONTEXT);
                }
              }
              else {
                panel.setUpdateEnabled(false);
              }
            }
          }
        }, myJavaSession.getProject());
      }
    };
  }

  private class MyDebuggerStateManager extends DebuggerStateManager {
    @Override
    public void fireStateChanged(DebuggerContextImpl newContext, int event) {
      super.fireStateChanged(newContext, event);
    }

    @Override
    public DebuggerContextImpl getContext() {
      final DebuggerSession session = myJavaSession;
      return session.getContextManager().getContext();
    }

    @Override
    public void setState(DebuggerContextImpl context, int state, int event, String description) {
      final DebuggerSession session = myJavaSession;
      session.getContextManager().setState(context, state, event, description);
    }
  }

  @Override
  public void registerAdditionalActions(@NotNull DefaultActionGroup leftToolbar, @NotNull DefaultActionGroup topToolbar) {
    Constraints beforeRunner = new Constraints(Anchor.BEFORE, "Runner.Layout");
    leftToolbar.add(Separator.getInstance(), beforeRunner);
    leftToolbar.add(ActionManager.getInstance().getAction(DebuggerActions.EXPORT_THREADS), beforeRunner);
    leftToolbar.add(ActionManager.getInstance().getAction(DebuggerActions.DUMP_THREADS), beforeRunner);
    leftToolbar.add(Separator.getInstance(), beforeRunner);

    final DefaultActionGroup settings = new DefaultActionGroup("DebuggerSettings", true) {
      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setText(ActionsBundle.message("group.XDebugger.settings.text"));
        e.getPresentation().setIcon(AllIcons.General.SecondaryGroup);
      }

      @Override
      public boolean isDumbAware() {
        return true;
      }
    };
    settings.add(new WatchLastMethodReturnValueAction());
    settings.add(new AutoVarsSwitchAction());
    settings.add(new UnmuteOnStopAction());
    settings.addSeparator();
    addActionToGroup(settings, XDebuggerActions.AUTO_TOOLTIP);

    leftToolbar.add(settings, new Constraints(Anchor.AFTER, "Runner.Layout"));
  }

  private static class AutoVarsSwitchAction extends ToggleAction {
    private volatile boolean myAutoModeEnabled;

    public AutoVarsSwitchAction() {
      super("", "", AllIcons.Debugger.AutoVariablesMode);
      myAutoModeEnabled = DebuggerSettings.getInstance().AUTO_VARIABLES_MODE;
    }

    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      final boolean autoModeEnabled = (Boolean)presentation.getClientProperty(SELECTED_PROPERTY);
      presentation.setText(autoModeEnabled ? "All-Variables Mode" : "Auto-Variables Mode");
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myAutoModeEnabled;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean enabled) {
      myAutoModeEnabled = enabled;
      DebuggerSettings.getInstance().AUTO_VARIABLES_MODE = enabled;
      //myVariablesPanel.getFrameTree().setAutoVariablesMode(enabled);
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

    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      final boolean watchValues = (Boolean)presentation.getClientProperty(SELECTED_PROPERTY);
      final DebugProcessImpl process = myJavaSession.getProcess();
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

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myWatchesReturnValues;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean watch) {
      myWatchesReturnValues = watch;
      DebuggerSettings.getInstance().WATCH_RETURN_VALUES = watch;
      final DebugProcessImpl process = myJavaSession.getProcess();
      if (process != null) {
        process.setWatchMethodReturnValuesEnabled(watch);
      }
    }
  }

  private static class UnmuteOnStopAction extends ToggleAction {
    private volatile boolean myUnmuteOnStop;

    private UnmuteOnStopAction() {
      super(DebuggerBundle.message("action.unmute.on.stop.text"), DebuggerBundle.message("action.unmute.on.stop.text"), null);
      myUnmuteOnStop = DebuggerSettings.getInstance().UNMUTE_ON_STOP;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myUnmuteOnStop;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myUnmuteOnStop = state;
      DebuggerSettings.getInstance().UNMUTE_ON_STOP = state;
    }
  }

  private static void addActionToGroup(final DefaultActionGroup group, final String actionId) {
    AnAction action = ActionManager.getInstance().getAction(actionId);
    if (action != null) group.add(action);
  }

  public NodeManagerImpl getNodeManager() {
    return myNodeManager;
  }
}
