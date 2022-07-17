// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.engine.evaluation.expression.UnsupportedExpressionException;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.EditorTextProvider;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.debugger.ui.impl.watch.CompilingEvaluatorImpl;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.ExpressionInfo;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.evaluate.quick.XDebuggerPsiEvaluator;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

public class JavaDebuggerEvaluator extends XDebuggerEvaluator implements XDebuggerPsiEvaluator {
  private final DebugProcessImpl myDebugProcess;
  private final JavaStackFrame myStackFrame;

  public JavaDebuggerEvaluator(DebugProcessImpl debugProcess, JavaStackFrame stackFrame) {
    myDebugProcess = debugProcess;
    myStackFrame = stackFrame;
  }

  @Override
  public void evaluate(@NotNull final String expression,
                       @NotNull final XEvaluationCallback callback,
                       @Nullable XSourcePosition expressionPosition) {
    evaluate(XExpressionImpl.fromText(expression), callback, expressionPosition);
  }

  @Override
  public void evaluate(@NotNull final XExpression expression,
                       @NotNull final XEvaluationCallback callback,
                       @Nullable XSourcePosition expressionPosition) {
    myDebugProcess.getManagerThread().schedule(new DebuggerContextCommandImpl(myDebugProcess.getDebuggerContext(),
                                                                              myStackFrame.getStackFrameProxy().threadProxy()) {
      @Override
      public Priority getPriority() {
        return Priority.NORMAL;
      }

      @Override
      public void threadAction(@NotNull SuspendContextImpl suspendContext) {
        try {
          if (DebuggerUIUtil.isObsolete(callback)) {
            return;
          }

          JavaDebugProcess process = myDebugProcess.getXdebugProcess();
          if (process == null) {
            callback.errorOccurred(JavaDebuggerBundle.message("error.no.debug.process"));
            return;
          }
          TextWithImports text = TextWithImportsImpl.fromXExpression(expression);
          NodeManagerImpl nodeManager = process.getNodeManager();
          WatchItemDescriptor descriptor = nodeManager.getWatchItemDescriptor(null, text, null);
          EvaluationContextImpl evalContext = myStackFrame.getFrameDebuggerContext(getDebuggerContext()).createEvaluationContext();
          if (evalContext == null) {
            callback.errorOccurred(JavaDebuggerBundle.message("error.context.not.available"));
            return;
          }
          descriptor.setContext(evalContext);
          EvaluateException exception = descriptor.getEvaluateException();
          if (exception != null && descriptor.getValue() == null) {
            callback.errorOccurred(exception.getMessage());
            return;
          }
          callback.evaluated(JavaValue.create(null, descriptor, evalContext, nodeManager, true));
        }
        catch (Throwable e) {
          callback.errorOccurred(JavaDebuggerBundle.message("error.internal"));
          throw e;
        }
      }
    });
  }

  @Override
  public void evaluate(@NotNull PsiElement element, @NotNull XEvaluationCallback callback) {
    myDebugProcess.getManagerThread().schedule(new DebuggerContextCommandImpl(myDebugProcess.getDebuggerContext(),
                                                                              myStackFrame.getStackFrameProxy().threadProxy()) {
      @Override
      public Priority getPriority() {
        return Priority.NORMAL;
      }

      @Override
      public void threadAction(@NotNull SuspendContextImpl suspendContext) {
        if (DebuggerUIUtil.isObsolete(callback)) {
          return;
        }

        JavaDebugProcess process = myDebugProcess.getXdebugProcess();
        if (process == null) {
          callback.errorOccurred(JavaDebuggerBundle.message("error.no.debug.process"));
          return;
        }

        DebuggerContextImpl debuggerContext = myStackFrame.getFrameDebuggerContext(getDebuggerContext());
        EvaluationContextImpl evalContext = debuggerContext.createEvaluationContext();
        if (evalContext == null) {
          callback.errorOccurred(JavaDebuggerBundle.message("error.context.not.available"));
          return;
        }

        try {
          Project project = myDebugProcess.getProject();
          Ref<TextWithImportsImpl> text = new Ref<>();
          ExpressionEvaluator evaluator = ReadAction.compute(() -> {
            text.set(new TextWithImportsImpl(element));
            CodeFragmentFactory factory = DebuggerUtilsEx.getCodeFragmentFactory(element, null);
            try {
              return factory.getEvaluatorBuilder().build(element, ContextUtil.getSourcePosition(evalContext));
            }
            catch (UnsupportedExpressionException ex) {
              PsiElement context = PositionUtil.getContextElement(debuggerContext);
              ExpressionEvaluator eval = CompilingEvaluatorImpl.create(project, context, e ->
                factory.createCodeFragment(text.get(), context, project));
              if (eval != null) {
                return eval;
              }
              throw ex;
            }
          });
          WatchItemDescriptor descriptor = new WatchItemDescriptor(project, text.get()) {
            @Override
            protected @NotNull ExpressionEvaluator getEvaluator(EvaluationContextImpl evaluationContext) {
              return evaluator;
            }
          };
          descriptor.setContext(evalContext);
          callback.evaluated(JavaValue.create(null, descriptor, evalContext, process.getNodeManager(), true));
        }
        catch (EvaluateException e) {
          callback.errorOccurred(e.getMessage());
        }
      }
    });
  }

  @Override
  public @NotNull Promise<ExpressionInfo> getExpressionInfoAtOffsetAsync(@NotNull Project project,
                                                                         @NotNull Document document,
                                                                         int offset,
                                                                         boolean sideEffectsAllowed) {
    return ReadAction
      .nonBlocking(() -> {
        PsiElement elementAtCursor = DebuggerUtilsEx.findElementAt(PsiDocumentManager.getInstance(project).getPsiFile(document), offset);
        if (elementAtCursor != null && elementAtCursor.isValid()) {
          EditorTextProvider textProvider = EditorTextProvider.EP.forLanguage(elementAtCursor.getLanguage());
          if (textProvider != null) {
            Pair<PsiElement, TextRange> pair = textProvider.findExpression(elementAtCursor, sideEffectsAllowed);
            if (pair != null) {
              PsiElement element = pair.getFirst();
              return new ExpressionInfo(pair.getSecond(), null, null, element instanceof PsiExpression ? element : null);
            }
          }
        }
        return null;
      })
      .inSmartMode(project)
      .withDocumentsCommitted(project)
      .coalesceBy(this)
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  @Override
  public EvaluationMode getEvaluationMode(@NotNull String text, int startOffset, int endOffset, @Nullable PsiFile psiFile) {
    if (psiFile != null) {
      PsiElement[] range = CodeInsightUtil.findStatementsInRange(psiFile, startOffset, endOffset);
      return range.length > 1 ? EvaluationMode.CODE_FRAGMENT : EvaluationMode.EXPRESSION;
    }
    return super.getEvaluationMode(text, startOffset, endOffset, null);
  }
}
