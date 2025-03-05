// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.memory;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class ReplaceEmptyArrayToConstantFix extends PsiUpdateModCommandQuickFix {
  private final String myText;
  private final @IntentionName String myName;

  public ReplaceEmptyArrayToConstantFix(PsiClass aClass, PsiField field) {
    myText = aClass.getQualifiedName() + "." + field.getName();
    myName = CommonQuickFixBundle.message("fix.replace.with.x", aClass.getName() + "." + field.getName());
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  @Override
  public @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("constant.for.zero.length.array.quickfix.family");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
    PsiExpression newExp = JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText(myText, startElement);
    PsiElement element = startElement.replace(newExp);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(element);
  }
}
