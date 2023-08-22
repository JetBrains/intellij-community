// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MoveBoundClassToFrontFix extends PsiUpdateModCommandAction<PsiTypeParameter> {
  private final @IntentionName String myName;
  private final @NotNull SmartTypePointer myTypePointer;

  public MoveBoundClassToFrontFix(@NotNull PsiTypeParameter aClass, @NotNull PsiClassType classToExtendFrom) {
    super(aClass);
    myTypePointer = SmartTypePointerManager.getInstance(aClass.getProject()).createSmartTypePointer(classToExtendFrom);
    PsiClass psiClass = classToExtendFrom.resolve();

    myName = QuickFixBundle.message("move.bound.class.to.front.fix.text",
                                    psiClass == null ? "<null>" : HighlightUtil.formatClass(psiClass),
                                    HighlightUtil.formatClass(aClass));
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("move.class.in.extend.list.family");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiTypeParameter typeParameter, @NotNull ModPsiUpdater updater) {
    PsiReferenceList extendsList = typeParameter.getExtendsList();
    if (!(myTypePointer.getType() instanceof PsiClassType classType)) return;
    ExtendsListFix.modifyList(extendsList, false, -1, classType);
    ExtendsListFix.modifyList(extendsList, true, 0, classType);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiTypeParameter typeParameter) {
    return myTypePointer.getType() instanceof PsiClassType classType && classType.resolve() != null ? Presentation.of(myName) : null;
  }
}
