/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.EditorTextProvider;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author egor
 */
public class JavaDebuggerEvaluator extends XDebuggerEvaluator {
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
        if (DebuggerUIUtil.isObsolete(callback)) {
          return;
        }

        JavaDebugProcess process = myDebugProcess.getXdebugProcess();
        if (process == null) {
          callback.errorOccurred("No debug process");
          return;
        }
        TextWithImports text = TextWithImportsImpl.fromXExpression(expression);
        NodeManagerImpl nodeManager = process.getNodeManager();
        WatchItemDescriptor descriptor = nodeManager.getWatchItemDescriptor(null, text, null);
        EvaluationContextImpl evalContext = myStackFrame.getFrameDebuggerContext(getDebuggerContext()).createEvaluationContext();
        if (evalContext == null) {
          callback.errorOccurred("Context is not available");
          return;
        }
        descriptor.setContext(evalContext);
        @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
        EvaluateException exception = descriptor.getEvaluateException();
        if (exception != null && descriptor.getValue() == null) {
          callback.errorOccurred(exception.getMessage());
          return;
        }
        callback.evaluated(JavaValue.create(null, descriptor, evalContext, nodeManager, true));
      }
    });
  }

  @Nullable
  @Override
  public TextRange getExpressionRangeAtOffset(final Project project, final Document document, final int offset, final boolean sideEffectsAllowed) {
    final Ref<TextRange> currentRange = Ref.create(null);
    PsiDocumentManager.getInstance(project).commitAndRunReadAction(() -> {
      try {
        PsiElement elementAtCursor = DebuggerUtilsEx.findElementAt(PsiDocumentManager.getInstance(project).getPsiFile(document), offset);
        if (elementAtCursor == null || !elementAtCursor.isValid()) {
          return;
        }
        Pair<PsiElement, TextRange> pair = findExpression(elementAtCursor, sideEffectsAllowed);
        if (pair != null) {
          currentRange.set(pair.getSecond());
        }
      } catch (IndexNotReadyException ignored) {}
    });
    return currentRange.get();
  }

  @Nullable
  private static Pair<PsiElement, TextRange> findExpression(PsiElement element, boolean allowMethodCalls) {
    final EditorTextProvider textProvider = EditorTextProvider.EP.forLanguage(element.getLanguage());
    if (textProvider != null) {
      return textProvider.findExpression(element, allowMethodCalls);
    }
    return null;
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
