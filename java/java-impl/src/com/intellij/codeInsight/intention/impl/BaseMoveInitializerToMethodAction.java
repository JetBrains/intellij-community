/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * refactored from {@link com.intellij.codeInsight.intention.impl.MoveInitializerToConstructorAction}
 *
 * @author Danila Ponomarenko
 */
public abstract class BaseMoveInitializerToMethodAction extends PsiElementBaseIntentionAction {
  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (element instanceof PsiCompiledElement) return false;
    final PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class, false, PsiMember.class, PsiCodeBlock.class, PsiDocComment.class);
    if (field == null || hasUnsuitableModifiers(field)) return false;
    PsiExpression initializer = field.getInitializer();
    if (initializer == null || initializer.getNextSibling() instanceof PsiErrorElement) return false;
    PsiClass psiClass = field.getContainingClass();

    return psiClass != null && !psiClass.isInterface() && !(psiClass instanceof PsiAnonymousClass) && !(psiClass instanceof PsiSyntheticClass);
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
    field.getInitializer().delete();

    if (!assignments.isEmpty()) {
      highlightRExpression((PsiAssignmentExpression)assignments.get(0).getExpression(), project, editor);
    }
  }

  private static void highlightRExpression(@NotNull PsiAssignmentExpression assignment, @NotNull Project project, Editor editor) {
    final EditorColorsManager manager = EditorColorsManager.getInstance();
    final TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    final PsiExpression expression = assignment.getRExpression();

    HighlightManager.getInstance(project).addOccurrenceHighlights(editor, new PsiElement[]{expression}, attributes, false, null);
  }

  @NotNull
  private static List<PsiExpressionStatement> addFieldAssignments(@NotNull PsiField field, @NotNull Collection<PsiMethod> methods) {
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
    final PsiElementFactory factory = JavaPsiFacade.getInstance(codeBlock.getProject()).getElementFactory();

    final PsiExpressionStatement statement = (PsiExpressionStatement)factory.createStatementFromText(field.getName() + " = y;", codeBlock);

    PsiExpression initializer = field.getInitializer();
    initializer = RefactoringUtil.convertInitializerToNormalExpression(initializer, field.getType());

    final PsiAssignmentExpression expression = (PsiAssignmentExpression)statement.getExpression();
    expression.getRExpression().replace(initializer);

    final PsiElement newStatement = codeBlock.addBefore(statement, findFirstFieldUsage(codeBlock.getStatements(), field));
    replaceWithQualifiedReferences(newStatement, newStatement, factory);
    return (PsiExpressionStatement)newStatement;
  }

  @Nullable
  private static PsiElement findFirstFieldUsage(@NotNull PsiStatement[] statements, @NotNull PsiField field) {
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
      if (RefactoringChangeUtil.isSuperOrThisMethodCall(expression)) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsReference(final @NotNull PsiElement element,
                                           final @NotNull PsiField field) {
    final Ref<Boolean> result = new Ref<>(Boolean.FALSE);
    element.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
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
    if (resolved instanceof PsiVariable && !(resolved instanceof PsiField) && !PsiTreeUtil.isAncestor(root, resolved, false)) {
      final PsiVariable variable = (PsiVariable)resolved;
      PsiElement qualifiedExpr = factory.createExpressionFromText("this." + variable.getName(), expression);
      expression.replace(qualifiedExpr);
    }
  }
}