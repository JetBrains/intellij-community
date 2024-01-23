// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.java.JavaBundle;
import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.lang.LanguageCodeInsightActionHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;


public final class JavaImplementMethodsHandler implements ContextAwareActionHandler, LanguageCodeInsightActionHandler {
  @Override
  public boolean isValidFor(final Editor editor, final PsiFile file) {
    if (!(file instanceof PsiJavaFile) && !(file instanceof PsiCodeFragment)) {
      return false;
    }

    return OverrideImplementUtil.getContextClass(file.getProject(), editor, file, PsiUtil.isLanguageLevel8OrHigher(file)) != null;
  }

  @Override
  public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
    PsiClass aClass = OverrideImplementUtil.getContextClass(project, editor, file, PsiUtil.isLanguageLevel8OrHigher(file));
    if (aClass == null) {
      return;
    }
    ReadAction.nonBlocking(() -> {
        return DumbService.getInstance(project).computeWithAlternativeResolveEnabled(
          () -> OverrideImplementExploreUtil.getMethodSignaturesToImplement(aClass).isEmpty());
      })
      .finishOnUiThread(ModalityState.defaultModalityState(), empty -> {
        if (empty) {
          HintManager.getInstance().showErrorHint(editor, JavaBundle.message("implement.method.no.methods.to.implement"));
        }
        else {
          OverrideImplementUtil.chooseAndImplementMethods(project, editor, aClass);
        }
      })
      .expireWhen(() -> !aClass.isValid())
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public boolean isAvailableForQuickList(@NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext dataContext) {
    PsiClass aClass = OverrideImplementUtil.getContextClass(file.getProject(), editor, file, PsiUtil.isLanguageLevel8OrHigher(file));
    if (aClass == null) {
      return false;
    }
    return DumbService.getInstance(aClass.getProject()).computeWithAlternativeResolveEnabled(
      () -> !OverrideImplementExploreUtil.getMethodSignaturesToImplement(aClass).isEmpty());
  }
}
