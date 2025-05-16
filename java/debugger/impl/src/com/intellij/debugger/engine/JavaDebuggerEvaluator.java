// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.util.registry.Registry;
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
import com.intellij.xdebugger.impl.evaluate.quick.XDebuggerDocumentOffsetEvaluator;
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationCallbackWithOrigin;
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationOrigin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import java.util.Objects;

public class JavaDebuggerEvaluator extends XDebuggerEvaluator implements XDebuggerDocumentOffsetEvaluator {
  private final DebugProcessImpl myDebugProcess;
  private final JavaStackFrame myStackFrame;

  public JavaDebuggerEvaluator(DebugProcessImpl debugProcess, JavaStackFrame stackFrame) {
    myDebugProcess = debugProcess;
    myStackFrame = stackFrame;
  }

  @Override
  public void evaluate(final @NotNull String expression,
                       final @NotNull XEvaluationCallback callback,
                       @Nullable XSourcePosition expressionPosition) {
    evaluate(XExpressionImpl.fromText(expression), callback, expressionPosition);
  }

  @Override
  public void evaluate(final @NotNull XExpression expression,
                       final @NotNull XEvaluationCallback baseCallback,
                       @Nullable XSourcePosition expressionPosition) {
    evaluate(baseCallback, (DebuggerContextImpl debuggerContext, EvaluationContextImpl evalContext) -> {
      TextWithImports text = TextWithImportsImpl.fromXExpression(expression);
      NodeManagerImpl nodeManager = Objects.requireNonNull(myDebugProcess.getXdebugProcess()).getNodeManager();
      return nodeManager.getWatchItemDescriptor(null, text, null);
    });
  }

  private void evaluatePsiElement(@NotNull PsiElement element, @NotNull XEvaluationCallback baseCallback) {
    evaluate(baseCallback, (DebuggerContextImpl debuggerContext, EvaluationContextImpl evalContext) -> {
      Project project = myDebugProcess.getProject();
      Ref<TextWithImportsImpl> text = new Ref<>();
      ExpressionEvaluator evaluator = ReadAction.compute(() -> {
        text.set(new TextWithImportsImpl(element));
        CodeFragmentFactory factory = DebuggerUtilsEx.getCodeFragmentFactory(element, null);
        try {
          if (Registry.is("debugger.compiling.evaluator.force")) throw new UnsupportedExpressionException("force compilation");
          return factory.getEvaluatorBuilder().build(element, ContextUtil.getSourcePosition(evalContext));
        }
        catch (UnsupportedExpressionException ex) {
          PsiElement context = PositionUtil.getContextElement(debuggerContext);
          ExpressionEvaluator eval = CompilingEvaluatorImpl.create(project, context, e ->
            factory.createPsiCodeFragment(text.get(), context, project));
          if (eval != null) {
            return eval;
          }
          throw ex;
        }
      });
      return new WatchItemDescriptor(project, text.get()) {
        @Override
        protected @NotNull ExpressionEvaluator getEvaluator(EvaluationContextImpl evaluationContext) {
          return evaluator;
        }
      };
    });
  }

