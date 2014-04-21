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

import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerContextListener;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.ui.DebuggerContentInfo;
import com.intellij.debugger.ui.impl.ThreadsPanel;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.ui.content.Content;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XSuspendContext;
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
        ThreadsPanel panel = new ThreadsPanel(myJavaSession.getProject(), myStateManager);
        Content threadsContent = ui.createContent(
          DebuggerContentInfo.THREADS_CONTENT, panel, XDebuggerBundle.message("debugger.session.tab.threads.title"),
          AllIcons.Debugger.Threads, null);
        threadsContent.setCloseable(false);
        ui.addContent(threadsContent, 0, PlaceInGrid.left, true);
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
      return session != null ? session.getContextManager().getContext() : DebuggerContextImpl.EMPTY_CONTEXT;
    }

    @Override
    public void setState(DebuggerContextImpl context, int state, int event, String description) {
      final DebuggerSession session = myJavaSession;
      if (session != null) {
        session.getContextManager().setState(context, state, event, description);
      }
    }
  }
}
