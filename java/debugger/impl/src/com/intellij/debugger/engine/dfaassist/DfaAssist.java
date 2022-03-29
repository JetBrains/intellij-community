// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.dfaassist;

import com.intellij.codeInsight.hints.presentation.MenuOnClickPresentation;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.codeInsight.hints.presentation.PresentationRenderer;
import com.intellij.debugger.JavaDebuggerBundle;
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
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayModel;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtScheduledExecutorService;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.CancellablePromise;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class DfaAssist implements DebuggerContextListener, Disposable {
  private static final int CLEANUP_DELAY_MILLIS = 300;
  private final @NotNull Project myProject;
  private InlaySet myInlays = new InlaySet(null, Collections.emptyList()); // modified from EDT only
  private volatile CancellablePromise<?> myComputation;
  private volatile ScheduledFuture<?> myScheduledCleanup;
  private final DebuggerStateManager myManager;
  private volatile boolean myActive;

  private DfaAssist(@NotNull Project project, @NotNull DebuggerStateManager manager) {
    myProject = project;
    myManager = manager;
    setActive(ViewsGeneralSettings.getInstance().USE_DFA_ASSIST);
  }

  private static final class InlaySet implements Disposable {
    private final @NotNull List<Inlay<?>> myInlays;

    private InlaySet(@Nullable Editor editor, @NotNull List<Inlay<?>> inlays) {
      myInlays = inlays;
      if (editor != null) {
        editor.getDocument().addDocumentListener(new DocumentListener() {
          @Override
          public void beforeDocumentChange(@NotNull DocumentEvent event) {
            ApplicationManager.getApplication().invokeLater(() -> Disposer.dispose(InlaySet.this));
          }
        }, this);
      }
    }

    @Override
    public void dispose() {
      ApplicationManager.getApplication().assertIsDispatchThread();
      myInlays.forEach(Disposer::dispose);
      myInlays.clear();
    }
  }

  @Override
  public void changeEvent(@NotNull DebuggerContextImpl newContext, DebuggerSession.Event event) {
    if (event == DebuggerSession.Event.DISPOSE) {
      Disposer.dispose(this);
      return;
    }
    if (!myActive) return;
    if (event == DebuggerSession.Event.DETACHED) {
      cleanUp();
      return;
    }
    if (event == DebuggerSession.Event.RESUME) {
      cancelComputation();
      myScheduledCleanup = EdtScheduledExecutorService.getInstance().schedule(this::cleanUp, CLEANUP_DELAY_MILLIS, TimeUnit.MILLISECONDS);
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
    debugProcess.getManagerThread().schedule(new SuspendContextCommandImpl(newContext.getSuspendContext()) {
      @Override
      public void contextAction(@NotNull SuspendContextImpl suspendContext) {
        StackFrameProxyImpl proxy = suspendContext.getFrameProxy();
        if (proxy == null) {
          cleanUp();
          return;
        }
        DebuggerDfaRunner runner = createRunner(proxy);
        if (runner == null) {
          cleanUp();
          return;
        }
        myComputation = ReadAction.nonBlocking(() -> computeHints(runner)).withDocumentsCommitted(myProject)
          .coalesceBy(DfaAssist.this)
          .finishOnUiThread(ModalityState.NON_MODAL, hints -> DfaAssist.this.displayInlays(hints))
          .submit(AppExecutorUtil.getAppExecutorService());
      }

      private @Nullable DebuggerDfaRunner createRunner(StackFrameProxyImpl proxy) {
        Callable<DebuggerDfaRunner> action = () -> {
          try {
            return createDfaRunner(proxy, pointer.getElement());
          }
          catch (VMDisconnectedException | VMOutOfMemoryException | InternalException |
            EvaluateException | InconsistentDebugInfoException | InvalidStackFrameException ignore) {
            return null;
          }
        };
        return ReadAction.nonBlocking(action).withDocumentsCommitted(myProject).executeSynchronously();
      }
    });
  }

  private void setActive(boolean active) {
    if (myActive != active) {
      myActive = active;
      if (!myActive) {
        cleanUp();
      } else {
        DebuggerSession session = myManager.getContext().getDebuggerSession();
        if (session != null) {
          session.refresh(false);
        }
      }
    }
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
    ScheduledFuture<?> cleanup = myScheduledCleanup;
    if (cleanup != null) {
      cleanup.cancel(false);
    }
  }

  private void cleanUp() {
    cancelComputation();
    UIUtil.invokeLaterIfNeeded(() -> Disposer.dispose(myInlays));
  }

  private void displayInlays(Map<PsiElement, DfaHint> hints) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    cleanUp();
    if (hints.isEmpty()) return;
    EditorImpl editor = ObjectUtils.tryCast(FileEditorManager.getInstance(myProject).getSelectedTextEditor(), EditorImpl.class);
    if (editor == null) return;
    PsiFile psiFile = hints.keySet().iterator().next().getContainingFile();
    if (psiFile == null) return;
    VirtualFile expectedFile = psiFile.getVirtualFile();
    if (expectedFile == null || !expectedFile.equals(editor.getVirtualFile())) return;
    InlayModel model = editor.getInlayModel();
    List<Inlay<?>> newInlays = new ArrayList<>();
    AnAction turnOffDfaProcessor = new TurnOffDfaProcessorAction();
    hints.forEach((expr, hint) -> {
      Segment range = expr.getTextRange();
      if (range == null) return;
      PresentationFactory factory = new PresentationFactory(editor);
      MenuOnClickPresentation presentation = new MenuOnClickPresentation(
        factory.roundWithBackground(factory.smallText(hint.getTitle())), myProject,
        () -> Collections.singletonList(turnOffDfaProcessor));
      newInlays.add(model.addInlineElement(range.getEndOffset(), new PresentationRenderer(presentation)));
    });
    if (!newInlays.isEmpty()) {
      myInlays = new InlaySet(editor, newInlays);
    }
  }

  private static @NotNull Map<PsiElement, DfaHint> computeHints(@NotNull DebuggerDfaRunner runner) {
    DebuggerDfaListener interceptor = runner.interpret();
    if (interceptor == null) return Collections.emptyMap();
    return interceptor.computeHints();
  }

  public static @Nullable DebuggerDfaRunner createDfaRunner(@NotNull StackFrameProxyEx proxy, @Nullable PsiElement element)
    throws EvaluateException {
    if (element == null || !element.isValid() || DumbService.isDumb(element.getProject())) return null;

    DfaAssistProvider provider = DfaAssistProvider.EP_NAME.forLanguage(element.getLanguage());
    if (provider == null) return null;
    try {
      if (!provider.locationMatches(element, proxy.location())) return null;
    }
    catch (IllegalArgumentException iea) {
      throw new EvaluateException(iea.getMessage(), iea);
    }
    PsiElement anchor = provider.getAnchor(element);
    if (anchor == null) return null;
    PsiElement body = provider.getCodeBlock(anchor);
    if (body == null) return null;
    DebuggerDfaRunner runner = new DebuggerDfaRunner(provider, body, anchor, proxy);
    return runner.isValid() ? runner : null;
  }

  private final class TurnOffDfaProcessorAction extends AnAction {
    private TurnOffDfaProcessorAction() {
      super(JavaDebuggerBundle.message("action.TurnOffDfaAssist.text"),
            JavaDebuggerBundle.message("action.TurnOffDfaAssist.description"), AllIcons.Actions.Cancel);
    }
    @Override
    public void actionPerformed(@NotNull AnActionEvent evt) {
      Disposer.dispose(DfaAssist.this);
    }
  }

  /**
   * Install dataflow assistant to the specified debugging session
   * @param javaSession JVM debugger session to install an assistant to
   * @param session X debugger session
   */
  public static void installDfaAssist(@NotNull DebuggerSession javaSession,
                                      @NotNull XDebugSession session) {
    DebuggerStateManager manager = javaSession.getContextManager();
    DebuggerContextImpl context = manager.getContext();
    Project project = context.getProject();
    if (project != null) {
      DfaAssist assist = new DfaAssist(project, manager);
      manager.addListener(assist);
      session.addSessionListener(new XDebugSessionListener() {
        @Override
        public void settingsChanged() {
          assist.setActive(ViewsGeneralSettings.getInstance().USE_DFA_ASSIST);
        }
      }, assist);
    }
  }
}
