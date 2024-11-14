// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation;

import com.intellij.codeInsight.completion.CompletionService;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.codeinsight.RuntimeTypeEvaluator;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.JavaDebuggerCodeFragmentFactory;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilder;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
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
 */
public class DefaultCodeFragmentFactory extends JavaDebuggerCodeFragmentFactory {
  private static final class SingletonHolder {
    public static final DefaultCodeFragmentFactory ourInstance = new DefaultCodeFragmentFactory();
  }

  public static DefaultCodeFragmentFactory getInstance() {
    return SingletonHolder.ourInstance;
  }

  @Override
  protected JavaCodeFragment createPresentationPsiCodeFragmentImpl(final @NotNull TextWithImports item,
                                                                   final PsiElement context,
                                                                   final @NotNull Project project) {
    return createPsiCodeFragment(item, context, project);
  }

  @Override
  public JavaCodeFragment createPsiCodeFragmentImpl(TextWithImports item, PsiElement context, final @NotNull Project project) {
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

    if (!item.getImports().isEmpty()) {
      fragment.addImportsFromString(item.getImports());
    }
    fragment.setVisibilityChecker(JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);
    fragment.putUserData(KEY, "DebuggerComboBoxEditor.IS_DEBUGGER_EDITOR");
    fragment.putCopyableUserData(JavaCompletionUtil.DYNAMIC_TYPE_EVALUATOR, (expression, parameters) -> {
      if (!RuntimeTypeEvaluator.isSubtypeable(expression)) {
        return null;
      }

      if (parameters.getInvocationCount() <= 1 && JavaCompletionUtil.mayHaveSideEffects(expression)) {
        CompletionService.getCompletionService().setAdvertisementText(JavaDebuggerBundle.message("invoke.completion.once.more"));
        return null;
      }

      final DebuggerContextImpl debuggerContext = DebuggerManagerEx.getInstanceEx(project).getContext();
      DebuggerManagerThreadImpl managerThread = debuggerContext.getManagerThread();
      if (managerThread != null) {
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
        managerThread.invoke(worker);
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

  @Override
  public boolean isContextAccepted(PsiElement contextElement) {
    return true; // default factory works everywhere debugger can stop
  }

  @Override
  @NotNull
  public LanguageFileType getFileType() {
    return JavaFileType.INSTANCE;
  }

  @Override
  public EvaluatorBuilder getEvaluatorBuilder() {
    return EvaluatorBuilderImpl.getInstance();
  }

  @Override
  public EvaluationContextWrapper createEvaluationContextWrapper() {
    return new JavaEvaluationContextWrapper();
  }

  public static final Key<String> KEY = Key.create("DefaultCodeFragmentFactory.KEY");

  public static boolean isDebuggerFile(PsiFile file) {
    return KEY.isIn(file);
  }
}
