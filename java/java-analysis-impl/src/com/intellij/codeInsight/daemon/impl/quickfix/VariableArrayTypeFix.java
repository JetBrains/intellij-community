// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class VariableArrayTypeFix extends LocalQuickFixOnPsiElement {
  @NotNull
  private final PsiArrayType myTargetType;
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

  @Nullable
  public static VariableArrayTypeFix createFix(PsiArrayInitializerExpression initializer, @NotNull PsiType componentType) {
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
    else if (parent instanceof PsiNewExpression) {
      PsiNewExpression newExpressionLocal = (PsiNewExpression)parent;
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

  @Nullable
  private static PsiVariable getFromAssignment(final PsiAssignmentExpression assignment) {
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

  @NotNull
  @Override
  public String getText() {
    return myName;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return myFamilyName;
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    final PsiArrayInitializerExpression myInitializer = (PsiArrayInitializerExpression)startElement;
    final PsiVariable myVariable = getVariableLocal(myInitializer);

    return myVariable != null
           && myVariable.isValid()
           && BaseIntentionAction.canModify(myVariable)
           && myTargetType.isValid();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    final PsiArrayInitializerExpression myInitializer = (PsiArrayInitializerExpression)startElement;
    final PsiVariable myVariable = getVariableLocal(myInitializer);
    if (myVariable == null) return;
    /*
      only for the case when in same statement with initialization
     */
    final PsiNewExpression myNewExpression = getNewExpressionLocal(myInitializer);

    if (!FileModificationService.getInstance().prepareFileForWrite(myVariable.getContainingFile())) return;

    if (! myTargetType.equals(myVariable.getType())) {
      WriteAction.run(() -> fixVariableType(project, file, myVariable));
    }

    if (myNewExpression != null) {
      if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;

      WriteAction.run(() -> fixArrayInitializer(myInitializer, myNewExpression));
    }
  }

  private void fixVariableType(@NotNull Project project, @NotNull PsiFile file, PsiVariable myVariable) {
    myVariable.normalizeDeclaration();
    myVariable.getTypeElement().replace(JavaPsiFacade.getElementFactory(project).createTypeElement(myTargetType));
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(myVariable);

    if (! myVariable.getContainingFile().equals(file)) {
      UndoUtil.markPsiFileForUndo(myVariable.getContainingFile());
    }
  }

  private void fixArrayInitializer(PsiArrayInitializerExpression myInitializer, PsiNewExpression myNewExpression) {
    @NonNls String text = "new " + myTargetType.getCanonicalText() + "{}";
    final PsiNewExpression newExpression = (PsiNewExpression) JavaPsiFacade.getElementFactory(myNewExpression.getProject()).createExpressionFromText(text, myNewExpression.getParent());
    final PsiElement[] children = newExpression.getChildren();
    children[children.length - 1].replace(myInitializer);
    myNewExpression.replace(newExpression);
  }
}
