// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.dfaassist;

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerContextListener;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.jdi.StackFrameProxyEx;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.settings.ViewsGeneralSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtScheduler;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerManagerListener;
import com.intellij.xdebugger.impl.dfaassist.DfaAssistBase;
import com.intellij.xdebugger.impl.dfaassist.DfaResult;
import com.sun.jdi.*;
import kotlinx.coroutines.Job;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.CancellablePromise;

import java.util.Objects;
import java.util.concurrent.Callable;

import static com.intellij.xdebugger.impl.dfaassist.DfaAssistBase.AssistMode.*;

public final class DfaAssist extends DfaAssistBase implements DebuggerContextListener, XDebuggerManagerListener {
  private static final int CLEANUP_DELAY_MILLIS = 300;
  private volatile CancellablePromise<?> myComputation;
  private volatile Job scheduledCleanup;
  private volatile boolean myInactive = false;
  private final DebuggerStateManager myManager;

  private DfaAssist(@NotNull Project project, @NotNull DebuggerStateManager manager) {
    super(project);
    project.getMessageBus().connect(this).subscribe(XDebuggerManager.TOPIC, this);
    myManager = manager;
    updateFromSettings();
  }

  private void updateFromSettings() {
    AssistMode newMode = fromSettings();
    if (myMode != newMode) {
      myMode = newMode;
      update();
    }
  }

  private void update() {
    if (myMode == NONE || myInactive) {
      cleanUp();
    }
    else {
      DebuggerSession session = myManager.getContext().getDebuggerSession();
      if (session != null) {
        DebuggerInvocationUtil.invokeLater(myProject, () -> {
          session.refresh(false);
        });
      }
    }
  }

  @Override
  public void currentSessionChanged(@Nullable XDebugSession previousSession, @Nullable XDebugSession currentSession) {
    DebuggerSession session = myManager.getContext().getDebuggerSession();
    if (session != null) {
      XDebugSession xDebugSession = session.getXDebugSession();
      if (xDebugSession == previousSession && !myInactive) {
        myInactive = true;
        update();
      }
      else if (xDebugSession == currentSession && myInactive) {
        myInactive = false;
        update();
      }
    }
  }

  @Override
  public void changeEvent(@NotNull DebuggerContextImpl newContext, DebuggerSession.Event event) {
    if (event == DebuggerSession.Event.DISPOSE) {
      Disposer.dispose(this);
      return;
    }
    if (myMode == NONE) return;
    if (event == DebuggerSession.Event.DETACHED) {
      cleanUp();
      return;
    }
    if (event == DebuggerSession.Event.RESUME) {
      cancelComputation();
      scheduledCleanup = EdtScheduler.getInstance().schedule(CLEANUP_DELAY_MILLIS, this::cleanUp);
    }
    if (event != DebuggerSession.Event.PAUSE && event != DebuggerSession.Event.REFRESH) {
      return;
    }
    SourcePosition sourcePosition = newContext.getSourcePosition();
    if (sourcePosition == null) {
      cleanUp();
      return;
    }
    DfaAssistProvider provider = DfaAssistProvider.EP_NAME.forLanguage(sourcePosition.getFile().getLanguage());
    DebugProcessImpl debugProcess = newContext.getDebugProcess();
    PsiElement element = sourcePosition.getElementAt();
    if (debugProcess == null || provider == null || element == null) {
      cleanUp();
      return;
    }
    SmartPsiElementPointer<PsiElement> pointer = SmartPointerManager.createPointer(element);
    Objects.requireNonNull(newContext.getManagerThread()).schedule(new SuspendContextCommandImpl(newContext.getSuspendContext()) {
      @Override
      public void contextAction(@NotNull SuspendContextImpl suspendContext) {
        StackFrameProxyImpl proxy = suspendContext.getFrameProxy();
        if (proxy == null) {
          cleanUp();
          return;
        }
        DebuggerDfaRunner.Pupa runnerPupa = makePupa(proxy, pointer);
        if (runnerPupa == null) {
          cleanUp();
          return;
        }
        myComputation = ReadAction.nonBlocking(() -> {
            DebuggerDfaRunner runner = runnerPupa.transform();
            return runner == null ? DfaResult.EMPTY : runner.computeHints();
          })
          .withDocumentsCommitted(myProject)
          .coalesceBy(DfaAssist.this)
          .finishOnUiThread(ModalityState.nonModal(), hints -> DfaAssist.this.displayInlays(hints))
          .submit(AppExecutorUtil.getAppExecutorService());
      }
    });
  }

