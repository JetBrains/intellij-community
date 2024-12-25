// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class VariableArrayTypeFix extends PsiUpdateModCommandAction<PsiArrayInitializerExpression> {
  private final @NotNull PsiArrayType myTargetType;
  private final @IntentionName String myName;
  private final @IntentionFamilyName String myFamilyName;

  private VariableArrayTypeFix(@NotNull PsiArrayInitializerExpression initializer,
                               @NotNull PsiArrayType arrayType,
                               @NotNull PsiVariable variable) {
    super(initializer);
    myTargetType = arrayType;
    PsiExpression myNewExpression = getNewExpressionLocal(initializer);
    myName = myTargetType.equals(variable.getType()) && myNewExpression != null
             ? QuickFixBundle.message("change.new.operator.type.text", getNewText(myNewExpression,initializer), myTargetType.getCanonicalText(), "")
             : QuickFixBundle.message("fix.variable.type.text", formatType(variable), variable.getName(), myTargetType.getCanonicalText());
    myFamilyName = QuickFixBundle.message(myTargetType.equals(variable.getType()) && myNewExpression != null ? "change.new.operator.type.family"
                                                                                                             : "fix.variable.type.family");
  }

  public static @Nullable VariableArrayTypeFix createFix(PsiArrayInitializerExpression initializer, @NotNull PsiType componentType) {
    PsiArrayType arrayType = new PsiArrayType(componentType);
    PsiArrayInitializerExpression arrayInitializer = initializer;
    while (arrayInitializer.getParent() instanceof PsiArrayInitializerExpression) {
      arrayInitializer = (PsiArrayInitializerExpression)arrayInitializer.getParent();
      arrayType = new PsiArrayType(arrayType);
    }
    PsiVariable variable = getVariableLocal(arrayInitializer);
    if (variable == null) return null;
    return new VariableArrayTypeFix(arrayInitializer, arrayType, variable);
  }

  private static String formatType(@NotNull PsiVariable variable) {
    return JavaElementKind.fromElement(variable).lessDescriptive().subject();
  }

  private static PsiVariable getVariableLocal(@NotNull PsiArrayInitializerExpression initializer) {
    PsiVariable variableLocal = null;

    final PsiElement parent = initializer.getParent();
    if (parent instanceof PsiVariable) {
      variableLocal = (PsiVariable)parent;
    }
    else if (parent instanceof PsiNewExpression newExpressionLocal) {
      final PsiElement newParent = newExpressionLocal.getParent();
      if (newParent instanceof PsiAssignmentExpression) {
        variableLocal = getFromAssignment((PsiAssignmentExpression)newParent);
      }
      else if (newParent instanceof PsiVariable) {
        variableLocal = (PsiVariable)newParent;
      }
    }
    else if (parent instanceof PsiAssignmentExpression) {
      variableLocal = getFromAssignment((PsiAssignmentExpression)parent);
    }
    return variableLocal;
  }

  private static PsiNewExpression getNewExpressionLocal(@NotNull PsiArrayInitializerExpression initializer) {
    return ObjectUtils.tryCast(initializer.getParent(), PsiNewExpression.class);
  }

  private static @Nullable PsiVariable getFromAssignment(final PsiAssignmentExpression assignment) {
    final PsiExpression reference = assignment.getLExpression();
    final PsiElement referencedElement = reference instanceof PsiReferenceExpression ? ((PsiReferenceExpression)reference).resolve() : null;
    return referencedElement instanceof PsiVariable ? (PsiVariable)referencedElement : null;
  }

  private static String getNewText(PsiElement myNewExpression, PsiArrayInitializerExpression myInitializer) {
    final String newText = myNewExpression.getText();
    final int initializerIdx = newText.indexOf(myInitializer.getText());
    if (initializerIdx != -1) {
      return newText.substring(0, initializerIdx).trim();
    }
    return newText;
  }

  @Override
  public @NotNull String getFamilyName() {
    return myFamilyName;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiArrayInitializerExpression element) {
    final PsiVariable variable = getVariableLocal(element);
    if (variable == null || !BaseIntentionAction.canModify(variable) || !myTargetType.isValid()) return null;
    return Presentation.of(myName);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiArrayInitializerExpression initializer, @NotNull ModPsiUpdater updater) {
    PsiVariable variable = getVariableLocal(initializer);
    if (variable == null) return;
    variable = updater.getWritable(variable);
    /*
      only for the case when in same statement with initialization
     */
    final PsiNewExpression myNewExpression = getNewExpressionLocal(initializer);

    if (!myTargetType.equals(variable.getType())) {
      fixVariableType(context.project(), variable);
    }

    if (myNewExpression != null) {
      fixArrayInitializer(initializer, myNewExpression);
    }
  }

  private void fixVariableType(@NotNull Project project, PsiVariable myVariable) {
    myVariable.normalizeDeclaration();
    Objects.requireNonNull(myVariable.getTypeElement()).replace(JavaPsiFacade.getElementFactory(project).createTypeElement(myTargetType));
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(myVariable);
  }

  private void fixArrayInitializer(PsiArrayInitializerExpression myInitializer, PsiNewExpression myNewExpression) {
    @NonNls String text = "new " + myTargetType.getCanonicalText() + "{}";
    final PsiNewExpression newExpression = (PsiNewExpression) JavaPsiFacade.getElementFactory(myNewExpression.getProject()).createExpressionFromText(text, myNewExpression.getParent());
    final PsiElement[] children = newExpression.getChildren();
    children[children.length - 1].replace(myInitializer);
    myNewExpression.replace(newExpression);
  }
}
