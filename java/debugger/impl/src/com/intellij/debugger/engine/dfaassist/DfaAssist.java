// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.dfaassist;

import com.intellij.codeInsight.hints.presentation.MenuOnClickPresentation;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.codeInsight.hints.presentation.PresentationRenderer;
import com.intellij.codeInspection.dataFlow.RunnerResult;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.*;
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
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.CancellablePromise;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DfaAssist implements DebuggerContextListener {
  private final @NotNull Project myProject;
  private InlaySet myInlays = new InlaySet(null, Collections.emptyList()); // modified from EDT only
  private volatile CancellablePromise<?> myPromise;

  private DfaAssist(@NotNull Project project) {
    myProject = project;
  }

  private static class InlaySet implements Disposable {
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
    if (!ViewsGeneralSettings.getInstance().USE_DFA_ASSIST) {
      shutDown(newContext);
      return;
    }
    if (event == DebuggerSession.Event.DETACHED || event == DebuggerSession.Event.DISPOSE) {
      cleanUp();
    }
    if (event != DebuggerSession.Event.PAUSE && event != DebuggerSession.Event.REFRESH) return;
    SourcePosition sourcePosition = newContext.getSourcePosition();
    if (sourcePosition == null) {
      cleanUp();
      return;
    }
    PsiJavaFile file = ObjectUtils.tryCast(sourcePosition.getFile(), PsiJavaFile.class);
    DebugProcessImpl debugProcess = newContext.getDebugProcess();
    PsiElement element = sourcePosition.getElementAt();
    if (debugProcess == null || file == null || element == null) {
      cleanUp();
      return;
    }
    SmartPsiElementPointer<PsiElement> pointer = SmartPointerManager.createPointer(element);
    debugProcess.getManagerThread().schedule(new SuspendContextCommandImpl(newContext.getSuspendContext()) {
      @Override
      public void contextAction(@NotNull SuspendContextImpl suspendContext) throws Exception {
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
        myPromise = ReadAction.nonBlocking(() -> computeHints(runner)).withDocumentsCommitted(myProject)
          .coalesceBy(DfaAssist.this)
          .finishOnUiThread(ModalityState.NON_MODAL, hints -> DfaAssist.this.displayInlays(hints, newContext))
          .submit(AppExecutorUtil.getAppExecutorService());
      }

      @Nullable
      private DebuggerDfaRunner createRunner(StackFrameProxyImpl proxy) throws EvaluateException {
        try {
          StackFrame frame = proxy.getStackFrame();
          return ReadAction.nonBlocking(() -> createDfaRunner(frame, pointer.getElement()))
              .withDocumentsCommitted(myProject).executeSynchronously();
        }
        catch (VMDisconnectedException | VMOutOfMemoryException | InternalException ignore) {
        }
        return null;
      }
    });
  }

  private void cleanUp() {
    CancellablePromise<?> promise = myPromise;
    if (promise != null) {
      promise.cancel();
    }
    ApplicationManager.getApplication().invokeLater(() -> Disposer.dispose(myInlays));
  }

  private void displayInlays(Map<PsiExpression, DfaHint> hints, DebuggerContextImpl context) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    Disposer.dispose(myInlays);
    if (hints.isEmpty()) return;
    EditorImpl editor = ObjectUtils.tryCast(FileEditorManager.getInstance(myProject).getSelectedTextEditor(), EditorImpl.class);
    if (editor == null) return;
    PsiFile psiFile = hints.keySet().iterator().next().getContainingFile();
    if (psiFile == null) return;
    VirtualFile expectedFile = psiFile.getVirtualFile();
    if (expectedFile == null || !expectedFile.equals(editor.getVirtualFile())) return;
    InlayModel model = editor.getInlayModel();
    List<Inlay<?>> newInlays = new ArrayList<>();
    AnAction turnOffDfaProcessor = new TurnOffDfaProcessorAction(context);
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

  @NotNull
  private static Map<PsiExpression, DfaHint> computeHints(@NotNull DebuggerDfaRunner runner) {
    DebuggerInstructionVisitor visitor = new DebuggerInstructionVisitor();
    RunnerResult result = runner.interpret(visitor);
    if (result != RunnerResult.OK) return Collections.emptyMap();
    visitor.cleanup();
    return visitor.getHints();
  }

  @Nullable
  static DebuggerDfaRunner createDfaRunner(@NotNull StackFrame frame, @Nullable PsiElement element) {
    if (element == null || !element.isValid() || DumbService.isDumb(element.getProject())) return null;

    if (!locationMatches(element, frame.location())) return null;
    PsiStatement statement = getAnchorStatement(element);
    if (statement == null) return null;
    PsiElement body = getCodeBlock(statement);
    if (body == null) return null;
    DebuggerDfaRunner runner = new DebuggerDfaRunner(body, statement, frame);
    return runner.isValid() ? runner : null;
  }

  /**
   * Quick check whether code location matches the source code in the editor
   * @param element PsiElement in the editor
   * @param location location reported by debugger
   * @return true if debugger location likely matches to the editor location
   */
  private static boolean locationMatches(@NotNull PsiElement element, Location location) {
    Method method = location.method();
    PsiElement context = DebuggerUtilsEx.getContainingMethod(element);
    try {
      if (context instanceof PsiMethod) {
        PsiMethod psiMethod = (PsiMethod)context;
        String name = psiMethod.isConstructor() ? "<init>" : psiMethod.getName();
        return name.equals(method.name()) && psiMethod.getParameterList().getParametersCount() == method.arguments().size();
      }
      if (context instanceof PsiLambdaExpression) {
        return DebuggerUtilsEx.isLambda(method) && 
               method.arguments().size() >= ((PsiLambdaExpression)context).getParameterList().getParametersCount();
      }
      if (context instanceof PsiClassInitializer) {
        String expectedMethod = ((PsiClassInitializer)context).hasModifierProperty(PsiModifier.STATIC) ? "<clinit>" : "<init>";
        return method.name().equals(expectedMethod);
      }
    }
    catch (AbsentInformationException ignored) {
    }
    return false;
  }

  @Nullable
  private static PsiStatement getAnchorStatement(@NotNull PsiElement element) {
    while (element instanceof PsiWhiteSpace || element instanceof PsiComment) {
      element = element.getNextSibling();
    }
    PsiStatement statement = PsiTreeUtil.getParentOfType(element, PsiStatement.class, false, PsiLambdaExpression.class, PsiMethod.class);
    if (statement instanceof PsiBlockStatement && ((PsiBlockStatement)statement).getCodeBlock().getRBrace() == element) {
      statement = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
    }
    return statement;
  }

  @Nullable
  private static PsiElement getCodeBlock(@NotNull PsiStatement statement) {
    if (statement instanceof PsiWhileStatement || statement instanceof PsiDoWhileStatement) {
      return statement;
    }
    PsiElement e = statement;
    while (e != null && !(e instanceof PsiClass) && !(e instanceof PsiFileSystemItem)) {
      e = e.getParent();
      if (e instanceof PsiCodeBlock) {
        PsiElement parent = e.getParent();
        if (parent instanceof PsiMethod || parent instanceof PsiLambdaExpression || parent instanceof PsiClassInitializer ||
            // We cannot properly restore context if we started from finally, so let's analyze just finally block
            parent instanceof PsiTryStatement && ((PsiTryStatement)parent).getFinallyBlock() == e ||
            parent instanceof PsiBlockStatement && parent.getParent() instanceof PsiLoopStatement) {
          if (parent.getParent() instanceof PsiDoWhileStatement) {
            return parent.getParent();
          }
          return e;
        }
      }
      if (e instanceof PsiDoWhileStatement) {
        return e;
      }
    }
    return null;
  }

  private class TurnOffDfaProcessorAction extends AnAction {
    private final DebuggerContextImpl myContext;

    private TurnOffDfaProcessorAction(DebuggerContextImpl context) {
      super(DebuggerBundle.message("action.TurnOffDfaAssist.text"),
            DebuggerBundle.message("action.TurnOffDfaAssist.description"), AllIcons.Actions.Cancel);
      myContext = context;
    }
    @Override
    public void actionPerformed(@NotNull AnActionEvent evt) {
      shutDown(myContext);
    }
  }
  
  private void shutDown(DebuggerContextImpl context) {
    DebuggerSession session = context.getDebuggerSession();
    if (session != null) {
      session.getContextManager().removeListener(this);
      cleanUp();
    }
  }

  /**
   * Install dataflow assistant to the specified debugging session 
   * @param javaSession JVM debugger session to install an assistant to
   */
  public static void installDfaAssist(@NotNull DebuggerSession javaSession) {
    DebuggerStateManager manager = javaSession.getContextManager();
    DebuggerContextImpl context = manager.getContext();
    if (context.getProject() != null) {
      manager.addListener(new DfaAssist(context.getProject()));
    }
  }
}
