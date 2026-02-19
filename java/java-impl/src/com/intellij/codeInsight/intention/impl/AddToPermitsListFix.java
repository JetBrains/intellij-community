// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.ig.psiutils.SealedUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class AddToPermitsListFix extends PsiUpdateModCommandAction<PsiClass> {
  private final String myParentName;
  private final String myClassName;
  private final String myClassQualifiedName;

  public AddToPermitsListFix(@NotNull PsiClass subClass, @NotNull PsiClass superClass) {
    super(superClass);
    myParentName = Objects.requireNonNull(superClass.getName());
    myClassQualifiedName = subClass.getQualifiedName();
    myClassName = Objects.requireNonNull(subClass.getName());
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiClass superClass, @NotNull ModPsiUpdater updater) {
    SealedUtils.addClassToPermitsList(superClass, myClassQualifiedName);
    PsiReferenceList list = superClass.getPermitsList();
    if (list != null) {
      JavaCodeStyleManager.getInstance(context.project()).shortenClassReferences(list);
    }
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiClass element) {
    if (myClassQualifiedName == null) return null;
    return Presentation.of(JavaBundle.message("add.to.permits.list", myClassName, myParentName)).withFixAllOption(this);
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return JavaBundle.message("add.to.permits.list.family.name");
  }
}
