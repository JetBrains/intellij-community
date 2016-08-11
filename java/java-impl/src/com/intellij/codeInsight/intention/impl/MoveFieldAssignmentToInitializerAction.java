/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author cdr
 */
public class MoveFieldAssignmentToInitializerAction extends BaseIntentionAction {
  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  @NotNull
  public String getText() {
    return CodeInsightBundle.message("intention.move.field.assignment.to.declaration");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiAssignmentExpression assignment = getAssignmentUnderCaret(editor, file);
    if (assignment == null) return false;
    PsiElement parent = assignment.getParent();
    if (!(parent instanceof PsiExpressionStatement)) return false;
    PsiField field = getAssignedField(assignment);
    if (field == null || field.hasInitializer()) return false;
    PsiClass psiClass = field.getContainingClass();

    if (psiClass == null || psiClass.isInterface()) return false;
    if (psiClass.getContainingFile() != file) return false;
    PsiModifierListOwner ctrOrInitializer = enclosingMethodOrClassInitializer(assignment, field);
    if (ctrOrInitializer == null) return false;
    if (ctrOrInitializer.hasModifierProperty(PsiModifier.STATIC) != field.hasModifierProperty(PsiModifier.STATIC)) return false;

    if (!isValidAsFieldInitializer(assignment.getRExpression(), ctrOrInitializer)) return false;
    if (!isInitializedWithSameExpression(field, assignment, new ArrayList<>())) return false;
    return true;
  }

  private static boolean isValidAsFieldInitializer(final PsiExpression initializer, final PsiModifierListOwner ctrOrInitializer) {
    if (initializer == null) return false;
    final Ref<Boolean> result = new Ref<>(Boolean.TRUE);
    initializer.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        PsiElement resolved = expression.resolve();
        if (resolved == null) return;
        if (PsiTreeUtil.isAncestor(ctrOrInitializer, resolved, false) && !PsiTreeUtil.isAncestor(initializer, resolved, false)) {
          // resolved somewhere inside constructor but outside initializer
          result.set(Boolean.FALSE);
        }
      }
    });
    return result.get().booleanValue();
  }

  private static PsiModifierListOwner enclosingMethodOrClassInitializer(final PsiAssignmentExpression assignment, final PsiField field) {
    PsiElement parentOwner = assignment;
    while (true) {
      parentOwner = PsiTreeUtil.getParentOfType(parentOwner, PsiModifierListOwner.class, true, PsiMember.class);
      if (parentOwner == null) return null;
      PsiElement parent = parentOwner.getParent();

      if (parent == field.getContainingClass()) return (PsiModifierListOwner)parentOwner;
    }
  }

  private static boolean isInitializedWithSameExpression(final PsiField field,
                                                         final PsiAssignmentExpression assignment,
                                                         final Collection<PsiAssignmentExpression> initializingAssignments) {
    final PsiExpression expression = assignment.getRExpression();
    if (expression == null) return false;
    PsiClass containingClass = field.getContainingClass();
    if (containingClass == null) return false;

    final Ref<Boolean> result = new Ref<>(Boolean.TRUE);
    final List<PsiAssignmentExpression> totalUsages = new ArrayList<>();
    containingClass.accept(new JavaRecursiveElementVisitor() {
      private PsiCodeBlock currentInitializingBlock; //ctr or class initializer

      @Override
      public void visitCodeBlock(PsiCodeBlock block) {
        PsiElement parent = block.getParent();
        if (parent instanceof PsiClassInitializer || parent instanceof PsiMethod && ((PsiMethod)parent).isConstructor()) {
          currentInitializingBlock = block;
          super.visitCodeBlock(block);
          currentInitializingBlock = null;
        }
        else {
          super.visitCodeBlock(block);
        }
      }

      @Override
      public void visitReferenceExpression(PsiReferenceExpression reference) {
        if (!result.get().booleanValue()) return;
        super.visitReferenceExpression(reference);
        if (!PsiUtil.isOnAssignmentLeftHand(reference)) return;
        PsiElement resolved = reference.resolve();
        if (resolved != field) return;
        PsiAssignmentExpression assignmentExpression = PsiTreeUtil.getParentOfType(reference, PsiAssignmentExpression.class);
        PsiExpression rValue = assignmentExpression.getRExpression();
        if (currentInitializingBlock != null) {
          // ignore usages other than initializing
          if (rValue == null || !PsiEquivalenceUtil.areElementsEquivalent(rValue, expression)) {
            result.set(Boolean.FALSE);
          }
          initializingAssignments.add(assignmentExpression);
        }
        totalUsages.add(assignment);
      }
    });
    // the only assignment is OK
    if (totalUsages.size() == 1 && initializingAssignments.isEmpty()) {
      initializingAssignments.addAll(totalUsages);
      return true;
    }
    return result.get().booleanValue();
  }

  private static PsiField getAssignedField(final PsiAssignmentExpression assignment) {
    PsiExpression lExpression = PsiUtil.skipParenthesizedExprDown(assignment.getLExpression());
    if (!(lExpression instanceof PsiReferenceExpression)) return null;
    PsiElement resolved = ((PsiReferenceExpression)lExpression).resolve();
    if (!(resolved instanceof PsiField)) return null;
    return (PsiField)resolved;
  }

  private static PsiAssignmentExpression getAssignmentUnderCaret(final Editor editor, final PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null || element instanceof PsiCompiledElement) return null;
    return PsiTreeUtil.getParentOfType(element, PsiAssignmentExpression.class, false, PsiMember.class);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiAssignmentExpression assignment = getAssignmentUnderCaret(editor, file);
    if (assignment == null) return;
    PsiField field = getAssignedField(assignment);
    if (field == null) return;
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;

    List<PsiAssignmentExpression> assignments = new ArrayList<>();
    if (!isInitializedWithSameExpression(field, assignment, assignments)) return;
    PsiExpression initializer = assignment.getRExpression();
    field.setInitializer(initializer);

    for (PsiAssignmentExpression assignmentExpression : assignments) {
      PsiElement statement = assignmentExpression.getParent();
      PsiElement parent = statement.getParent();
      if (parent instanceof PsiIfStatement ||
          parent instanceof PsiWhileStatement ||
          parent instanceof PsiForStatement ||
          parent instanceof PsiForeachStatement) {
        PsiStatement emptyStatement =
          JavaPsiFacade.getInstance(file.getProject()).getElementFactory().createStatementFromText(";", statement);
        statement.replace(emptyStatement);
      }
      else {
        statement.delete();
      }
    }

    TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    HighlightManager.getInstance(project).addOccurrenceHighlights(editor, new PsiElement[]{field.getInitializer()}, attributes, false, null);
  }
}
