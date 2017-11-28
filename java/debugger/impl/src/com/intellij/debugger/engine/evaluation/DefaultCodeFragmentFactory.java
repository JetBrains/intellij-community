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
package com.intellij.debugger.engine.evaluation;

import com.intellij.codeInsight.completion.CompletionService;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.codeinsight.RuntimeTypeEvaluator;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilder;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.DebuggerExpressionComboBox;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Eugene Zhuravlev
 *         Date: Jun 7, 2005
 */
public class DefaultCodeFragmentFactory extends CodeFragmentFactory {
  private static final class SingletonHolder {
    public static final DefaultCodeFragmentFactory ourInstance = new DefaultCodeFragmentFactory();
  }

  public static DefaultCodeFragmentFactory getInstance() {
    return SingletonHolder.ourInstance;
  }

  public JavaCodeFragment createPresentationCodeFragment(final TextWithImports item, final PsiElement context, final Project project) {
    return createCodeFragment(item, context, project);
  }

  public JavaCodeFragment createCodeFragment(TextWithImports item, PsiElement context, final Project project) {
    final JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(project);
    final String text = item.getText();

    JavaCodeFragment fragment = null;
    if (CodeFragmentKind.EXPRESSION == item.getKind()) {
      try {
        String expressionText = StringUtil.trimTrailing(text, ';');
        if (!expressionText.isEmpty()) {
          JavaPsiFacade.getElementFactory(project).createExpressionFromText(expressionText, context); // to test that expression is ok
        }
        fragment = factory.createExpressionCodeFragment(expressionText, context, null, true);
      }
      catch (IncorrectOperationException ignored) {
      }
    }

    if (fragment == null) {
      fragment = factory.createCodeBlockCodeFragment(text, context, true);
    }

    if(item.getImports().length() > 0) {
      fragment.addImportsFromString(item.getImports());
    }
    fragment.setVisibilityChecker(JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);
    //noinspection HardCodedStringLiteral
    fragment.putUserData(KEY, "DebuggerComboBoxEditor.IS_DEBUGGER_EDITOR");
    fragment.putCopyableUserData(JavaCompletionUtil.DYNAMIC_TYPE_EVALUATOR, (expression, parameters) -> {
      if (!RuntimeTypeEvaluator.isSubtypeable(expression)) {
        return null;
      }

      if (parameters.getInvocationCount() <= 1 && JavaCompletionUtil.mayHaveSideEffects(expression)) {
        final CompletionService service = CompletionService.getCompletionService();
        if (parameters.getInvocationCount() < 2) {
          service.setAdvertisementText("Invoke completion once more to see runtime type variants");
        }
        return null;
      }

      final DebuggerContextImpl debuggerContext = DebuggerManagerEx.getInstanceEx(project).getContext();
      DebuggerSession debuggerSession = debuggerContext.getDebuggerSession();
      if (debuggerSession != null && debuggerContext.getSuspendContext() != null) {
        final Semaphore semaphore = new Semaphore();
        semaphore.down();
        final AtomicReference<PsiType> nameRef = new AtomicReference<>();
        final RuntimeTypeEvaluator worker =
          new RuntimeTypeEvaluator(null, expression, debuggerContext, ProgressManager.getInstance().getProgressIndicator()) {
            @Override
            protected void typeCalculationFinished(@Nullable PsiType type) {
              nameRef.set(type);
              semaphore.up();
            }
          };
        debuggerSession.getProcess().getManagerThread().invoke(worker);
        for (int i = 0; i < 50; i++) {
          ProgressManager.checkCanceled();
          if (semaphore.waitFor(20)) break;
        }
        return nameRef.get();
      }
      return null;
    });

    return fragment;
  }

  public boolean isContextAccepted(PsiElement contextElement) {
    return true; // default factory works everywhere debugger can stop
  }

  @NotNull
  public LanguageFileType getFileType() {
    return StdFileTypes.JAVA;
  }

  @Override
  public EvaluatorBuilder getEvaluatorBuilder() {
    return EvaluatorBuilderImpl.getInstance();
  }

  public static final Key<String> KEY = Key.create("DefaultCodeFragmentFactory.KEY");

  public static boolean isDebuggerFile(PsiFile file) {
    return file.getUserData(KEY) != null || file.getUserData(DebuggerExpressionComboBox.KEY) != null;
  }
}
