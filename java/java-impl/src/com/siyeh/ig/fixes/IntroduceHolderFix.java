// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.stream.Stream;

/**
 * @author Bas Leijdekkers
 */
public class IntroduceHolderFix extends PsiUpdateModCommandQuickFix {

  private IntroduceHolderFix() {}

  public static IntroduceHolderFix createFix(PsiField field, PsiIfStatement ifStatement) {
    if (!isStaticAndAssignedOnce(field) || !isSafeToDeleteIfStatement(ifStatement, field)) {
      return null;
    }
    return new IntroduceHolderFix();
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PsiReferenceExpression referenceExpression;
    final PsiIfStatement ifStatement;
    if (element instanceof PsiKeyword) {
      // double-checked locking
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiIfStatement)) {
        return;
      }
      ifStatement = (PsiIfStatement)parent;
      final PsiIfStatement innerIfStatement = getDoubleCheckedLockingInnerIf(ifStatement);
      if (innerIfStatement == null) {
        return;
      }
      final PsiStatement thenBranch2 = ControlFlowUtils.stripBraces(innerIfStatement.getThenBranch());
      if (!(thenBranch2 instanceof PsiExpressionStatement expressionStatement)) {
        return;
      }
      final PsiExpression expression = expressionStatement.getExpression();
      if (!(expression instanceof PsiAssignmentExpression assignmentExpression)) {
        return;
      }
      final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(assignmentExpression.getLExpression());
      if (!(lhs instanceof PsiReferenceExpression)) {
        return;
      }
      referenceExpression = (PsiReferenceExpression)lhs;
    } else {
      referenceExpression = (PsiReferenceExpression)element;
      ifStatement = PsiTreeUtil.getParentOfType(referenceExpression, PsiIfStatement.class);
    }
    replaceWithStaticHolder(referenceExpression, ifStatement, updater);
  }

  public static PsiIfStatement getDoubleCheckedLockingInnerIf(PsiIfStatement ifStatement) {
    final PsiStatement thenBranch = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
    if (!(thenBranch instanceof PsiSynchronizedStatement synchronizedStatement)) {
      return null;
    }
    final PsiCodeBlock body = synchronizedStatement.getBody();
    final PsiStatement statement = ControlFlowUtils.getOnlyStatementInBlock(body);
    return (statement instanceof PsiIfStatement) ? (PsiIfStatement)statement : null;
  }

  private static void replaceWithStaticHolder(PsiReferenceExpression referenceExpression, PsiIfStatement ifStatement,
                                              @NotNull ModPsiUpdater updater) {
    final PsiElement resolved = referenceExpression.resolve();
    if (!(resolved instanceof PsiField field)) {
      return;
    }
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(field.getProject());
    final String fieldName = field.getName();
    final @NonNls String holderName =
      StringUtil.capitalize(codeStyleManager.variableNameToPropertyName(fieldName, VariableKind.STATIC_FINAL_FIELD)) + "Holder";
    final PsiElement expressionParent = referenceExpression.getParent();
    if (!(expressionParent instanceof PsiAssignmentExpression assignmentExpression)) {
      return;
    }
    final PsiExpression rhs = assignmentExpression.getRExpression();
    if (rhs == null) {
      return;
    }
    final @NonNls String text = "private static final class " + holderName + " {}";
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(field.getProject());
    final PsiClass holder = elementFactory.createClassFromText(text, field).getInnerClasses()[0];
    final PsiMember method = PsiTreeUtil.getParentOfType(referenceExpression, PsiMember.class);
    if (method == null) {
      return;
    }
    final PsiClass holderClass = (PsiClass)method.getParent().addBefore(holder, method);
    final PsiField newField = (PsiField)holderClass.add(field);
    final PsiModifierList modifierList = newField.getModifierList();
    assert modifierList != null;
    modifierList.setModifierProperty(PsiModifier.FINAL, true);
    if (!PsiUtil.isAvailable(JavaFeature.NESTMATES, holderClass)) {
      modifierList.setModifierProperty(PsiModifier.PACKAGE_LOCAL, true);
    }
    newField.setInitializer(rhs);
    CodeStyleManager.getInstance(referenceExpression.getProject()).reformat(holderClass);

    if (ifStatement != null) {
      new CommentTracker().deleteAndRestoreComments(ifStatement);
    }

    final PsiExpression holderReference = elementFactory.createExpressionFromText(holderName + "." + fieldName, field);
    final PsiClass containingClass = PsiUtil.getTopLevelClass(field);
    if (containingClass == null) return;
    // Search references within top-level class only.
    // If there are other references, the fix should not be created, see isStaticAndAssignedOnce
    for (PsiReferenceExpression reference : VariableAccessUtils.getVariableReferences(field, containingClass)) {
      reference.replace(holderReference);
    }
    String suggestedHolderName = suggestHolderName(field);
    field.delete();

    updater.rename(holderClass, Stream.of(holderClass.getName(), suggestedHolderName).distinct().toList());
  }

  private static @NonNls String suggestHolderName(PsiField field) {
    String string = field.getType().getDeepComponentType().getPresentableText();
    final int index = string.indexOf('<');
    if (index != -1) {
      string = string.substring(0, index);
    }
    return string + "Holder";
  }

  @Override
  public @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("introduce.holder.class.quickfix");
  }

  private static boolean isStaticAndAssignedOnce(PsiField field) {
    if (!field.hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }
    final PsiClass containingClass = PsiUtil.getTopLevelClass(field);
    final int[] writeCount = new int[1];
    return ReferencesSearch.search(field).forEach(reference -> {
      final PsiElement element = reference.getElement();
      if (!PsiTreeUtil.isAncestor(containingClass, element, true)) {
        return false;
      }
      if (!(element instanceof PsiExpression) || !PsiUtil.isAccessedForWriting((PsiExpression)element)) {
        return true;
      }
      return ++writeCount[0] != 2;
    });
  }

  private static boolean isSafeToDeleteIfStatement(PsiIfStatement ifStatement, PsiField field) {
    if (ifStatement.getElseBranch() != null) {
      return false;
    }
    final PsiStatement thenBranch = ifStatement.getThenBranch();
    if (thenBranch == null) {
      return false;
    }
    final PsiStatement statement = ControlFlowUtils.stripBraces(thenBranch);
    if (!(statement instanceof PsiExpressionStatement expressionStatement)) {
      return false;
    }
    return isSimpleAssignment(expressionStatement, field);
  }

  private static boolean isSimpleAssignment(PsiExpressionStatement expressionStatement, PsiField field) {
    final PsiExpression expression = expressionStatement.getExpression();
    if (!(expression instanceof PsiAssignmentExpression assignmentExpression)) {
      return false;
    }
    final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(assignmentExpression.getLExpression());
    if (!(lhs instanceof PsiReferenceExpression referenceExpression)) {
      return false;
    }
    if (!field.equals(referenceExpression.resolve())) {
      return false;
    }
    final PsiExpression rhs = assignmentExpression.getRExpression();
    final boolean safe = PsiTreeUtil.processElements(rhs, PsiReferenceExpression.class, ref -> {
      final PsiElement target = ref.resolve();
      return !(target instanceof PsiLocalVariable) && !(target instanceof PsiParameter);
    });
    if (!safe) {
      return false;
    }
    final PsiElement[] elements = rhs == null ? PsiElement.EMPTY_ARRAY : new PsiElement[] {rhs};
    final HashSet<PsiField> usedFields = new HashSet<>();
    final PsiClass targetClass = field.getContainingClass();
    return CommonJavaRefactoringUtil.canBeStatic(targetClass, expressionStatement, elements, usedFields) && usedFields.isEmpty();
  }
}
