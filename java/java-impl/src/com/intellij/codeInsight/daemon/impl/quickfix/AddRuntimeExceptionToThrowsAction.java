// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.modcommand.*;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.intellij.modcommand.ModCommand.*;

public final class AddRuntimeExceptionToThrowsAction implements ModCommandAction {
  private final ThreeState myProcessHierarchy;

  public AddRuntimeExceptionToThrowsAction() {
    this(ThreeState.UNSURE);
  }

  private AddRuntimeExceptionToThrowsAction(@NotNull ThreeState processHierarchy) {
    myProcessHierarchy = processHierarchy;
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    PsiClassType aClass = getRuntimeExceptionAtCaret(context);
    PsiMethod method = PsiTreeUtil.getParentOfType(context.findLeaf(), PsiMethod.class);
    if (method == null) return nop();
    ModCommand command =
      AddExceptionToThrowsFix.addExceptionsToThrowsList(context.project(), method, Collections.singleton(aClass), myProcessHierarchy);
    if (command == null) {
      return chooseAction(QuickFixBundle.message("add.runtime.exception.to.throws.header"),
                          new AddRuntimeExceptionToThrowsAction(ThreeState.YES),
                          new AddRuntimeExceptionToThrowsAction(ThreeState.NO));
    }
    return command;
  }


  private static boolean isMethodThrows(PsiMethod method, PsiClassType exception) {
    PsiClassType[] throwsTypes = method.getThrowsList().getReferencedTypes();
    for (PsiClassType throwsType : throwsTypes) {
      if (throwsType.isAssignableFrom(exception)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    if (!(context.file() instanceof PsiJavaFile)) return null;
    if (!BaseIntentionAction.canModify(context.file())) return null;
    PsiClassType exception = getRuntimeExceptionAtCaret(context);
    if (exception == null) return null;

    PsiMethod method = PsiTreeUtil.getParentOfType(context.findLeaf(), PsiMethod.class, true, PsiLambdaExpression.class);
    if (method == null || !method.getThrowsList().isPhysical() || isMethodThrows(method, exception)) return null;

    return switch (myProcessHierarchy) {
      case YES -> Presentation.of(QuickFixBundle.message("add.exception.to.throws.hierarchy"));
      case NO -> Presentation.of(QuickFixBundle.message("add.exception.to.throws.only.this"));
      case UNSURE ->
        Presentation.of(QuickFixBundle.message("add.runtime.exception.to.throws.text", "throws " + exception.getPresentableText()));
    };
  }

  private static PsiClassType getRuntimeExceptionAtCaret(@NotNull ActionContext context) {
    PsiElement element = context.findLeaf();
    if (element == null) return null;
    PsiThrowStatement expression = PsiTreeUtil.getParentOfType(element, PsiThrowStatement.class);
    if (expression == null) return null;
    PsiExpression exception = expression.getException();
    if (exception == null) return null;
    PsiType type = exception.getType();
    if (!(type instanceof PsiClassType)) return null;
    if (!ExceptionUtil.isUncheckedException((PsiClassType)type)) return null;
    return (PsiClassType)type;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("add.runtime.exception.to.throws.family");
  }
}
