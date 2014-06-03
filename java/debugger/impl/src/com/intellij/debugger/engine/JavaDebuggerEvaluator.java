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

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.EditorTextProvider;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
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
    myDebugProcess.getManagerThread().schedule(new DebuggerContextCommandImpl(myDebugProcess.getDebuggerContext()) {
      @Override
      public void threadAction() {
        WatchItemDescriptor descriptor = new WatchItemDescriptor(myDebugProcess.getProject(), TextWithImportsImpl.fromXExpression(
          expression));
        EvaluationContextImpl evalContext = myStackFrame.getFrameDebuggerContext().createEvaluationContext();
        if (evalContext == null) {
          callback.errorOccurred("Context is not available");
          return;
        }
        JavaDebugProcess process = myDebugProcess.getXdebugProcess();
        if (process != null) {
          callback.evaluated(JavaValue.create(descriptor, evalContext, process.getNodeManager()));
        }
      }
    });
  }

  @Nullable
  @Override
  public TextRange getExpressionRangeAtOffset(final Project project, final Document document, final int offset, final boolean sideEffectsAllowed) {
    final Ref<TextRange> currentRange = Ref.create(null);
    PsiDocumentManager.getInstance(project).commitAndRunReadAction(new Runnable() {
      @Override
      public void run() {
        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
        if (psiFile == null) {
          return;
        }
        PsiElement elementAtCursor = psiFile.findElementAt(offset);
        if (elementAtCursor == null) {
          return;
        }
        Pair<PsiElement, TextRange> pair = findExpression(elementAtCursor, sideEffectsAllowed);
        if (pair != null) {
          currentRange.set(pair.getSecond());
        }
      }
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
}