  @Override
  public void dispose() {
    myManager.removeListener(this);
    cleanUp();
  }

  private void cancelComputation() {
    CancellablePromise<?> promise = myComputation;
    if (promise != null) {
      promise.cancel();
    }
    Job cleanup = scheduledCleanup;
    if (cleanup != null) {
      cleanup.cancel(null);
    }
  }

  @Override
  protected void cleanUp() {
    cancelComputation();
    super.cleanUp();
  }

  public static @Nullable DebuggerDfaRunner createDfaRunner(@NotNull StackFrameProxyEx proxy,
                                                            @NotNull SmartPsiElementPointer<PsiElement> pointer) {
    DebuggerDfaRunner.Pupa pupa = makePupa(proxy, pointer);
    if (pupa == null) return null;
    return ReadAction.nonBlocking(pupa::transform).withDocumentsCommitted(pointer.getProject()).executeSynchronously();
  }

  @Nullable
  private static DebuggerDfaRunner.Pupa makePupa(@NotNull StackFrameProxyEx proxy, @NotNull SmartPsiElementPointer<PsiElement> pointer) {
    Callable<DebuggerDfaRunner.Larva> action = () -> {
      try {
        return DebuggerDfaRunner.Larva.hatch(proxy, pointer.getElement());
      }
      catch (VMDisconnectedException | VMOutOfMemoryException | InternalException |
             EvaluateException | InconsistentDebugInfoException | InvalidStackFrameException ignore) {
        return null;
      }
    };
    Project project = pointer.getProject();
    DebuggerDfaRunner.Larva larva = ReadAction.nonBlocking(action).withDocumentsCommitted(project).executeSynchronously();
    if (larva == null) return null;
    DebuggerDfaRunner.Pupa pupa;
    try {
      pupa = larva.pupate();
    }
    catch (VMDisconnectedException | VMOutOfMemoryException | InternalException |
           EvaluateException | InconsistentDebugInfoException | InvalidStackFrameException ignore) {
      return null;
    }
    return pupa;
  }

  /**
   * Install dataflow assistant to the specified debugging session
   *
   * @param javaSession JVM debugger session to install an assistant to
   * @param session     X debugger session
   */
  public static void installDfaAssist(@NotNull DebuggerSession javaSession,
                                      @NotNull XDebugSession session,
                                      @NotNull Disposable parent) {
    DebuggerStateManager manager = javaSession.getContextManager();
    DebuggerContextImpl context = manager.getContext();
    Project project = context.getProject();
    if (project != null) {
      DfaAssist assist = new DfaAssist(project, manager);
      Disposer.register(parent, assist);
      manager.addListener(assist);
      session.addSessionListener(new XDebugSessionListener() {
        @Override
        public void settingsChanged() {
          assist.updateFromSettings();
        }
      }, assist);
    }
  }

  private static AssistMode fromSettings() {
    ViewsGeneralSettings settings = ViewsGeneralSettings.getInstance();
    if (settings.USE_DFA_ASSIST && settings.USE_DFA_ASSIST_GRAY_OUT) {
      return BOTH;
    }
    if (settings.USE_DFA_ASSIST) {
      return INLAYS;
    }
    if (settings.USE_DFA_ASSIST_GRAY_OUT) {
      return GRAY_OUT;
    }
    return NONE;
  }
}
