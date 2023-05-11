// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.JavaPsiConstructorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * refactored from {@link MoveInitializerToConstructorAction}
 */
public abstract class BaseMoveInitializerToMethodAction extends PsiElementBaseIntentionAction {
  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (element instanceof PsiCompiledElement) return false;
    final PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class, false, PsiMember.class, PsiCodeBlock.class, PsiDocComment.class);
    if (field == null || hasUnsuitableModifiers(field)) return false;
    // Doesn't work for Groovy
    if (field.getLanguage() != JavaLanguage.INSTANCE) return false;
    PsiExpression initializer = field.getInitializer();
    if (initializer == null || initializer.getNextSibling() instanceof PsiErrorElement) return false;
    PsiClass psiClass = field.getContainingClass();

    return psiClass != null &&
           !psiClass.isInterface() &&
           !psiClass.isRecord() &&
           !(psiClass instanceof PsiAnonymousClass) &&
           !(psiClass instanceof PsiSyntheticClass);
  }

  private boolean hasUnsuitableModifiers(@NotNull PsiField field) {
    for (@PsiModifier.ModifierConstant String modifier : getUnsuitableModifiers()) {
      if (field.hasModifierProperty(modifier)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  protected abstract Collection<String> getUnsuitableModifiers();

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class);
    assert field != null;
    final PsiClass aClass = field.getContainingClass();
    if (aClass == null) return;

    final Collection<PsiMethod> methodsToAddInitialization = getOrCreateMethods(project, editor, element.getContainingFile(), aClass);

    if (methodsToAddInitialization.isEmpty()) return;

    final List<PsiExpressionStatement> assignments = addFieldAssignments(field, methodsToAddInitialization);
    PsiExpression initializer = field.getInitializer();
    if (initializer != null) {
      initializer.delete();
    }

    if (!assignments.isEmpty()) {
      highlightRExpression((PsiAssignmentExpression)assignments.get(0).getExpression(), project, editor);
    }
  }

  private static void highlightRExpression(@NotNull PsiAssignmentExpression assignment, @NotNull Project project, Editor editor) {
    final PsiExpression expression = assignment.getRExpression();

    HighlightManager.getInstance(project).addOccurrenceHighlights(editor, new PsiElement[]{expression},
                                                                  EditorColors.SEARCH_RESULT_ATTRIBUTES, false, null);
  }

  @NotNull
  private static List<PsiExpressionStatement> addFieldAssignments(@NotNull PsiField field, @NotNull Collection<? extends PsiMethod> methods) {
    final List<PsiExpressionStatement> assignments = new ArrayList<>();
    for (PsiMethod method : methods) {
      assignments.add(addAssignment(getOrCreateMethodBody(method), field));
    }
    return assignments;
  }

  @NotNull
  private static PsiCodeBlock getOrCreateMethodBody(@NotNull PsiMethod method) {
    PsiCodeBlock codeBlock = method.getBody();
    if (codeBlock == null) {
      CreateFromUsageUtils.setupMethodBody(method);
      codeBlock = method.getBody();
    }
    return codeBlock;
  }

  @NotNull
  protected abstract Collection<PsiMethod> getOrCreateMethods(@NotNull Project project, @NotNull Editor editor, PsiFile file, @NotNull PsiClass aClass);

  @NotNull
  private static PsiExpressionStatement addAssignment(@NotNull PsiCodeBlock codeBlock, @NotNull PsiField field) throws IncorrectOperationException {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(codeBlock.getProject());

    final PsiExpressionStatement statement = (PsiExpressionStatement)factory.createStatementFromText(field.getName() + " = y;", codeBlock);

    PsiExpression initializer = field.getInitializer();
    initializer = CommonJavaRefactoringUtil.convertInitializerToNormalExpression(initializer, field.getType());

    final PsiAssignmentExpression expression = (PsiAssignmentExpression)statement.getExpression();
    Objects.requireNonNull(expression.getRExpression()).replace(Objects.requireNonNull(initializer));

    final PsiElement newStatement = codeBlock.addBefore(statement, findFirstFieldUsage(codeBlock.getStatements(), field));
    replaceWithQualifiedReferences(newStatement, newStatement, factory);
    return (PsiExpressionStatement)newStatement;
  }

  @Nullable
  private static PsiElement findFirstFieldUsage(PsiStatement @NotNull [] statements, @NotNull PsiField field) {
    for (PsiStatement blockStatement : statements) {
      if (!isSuperOrThisMethodCall(blockStatement) && containsReference(blockStatement, field)) {
        return blockStatement;
      }
    }
    return null;
  }

  private static boolean isSuperOrThisMethodCall(@NotNull PsiStatement statement) {
    if (statement instanceof PsiExpressionStatement) {
      final PsiElement expression = ((PsiExpressionStatement)statement).getExpression();
      return JavaPsiConstructorUtil.isConstructorCall(expression);
    }
    return false;
  }

  private static boolean containsReference(final @NotNull PsiElement element,
                                           final @NotNull PsiField field) {
    final Ref<Boolean> result = new Ref<>(Boolean.FALSE);
    element.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        if (expression.resolve() == field) {
          result.set(Boolean.TRUE);
        }
        super.visitReferenceExpression(expression);
      }
    });
    return result.get().booleanValue();
  }

  private static void replaceWithQualifiedReferences(@NotNull PsiElement expression, @NotNull PsiElement root, @NotNull PsiElementFactory factory) throws IncorrectOperationException {
    final PsiReference reference = expression.getReference();
    if (reference == null) {
      for (PsiElement child : expression.getChildren()) {
        replaceWithQualifiedReferences(child, root, factory);
      }
      return;
    }

    final PsiElement resolved = reference.resolve();
    if (resolved instanceof PsiVariable variable && !(resolved instanceof PsiField) && !PsiTreeUtil.isAncestor(root, resolved, false)) {
      PsiElement qualifiedExpr = factory.createExpressionFromText("this." + variable.getName(), expression);
      expression.replace(qualifiedExpr);
    }
  }
}