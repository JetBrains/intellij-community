/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ConstructionUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author cdr
 * @author Tagir Valeev
 */
public class MoveFieldAssignmentToInitializerInspection extends BaseJavaBatchLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitAssignmentExpression(PsiAssignmentExpression assignment) {
        PsiElement parent = assignment.getParent();
        if (!(parent instanceof PsiExpressionStatement)) return;
        PsiField field = getAssignedField(assignment);
        if (field == null || field.hasInitializer()) return;
        PsiClass psiClass = field.getContainingClass();

        if (psiClass == null || psiClass.isInterface()) return;
        if (psiClass.getContainingFile() != holder.getFile()) return;
        PsiModifierListOwner ctrOrInitializer = enclosingMethodOrClassInitializer(assignment, field);
        if (ctrOrInitializer == null) return;
        if (ctrOrInitializer.hasModifierProperty(PsiModifier.STATIC) != field.hasModifierProperty(PsiModifier.STATIC)) return;

        if (!isValidAsFieldInitializer(assignment.getRExpression(), ctrOrInitializer, field)) return;
        if (!isInitializedWithSameExpression(field, assignment, new ArrayList<>())) return;

        boolean shouldWarn = shouldWarn(ctrOrInitializer, field, assignment);
        if (!shouldWarn && !isOnTheFly) return;
        TextRange range;
        if (shouldWarn && !InspectionProjectProfileManager.isInformationLevel(getShortName(), assignment)) {
          PsiExpression lValue = assignment.getLExpression();
          range = new TextRange(0, lValue.getTextLength()).shiftRight(lValue.getStartOffsetInParent());
        } else {
          range = new TextRange(0, assignment.getTextLength());
        }
        holder.registerProblem(assignment, CodeInsightBundle.message("intention.move.field.assignment.to.declaration"),
                               shouldWarn ? ProblemHighlightType.GENERIC_ERROR_OR_WARNING : ProblemHighlightType.INFORMATION,
                               range, new MoveFieldAssignmentToInitializerFix());
      }
    };
  }

  boolean shouldWarn(PsiModifierListOwner ctrOrInitializer, PsiField field, PsiAssignmentExpression assignment) {
    if (!(ctrOrInitializer instanceof PsiClassInitializer)) return false;
    PsiExpressionStatement statement = ObjectUtils.tryCast(assignment.getParent(), PsiExpressionStatement.class);
    if (statement == null) return false;
    PsiCodeBlock codeBlock = ObjectUtils.tryCast(statement.getParent(), PsiCodeBlock.class);
    if (codeBlock == null) return false;
    if (codeBlock.getParent() != ctrOrInitializer) return false;
    if (!ReferencesSearch.search(field, new LocalSearchScope(ctrOrInitializer)).forEach(ref -> {
      return PsiTreeUtil.isAncestor(assignment, ref.getElement(), true);
    })) {
      return false;
    }
    // If it's not the first statement in the initializer, allow some more (likely unrelated) assignments only
    if (StreamEx.of(codeBlock.getStatements()).takeWhile(st -> statement != st).allMatch(st -> ExpressionUtils.getAssignment(st) != null)) {
      return true;
    }
    // Or allow simple initializers
    PsiExpression value = assignment.getRExpression();
    return ExpressionUtils.isSimpleExpression(value) || ConstructionUtils.isEmptyCollectionInitializer(value);
  }

  private static boolean isValidAsFieldInitializer(final PsiExpression initializer,
                                                   final PsiModifierListOwner ctrOrInitializer,
                                                   PsiField field) {
    if (initializer == null) return false;
    if (!ExceptionUtil.getThrownCheckedExceptions(initializer).isEmpty()) return false;
    final Ref<Boolean> result = new Ref<>(Boolean.TRUE);
    initializer.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        PsiElement resolved = expression.resolve();
        if (resolved == null) return;
        if (resolved == field) {
          result.set(Boolean.FALSE);
          return;
        }
        if (PsiTreeUtil.isAncestor(ctrOrInitializer, resolved, false) && !PsiTreeUtil.isAncestor(initializer, resolved, false)) {
          // resolved somewhere inside constructor but outside initializer
          result.set(Boolean.FALSE);
          return;
        }
        if (resolved instanceof PsiMember && ((PsiMember)resolved).getContainingClass() == field.getContainingClass()) {
          if (field.hasModifierProperty(PsiModifier.STATIC) ||
            !((PsiMember)resolved).hasModifierProperty(PsiModifier.STATIC)) {
            // refers to another field/method of the same class (except referring from non-static member to static)
            result.set(Boolean.FALSE);
          }
        }
      }
    });
    return result.get().booleanValue();
  }

  static PsiModifierListOwner enclosingMethodOrClassInitializer(final PsiAssignmentExpression assignment, final PsiField field) {
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
    containingClass.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression reference) {
        if (!result.get().booleanValue()) return;
        super.visitReferenceExpression(reference);
        if (!PsiUtil.isOnAssignmentLeftHand(reference)) return;
        PsiElement resolved = reference.resolve();
        if (resolved != field) return;
        PsiAssignmentExpression assignmentExpression = PsiTreeUtil.getParentOfType(reference, PsiAssignmentExpression.class);
        if (assignmentExpression == null) return;
        PsiExpression rValue = assignmentExpression.getRExpression();
        PsiMember member = PsiTreeUtil.getParentOfType(assignmentExpression, PsiMember.class);
        if (member instanceof PsiClassInitializer || member instanceof PsiMethod && ((PsiMethod)member).isConstructor()) {
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

  private static class MoveFieldAssignmentToInitializerFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return CodeInsightBundle.message("intention.move.field.assignment.to.declaration");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiAssignmentExpression assignment = ObjectUtils.tryCast(descriptor.getStartElement(), PsiAssignmentExpression.class);
      if (assignment == null) return;
      PsiField field = getAssignedField(assignment);
      if (field == null) return;

      List<PsiAssignmentExpression> assignments = new ArrayList<>();
      if (!isInitializedWithSameExpression(field, assignment, assignments)) return;

      PsiElement prev = PsiTreeUtil.skipWhitespacesAndCommentsBackward(assignment.getParent());
      String comments = prev == null ? null : CommentTracker.commentsBetween(prev, assignment);

      PsiExpression initializer = assignment.getRExpression();
      field.setInitializer(initializer);

      PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      if (comments != null) {
        PsiCodeBlock block = factory.createCodeBlockFromText("{" + comments + "}", initializer);
        for(PsiElement child : block.getChildren()) {
          if(child instanceof PsiComment || child instanceof PsiWhiteSpace) {
            field.getParent().addBefore(child, field);
          }
        }
      }

      PsiModifierListOwner owner = enclosingMethodOrClassInitializer(assignment, field);

      for (PsiAssignmentExpression assignmentExpression : assignments) {
        PsiElement statement = assignmentExpression.getParent();
        PsiElement parent = statement.getParent();
        if (parent instanceof PsiIfStatement ||
            parent instanceof PsiWhileStatement ||
            parent instanceof PsiForStatement ||
            parent instanceof PsiForeachStatement) {
          PsiStatement emptyStatement = factory.createStatementFromText(";", statement);
          statement.replace(emptyStatement);
        }
        else {
          statement.delete();
        }
      }

      // Delete empty initializer left after fix
      if (owner instanceof PsiClassInitializer) {
        PsiCodeBlock body = ((PsiClassInitializer)owner).getBody();
        if(body.getStatements().length == 0 && Arrays.stream(body.getChildren()).noneMatch(PsiComment.class::isInstance)) {
          owner.delete();
        }
      }
    }
  }
}
