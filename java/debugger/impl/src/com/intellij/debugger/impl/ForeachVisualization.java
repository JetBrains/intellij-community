/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.debugger.impl;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.requests.Requestor;
import com.intellij.debugger.ui.breakpoints.RunToCursorBreakpoint;
import com.intellij.debugger.ui.impl.watch.ArrayElementDescriptorImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.ui.UIUtil;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventIterator;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.request.EventRequest;
import org.jetbrains.annotations.Nullable;

/**
 * Created with IntelliJ IDEA.
 * User: zajac
 * Date: 21.02.12
 * Time: 4:22
 * To change this template use File | Settings | File Templates.
 */
public class ForeachVisualization {

  private volatile ForeachLoop.ForeachState myFutureState;
  private volatile SourcePosition myFirstForeachBodyStatementPosition;
  private DebuggerSession mySession;

  public ForeachVisualization(final DebugProcess process) {
    process.addDebugProcessListener(new DebugProcessAdapterImpl() {
      @Override
      public void paused(SuspendContextImpl suspendContext) {
        ForeachVisualization.this.paused(suspendContext);
      }
    });
  }

  public void skipTo(ForeachLoop foreach, ArrayElementDescriptorImpl descriptor, final DebuggerContextImpl debuggerContext) {
    mySession = debuggerContext.getDebuggerSession();
    myFutureState = foreach.getFutureState(descriptor);
    myFirstForeachBodyStatementPosition = foreach.getFirstBodyStatementPosition();
    doRunToPosition(mySession, myFirstForeachBodyStatementPosition);
  }

  private void paused(final SuspendContextImpl suspendContext) {
    if (myFutureState == null || !isRunToCursorEvent(suspendContext)) {
      return;
    }
    final SourcePosition sourcePosition = ApplicationManager.getApplication().runReadAction(new Computable<SourcePosition>() {
      @Override
      public SourcePosition compute() {
        return ContextUtil.getSourcePosition(suspendContext);
      }
    });
    if (sourcePosition != null &&
        Comparing.equal(sourcePosition.getFile(), myFirstForeachBodyStatementPosition.getFile()) &&
        sourcePosition.getLine() == myFirstForeachBodyStatementPosition.getLine()) {
      resumeSessionIfNeeded();
    }
    else {
      myFutureState = null;
      myFirstForeachBodyStatementPosition = null;
    }
  }

  private static boolean isRunToCursorEvent(SuspendContextImpl context) {
    final EventSet set = context.getEventSet();
    final EventIterator iterator = set.eventIterator();
    if (iterator.hasNext()) {
      final Event event = iterator.next();
      if (iterator.hasNext()) {
        return false;
      }
      final EventRequest request = event.request();
      if (request != null) {
        final Requestor requestor = context.getDebugProcess().getRequestsManager().findRequestor(request);
        if (requestor instanceof RunToCursorBreakpoint) {
          return true;
        }
      }
    }
    return false;
  }

  private void resumeSessionIfNeeded() {
    mySession.getContextManager().addListener(new DebuggerContextListener() {
      @Override
      public void changeEvent(final DebuggerContextImpl newContext, int event) {
        if (event == DebuggerSession.EVENT_PAUSE) {
          newContext.getDebugProcess().getManagerThread().schedule(new DebuggerCommandImpl() {
            @Override
            protected void action() throws Exception {
              final EvaluationContextImpl evaluationContext = newContext.createEvaluationContext();
              final ForeachLoop foreachStatement = findForeachStatement(evaluationContext);
              if (foreachStatement != null) {
                if (!foreachStatement.checkState(myFutureState, evaluationContext)) {
                  doRunToPosition(mySession, myFirstForeachBodyStatementPosition);
                }
              }
            }
          });
          mySession.getContextManager().removeListener(this);
        }
      }
    });
  }

  private static void doRunToPosition(final DebuggerSession session, final SourcePosition position) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        final Document document = PsiDocumentManager.getInstance(position.getFile().getProject()).getDocument(position.getFile());
        session.runToCursor(document, position.getLine(), false);
      }
    });
  }

  @SuppressWarnings("MethodMayBeStatic")
  @Nullable
  public ForeachLoop findForeachStatement(final EvaluationContext evaluationContext) {
    return ApplicationManager.getApplication().runReadAction(new Computable<ForeachLoop>() {
      @Override
      public ForeachLoop compute() {
        final SourcePosition position = ContextUtil.getSourcePosition(evaluationContext);
        if (position == null) {
          return null;
        }

        final Project project = evaluationContext.getDebugProcess().getProject();
        PsiForeachStatement foreach = PositionUtil.getPsiElementAt(project, PsiForeachStatement.class, position, true);
        if (foreach == null) {
          return null;
        }

        final Document document = PsiDocumentManager.getInstance(project).getDocument(position.getFile());
        final int spOffset = position.getOffset();
        final int offset = CharArrayUtil.shiftForward(document.getCharsSequence(), spOffset, " \t");

        while (foreach != null && offset == foreach.getTextOffset()) {
          foreach = PsiTreeUtil.getParentOfType(foreach, PsiForeachStatement.class);
        }

        if (foreach != null && foreach.getIteratedValue() != null) {
          return new ForeachLoop(foreach, evaluationContext);
        }
        return null;
      }
    });
  }
}