  private void evaluate(@NotNull XEvaluationCallback baseCallback,
                        @NotNull DescriptorProducer descriptorSupplier) {
    myDebugProcess.getManagerThread().schedule(new DebuggerContextCommandImpl(myDebugProcess.getDebuggerContext(),
                                                                              myStackFrame.getStackFrameProxy().threadProxy()) {
      @Override
      public @NotNull Priority getPriority() {
        return Priority.NORMAL;
      }

      @Override
      public void threadAction(@NotNull SuspendContextImpl suspendContext) {
        XEvaluationOrigin origin = getOrigin(baseCallback);
        ReportingEvaluationCallback callback = new ReportingEvaluationCallback(myDebugProcess.getProject(), baseCallback, origin);
        WatchItemDescriptor descriptor = null;
        try {
          if (DebuggerUIUtil.isObsolete(callback)) {
            return;
          }

          JavaDebugProcess process = myDebugProcess.getXdebugProcess();
          if (process == null) {
            callback.errorOccurred(JavaDebuggerBundle.message("error.no.debug.process"), descriptor);
            return;
          }
          DebuggerContextImpl debuggerContext = myStackFrame.getFrameDebuggerContext(getDebuggerContext());
          EvaluationContextImpl evalContext = debuggerContext.createEvaluationContext();
          if (evalContext == null) {
            callback.errorOccurred(JavaDebuggerBundle.message("error.context.not.available"), descriptor);
            return;
          }
          XEvaluationOrigin.setOrigin(evalContext, origin);

          try {
            descriptor = descriptorSupplier.produce(debuggerContext, evalContext);
            descriptor.setContext(evalContext);
            EvaluateException exception = descriptor.getEvaluateException();
            if (exception != null && descriptor.getValue() == null) {
              callback.invalidExpression(exception.getMessage(), descriptor);
              return;
            }
            callback.evaluated(JavaValue.create(null, descriptor, evalContext, process.getNodeManager(), true));
          }
          catch (EvaluateException e) {
            callback.errorOccurred(e.getMessage(), descriptor);
          }
        }
        catch (Throwable e) {
          callback.errorOccurred(JavaDebuggerBundle.message("error.internal"), descriptor);
          throw e;
        }
      }
    });
  }

  @ApiStatus.Internal
  @Override
  public final void evaluate(
    @NotNull Document document,
    int offset,
    @NotNull ValueHintType hintType,
    @NotNull XEvaluationCallback callback
  ) {
    getPsiExpressionAtOffsetAsync(
      myDebugProcess.getProject(), document, offset,
      hintType == ValueHintType.MOUSE_CLICK_HINT || hintType == ValueHintType.MOUSE_ALT_OVER_HINT
    ).onProcessed(pair -> {
      if (pair != null) {
        PsiElement element = pair.getFirst();
        if (element instanceof PsiExpression) {
          evaluatePsiElement(element, callback);
        }
        else {
          evaluate(XExpressionImpl.fromText(element.getText()), callback, null);
        }
      }
      else {
        callback.errorOccurred(JavaDebuggerBundle.message("evaluation.error.expression.info"));
      }
    });
  }


  private static @NotNull XEvaluationOrigin getOrigin(@NotNull XEvaluationCallback callback) {
    return callback instanceof XEvaluationCallbackWithOrigin callbackWithOrigin ?
           callbackWithOrigin.getOrigin() : XEvaluationOrigin.UNSPECIFIED;
  }

  @Override
  public @NotNull Promise<ExpressionInfo> getExpressionInfoAtOffsetAsync(@NotNull Project project,
                                                                         @NotNull Document document,
                                                                         int offset,
                                                                         boolean sideEffectsAllowed) {
    return getPsiExpressionAtOffsetAsync(project, document, offset, sideEffectsAllowed).then((pair) -> {
      if (pair != null) {
        return new ExpressionInfo(pair.getSecond(), null, null);
      }
      return null;
    });
  }

  private static @NotNull Promise<@Nullable Pair<PsiElement, TextRange>> getPsiExpressionAtOffsetAsync(
    @NotNull Project project,
    @NotNull Document document,
    int offset,
    boolean sideEffectsAllowed
  ) {
    return ReadAction
      .nonBlocking(() -> {
        PsiElement elementAtCursor = DebuggerUtilsEx.findElementAt(PsiDocumentManager.getInstance(project).getPsiFile(document), offset);
        if (elementAtCursor != null && elementAtCursor.isValid()) {
          EditorTextProvider textProvider = EditorTextProvider.EP.forLanguage(elementAtCursor.getLanguage());
          if (textProvider != null) {
            Pair<PsiElement, TextRange> pair = textProvider.findExpression(elementAtCursor, sideEffectsAllowed);
            return pair;
          }
        }
        return null;
      })
      .inSmartMode(project)
      .withDocumentsCommitted(project)
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

  @FunctionalInterface
  private interface DescriptorProducer {
    WatchItemDescriptor produce(DebuggerContextImpl debuggerContext, EvaluationContextImpl evalContext) throws EvaluateException;
  }
}
