// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.dfaassist;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
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
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
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

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class DfaAssist implements DebuggerContextListener, Disposable {
  private static final int CLEANUP_DELAY_MILLIS = 300;
  private final @NotNull Project myProject;
  // modified from EDT only
  private DfaAssistMarkup myMarkup = new DfaAssistMarkup(null, Collections.emptyList(), Collections.emptyList());
  private volatile CancellablePromise<?> myComputation;
  private volatile ScheduledFuture<?> myScheduledCleanup;
  private final DebuggerStateManager myManager;
  private volatile AssistMode myMode;

  private DfaAssist(@NotNull Project project, @NotNull DebuggerStateManager manager) {
    myProject = project;
    myManager = manager;
    updateFromSettings();
  }

  private void updateFromSettings() {
    AssistMode newMode = AssistMode.fromSettings();
    if (myMode != newMode) {
      myMode = newMode;
      if (newMode == AssistMode.NONE) {
        cleanUp();
      } else {
        DebuggerSession session = myManager.getContext().getDebuggerSession();
        if (session != null) {
          session.refresh(false);
        }
      }
    }
  }

  private static final class DfaAssistMarkup implements Disposable {
    private final @NotNull List<Inlay<?>> myInlays;
    private final @NotNull List<RangeHighlighter> myRanges;

    private DfaAssistMarkup(@Nullable Editor editor, @NotNull List<Inlay<?>> inlays, @NotNull List<RangeHighlighter> ranges) {
      myInlays = inlays;
      myRanges = ranges;
      if (editor != null) {
        editor.getDocument().addDocumentListener(new DocumentListener() {
          @Override
          public void beforeDocumentChange(@NotNull DocumentEvent event) {
            ApplicationManager.getApplication().invokeLater(() -> Disposer.dispose(DfaAssistMarkup.this));
          }
        }, this);
      }
    }

    @Override
    public void dispose() {
      ApplicationManager.getApplication().assertIsDispatchThread();
      myInlays.forEach(Disposer::dispose);
      myInlays.clear();
      myRanges.forEach(RangeHighlighter::dispose);
      myRanges.clear();
    }
  }

  @Override
  public void changeEvent(@NotNull DebuggerContextImpl newContext, DebuggerSession.Event event) {
    if (event == DebuggerSession.Event.DISPOSE) {
      Disposer.dispose(this);
      return;
    }
    if (myMode == AssistMode.NONE) return;
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
        DebuggerDfaRunner.Pupa runnerPupa = makePupa(proxy, pointer);
        if (runnerPupa == null) {
          cleanUp();
          return;
        }
        myComputation = ReadAction.nonBlocking(() -> {
            DebuggerDfaRunner runner = runnerPupa.transform();
            return runner == null ? DebuggerDfaRunner.DfaResult.EMPTY : runner.computeHints();
          })
          .withDocumentsCommitted(myProject)
          .coalesceBy(DfaAssist.this)
          .finishOnUiThread(ModalityState.NON_MODAL, hints -> DfaAssist.this.displayInlays(hints))
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
    ScheduledFuture<?> cleanup = myScheduledCleanup;
    if (cleanup != null) {
      cleanup.cancel(false);
    }
  }

  private void cleanUp() {
    cancelComputation();
    UIUtil.invokeLaterIfNeeded(() -> {
      Disposer.dispose(myMarkup);
    });
  }

  private void displayInlays(DebuggerDfaRunner.DfaResult result) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    cleanUp();
    Map<PsiElement, DfaHint> hints = result.hints;
    Collection<TextRange> unreachable = result.unreachable;
    if (result.file == null) return;
    EditorImpl editor = ObjectUtils.tryCast(FileEditorManager.getInstance(myProject).getSelectedTextEditor(), EditorImpl.class);
    if (editor == null) return;
    VirtualFile expectedFile = result.file.getVirtualFile();
    if (expectedFile == null || !expectedFile.equals(editor.getVirtualFile())) return;
    List<Inlay<?>> newInlays = new ArrayList<>();
    List<RangeHighlighter> ranges = new ArrayList<>();
    AssistMode mode = myMode;
    if (!hints.isEmpty() && mode.displayInlays()) {
      InlayModel model = editor.getInlayModel();
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
    }
    if (!unreachable.isEmpty() && mode.displayGrayOut()) {
      MarkupModelEx model = editor.getMarkupModel();
      for (TextRange range : unreachable) {
        RangeHighlighter highlighter = model.addRangeHighlighter(HighlightInfoType.UNUSED_SYMBOL.getAttributesKey(),
                                                                 range.getStartOffset(), range.getEndOffset(), HighlighterLayer.ERROR + 1,
                                                                 HighlighterTargetArea.EXACT_RANGE);
        ranges.add(highlighter);
      }
    }
    if (!newInlays.isEmpty() || !ranges.isEmpty()) {
      myMarkup = new DfaAssistMarkup(editor, newInlays, ranges);
    }
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
          assist.updateFromSettings();
        }
      }, assist);
    }
  }

  private enum AssistMode {
    NONE, INLAYS, GRAY_OUT, BOTH;

    boolean displayInlays() {
      return this == INLAYS || this == BOTH;
    }

    boolean displayGrayOut() {
      return this == GRAY_OUT || this == BOTH;
    }

    static AssistMode fromSettings() {
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
}
