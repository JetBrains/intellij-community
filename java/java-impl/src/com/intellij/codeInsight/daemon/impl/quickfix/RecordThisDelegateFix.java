// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class RecordThisDelegateFix extends PsiUpdateModCommandAction<PsiMethod> {

  private RecordThisDelegateFix(@NotNull PsiMethod element) {
    super(element);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiMethod element, @NotNull ModPsiUpdater updater) {
    CommentTracker tracker = new CommentTracker();
    Map<PsiField, PsiExpression> map = collectAssignedFields(element);
    PsiClass containingClass = element.getContainingClass();
    if (containingClass == null) return;
    PsiRecordComponent[] components = containingClass.getRecordComponents();
    if (components.length != map.size()) return;
    StringBuilder text = new StringBuilder("this(");
    for (int i = 0; i < components.length; i++) {
      if (i > 0) text.append(", ");
      PsiRecordComponent component = components[i];
      PsiField field = JavaPsiRecordUtil.getFieldForComponent(component);
      if (field == null) continue;
      PsiExpression expression = map.get(field);
      if (expression == null) continue;
      text.append(tracker.text(expression));
    }
    text.append(");");
    ArrayList<PsiExpression> expressions = new ArrayList<>(map.values());
    PsiCodeBlock body = element.getBody();
    if (body == null || body.getLBrace() == null) {
      return;
    }
    if (expressions.isEmpty()) {
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.project());
      body.addAfter(factory.createStatementFromText(text.toString(), element), body.getLBrace());
      CodeStyleManager.getInstance(context.project()).reformat(body);
      return;
    }
    for (int i = 0; i < expressions.size(); i++) {
      PsiExpression value = expressions.get(i);
      PsiStatement statement = PsiTreeUtil.getParentOfType(value, PsiStatement.class, false);
      if (statement == null) continue;
      if (i == expressions.size() - 1) {
        tracker.replaceAndRestoreComments(statement, text.toString());
      }
      else {
        tracker.delete(statement);
      }
    }
    CodeStyleManager.getInstance(context.project()).reformat(body);
  }

  @Override
  public @Nls @NotNull String getFamilyName() {
    return QuickFixBundle.message("record.delegate.to.canonical.constructor.fix.name");
  }


  public static @Nullable ModCommandAction create(@NotNull PsiMethod method) {
    if (!method.isConstructor()) return null;
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return null;
    if (!containingClass.isRecord()) return null;
    if (JavaPsiRecordUtil.isCanonicalConstructor(method) ||
        JavaPsiRecordUtil.isExplicitCanonicalConstructor(method)) {
      return null;
    }
    PsiMethodCallExpression call = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(method);
    if (call != null) return null;
    if (!canCollectFields(method, containingClass)) return null;
    return new RecordThisDelegateFix(method);
  }

  private static boolean canCollectFields(@NotNull PsiMethod method, PsiClass aClass) {
    Map<PsiField, PsiExpression> collected = collectAssignedFields(method);
    PsiRecordComponent[] components = aClass.getRecordComponents();
    return components.length == collected.size();
  }

  @NotNull
  private static Map<PsiField, PsiExpression> collectAssignedFields(@NotNull PsiMethod method) {
    Map<PsiField, PsiExpression> assignedFields = new HashMap<>();
    PsiCodeBlock body = method.getBody();
    if (body == null) return assignedFields;

    PsiStatement[] statements = body.getStatements();
    for (PsiStatement statement : statements) {
      if (PsiTreeUtil.hasErrorElements(statement)) break;
      if (!(statement instanceof PsiExpressionStatement expressionStatement)) {
        break;
      }
      PsiExpression expression = expressionStatement.getExpression();
      if (!(expression instanceof PsiAssignmentExpression assignment)) {
        break;
      }
      if (assignment.getOperationTokenType() != JavaTokenType.EQ) {
        break;
      }
      PsiExpression lExpression = assignment.getLExpression();
      PsiExpression rExpression = assignment.getRExpression();
      if (rExpression != null && lExpression instanceof PsiReferenceExpression referenceExpression) {
        PsiElement resolved = referenceExpression.resolve();
        if (resolved instanceof PsiField field) {
          if (assignedFields.containsKey(field)) return new HashMap<>();
          assignedFields.put(field, rExpression);
          continue;
        }
      }
      break;
    }
    return assignedFields;
  }
}