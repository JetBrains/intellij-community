// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.dfaassist;

import com.intellij.codeInsight.hints.presentation.MenuOnClickPresentation;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.codeInsight.hints.presentation.PresentationRenderer;
import com.intellij.codeInspection.dataFlow.RunnerResult;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerContextListener;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayModel;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Method;
import com.sun.jdi.StackFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class DfaAssist implements DebuggerContextListener {
  private final Project myProject;
  private final Queue<Inlay<?>> myInlays = new ConcurrentLinkedQueue<>();

  private DfaAssist(Project project) {
    myProject = project;
  }

  @Override
  public void changeEvent(@NotNull DebuggerContextImpl newContext, DebuggerSession.Event event) {
    if (event == DebuggerSession.Event.DETACHED || event == DebuggerSession.Event.DISPOSE) {
      disposeInlays();
    }
    if (event != DebuggerSession.Event.PAUSE) return;
    SourcePosition sourcePosition = newContext.getSourcePosition();
    if (sourcePosition == null) {
      disposeInlays();
      return;
    }
    PsiJavaFile file = ObjectUtils.tryCast(sourcePosition.getFile(), PsiJavaFile.class);
    DebugProcessImpl debugProcess = JavaDebugProcess.getCurrentDebugProcess(myProject);
    if (debugProcess == null || file == null) {
      disposeInlays();
      return;
    }
    PsiElement element = sourcePosition.getElementAt();
    debugProcess.getManagerThread().schedule(new SuspendContextCommandImpl(newContext.getSuspendContext()) {
      @Override
      public void contextAction(@NotNull SuspendContextImpl suspendContext) throws Exception {
        StackFrameProxyImpl proxy = suspendContext.getFrameProxy();
        if (proxy == null) {
          disposeInlays();
          return;
        }
        StackFrame frame = proxy.getStackFrame();
        Map<PsiExpression, DfaHint> hints = ReadAction.compute(() -> computeHints(frame, element));
        displayInlays(hints, sourcePosition);
      }
    });
  }

  private void disposeInlays() {
    ApplicationManager.getApplication().invokeLater(this::doDisposeInlays);
  }

  private void doDisposeInlays() {
    while (true) {
      Inlay<?> inlay = myInlays.poll();
      if (inlay == null) break;
      Disposer.dispose(inlay);
    }
  }

  private void displayInlays(Map<PsiExpression, DfaHint> hints, SourcePosition sourcePosition) {
    if (hints.isEmpty()) {
      disposeInlays();
      return;
    }
    ApplicationManager.getApplication().invokeLater(
      () -> {
        doDisposeInlays();
        EditorImpl editor = ObjectUtils.tryCast(sourcePosition.openEditor(true), EditorImpl.class);
        if (editor == null) return;
        InlayModel model = editor.getInlayModel();
        List<Inlay<?>> newInlays = new ArrayList<>();
        AnAction turnOffDfaProcessor = new TurnOffDfaProcessorAction();
        hints.forEach((expression, hint) -> {
          TextRange range = expression.getTextRange();
          PresentationFactory factory = new PresentationFactory(editor);
          MenuOnClickPresentation presentation = new MenuOnClickPresentation(
            factory.roundWithBackground(factory.smallText(hint.getTitle())), myProject,
            () -> Collections.singletonList(turnOffDfaProcessor));
          newInlays.add(model.addInlineElement(range.getEndOffset(), new PresentationRenderer(presentation)));
        });
        myInlays.addAll(newInlays);
      }
    );
  }

  @NotNull
  static Map<PsiExpression, DfaHint> computeHints(StackFrame frame, PsiElement element) throws AbsentInformationException {
    Method method = frame.location().method();
    if (!element.isValid()) return Collections.emptyMap();

    PsiMethod psiMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    if (psiMethod == null || !psiMethod.getName().equals(method.name()) ||
        psiMethod.getParameterList().getParametersCount() != method.arguments().size()) {
      return Collections.emptyMap();
    }
    PsiStatement statement = getAnchorStatement(element);
    if (statement == null) return Collections.emptyMap();
    // TODO: support class initializers
    // TODO: check/improve lambdas support
    PsiCodeBlock body = getCodeBlock(statement);
    if (body == null) return Collections.emptyMap();
    DebuggerInstructionVisitor visitor = new DebuggerInstructionVisitor();
    // TODO: read assertion status
    RunnerResult result = new DebuggerDfaRunner(body, statement, frame).analyzeMethod(body, visitor);
    if (result != RunnerResult.OK) return Collections.emptyMap();
    visitor.cleanup();
    return visitor.getHints();
  }

  @Nullable
  private static PsiStatement getAnchorStatement(@NotNull PsiElement element) {
    PsiStatement statement = PsiTreeUtil.getParentOfType(element, PsiStatement.class, false, PsiMethod.class);
    if (statement instanceof PsiBlockStatement && ((PsiBlockStatement)statement).getCodeBlock().getRBrace() == element) {
      statement = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
    }
    return statement;
  }

  @Nullable
  private static PsiCodeBlock getCodeBlock(@NotNull PsiStatement statement) {
    PsiElement e = statement;
    while (e != null && !(e instanceof PsiClass) && !(e instanceof PsiFileSystemItem)) {
      e = e.getParent();
      if (e instanceof PsiCodeBlock) {
        if (e.getParent() instanceof PsiMethod ||
            e.getParent() instanceof PsiBlockStatement && e.getParent().getParent() instanceof PsiLoopStatement) {
          return (PsiCodeBlock)e;
        }
      }
    }
    return null;
  }

  private class TurnOffDfaProcessorAction extends AnAction {
    private TurnOffDfaProcessorAction() {
      super("Turn Off Dataflow Assist", "Switch off dataflow aided debugging for this session", AllIcons.Actions.Cancel);
    }
    @Override
    public void actionPerformed(@NotNull AnActionEvent evt) {
      DebugProcessImpl process = JavaDebugProcess.getCurrentDebugProcess(myProject);
      if (process != null) {
        process.getSession().getContextManager().removeListener(DfaAssist.this);
        disposeInlays();
      }
    }
  }
  
  /**
   * Install dataflow assistant to the specified debugging session 
   * @param javaSession JVM debugger session to install an assistant to
   */
  public static void installDfaAssist(@NotNull DebuggerSession javaSession) {
    DebuggerStateManager manager = javaSession.getContextManager();
    manager.addListener(new DfaAssist(manager.getContext().getProject()));
  }
}
