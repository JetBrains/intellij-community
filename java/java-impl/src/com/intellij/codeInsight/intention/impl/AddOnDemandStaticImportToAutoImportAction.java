// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.java.JavaBundle;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AddOnDemandStaticImportToAutoImportAction extends PsiBasedModCommandAction<PsiElement> {

  private AddOnDemandStaticImportToAutoImportAction() {
    super(PsiElement.class);
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("intention.add.on.demand.static.import.to.auto.import.family");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    String nameToImport = getNameToImport(element);
    if (nameToImport == null) return null;
    return Presentation.of(JavaBundle.message("intention.add.on.demand.static.import.to.auto.import.text", nameToImport));
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiElement element) {
    String nameToImport = getNameToImport(element);
    if (nameToImport == null) return ModCommand.nop();
    return ModCommand.updateOptionList(context.file(), "JavaProjectCodeInsightSettings.includedAutoStaticNames",
                                       strings -> strings.add(nameToImport));
  }

  @Nullable
  private static String getNameToImport(@NotNull PsiElement element) {
    String name = null;
    PsiImportStaticStatement statement = PsiTreeUtil.getParentOfType(element, PsiImportStaticStatement.class);
    if (statement != null && statement.isOnDemand()) {
      PsiJavaCodeReferenceElement importReference = statement.getImportReference();
      if (importReference == null) return null;
      name = importReference.getCanonicalText();
    }

    if (element.getParent() instanceof PsiReferenceExpression referenceExpression &&
        referenceExpression.getParent() instanceof PsiMethodCallExpression methodCallExpression) {
      PsiFile file = element.getContainingFile();
      if (!(file instanceof PsiJavaFile javaFile)) return null;
      PsiImportList importList = javaFile.getImportList();
      if (importList == null) return null;
      PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      if (!(methodExpression.resolve() instanceof PsiMethod method)) {
        return null;
      }
      if (!method.hasModifier(JvmModifier.STATIC)) {
        return null;
      }
      PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) return null;
        String classQualifiedName = containingClass.getQualifiedName();
        if (classQualifiedName == null) {
          return null;
        }
        for (PsiImportStaticStatement staticStatement : importList.getImportStaticStatements()) {
          PsiJavaCodeReferenceElement importReference = staticStatement.getImportReference();
          if (importReference == null) continue;
          if (staticStatement.isOnDemand() &&
              classQualifiedName.equals(importReference.getQualifiedName())) {
            name = classQualifiedName;
            break;
          }
          if (!staticStatement.isOnDemand() &&
              (classQualifiedName + "." + methodExpression.getReferenceName()).equals(importReference.getQualifiedName())) {
            name = importReference.getQualifiedName();
            break;
          }
        }
      }
    }
    if (name == null) return null;
    if (JavaCodeStyleManager.getInstance(element.getProject()).isStaticAutoImportName(name)) return null;
    return name;
  }
}
