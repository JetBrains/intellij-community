// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.editorActions.DeclarationJoinLinesHandler;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * It's called "Java" inspection because the name without "Java" already exists.
 *
 * @author Pavel.Dolgov
 */
public class JoinDeclarationAndAssignmentJavaInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitAssignmentExpression(PsiAssignmentExpression assignmentExpression) {
        super.visitAssignmentExpression(assignmentExpression);

        final Pair.NonNull<PsiLocalVariable, PsiAssignmentExpression> pair = getPair(assignmentExpression);
        if (pair != null) {
          String name = pair.getFirst().getName();
          if (name != null) {
            String message = InspectionsBundle.message("inspection.join.declaration.and.assignment.message", name);
            JoinDeclarationAndAssignmentFix fix = new JoinDeclarationAndAssignmentFix();
            holder.registerProblem(pair.getFirst(), message, fix);
            holder.registerProblem(pair.getSecond(), message, fix);
          }
        }
      }
    };
  }

  @Nullable
  private static Pair.NonNull<PsiLocalVariable, PsiAssignmentExpression> getPair(@Nullable PsiElement element) {
    PsiLocalVariable variable = null;
    PsiAssignmentExpression assignmentExpression = null;
    if (element instanceof PsiAssignmentExpression) {
      assignmentExpression = (PsiAssignmentExpression)element;
      variable = findVariable(assignmentExpression);
    }
    else if (element instanceof PsiLocalVariable) {
      variable = (PsiLocalVariable)element;
      assignmentExpression = findAssignment(variable);
    }
    if (variable != null && assignmentExpression != null) {
      PsiExpression rExpression = assignmentExpression.getRExpression();
      if (rExpression != null &&
          ReferencesSearch.search(variable, new LocalSearchScope(rExpression), false).findFirst() == null) {
        return Pair.createNonNull(variable, assignmentExpression);
      }
    }
    return null;
  }

  @Nullable
  private static PsiLocalVariable findVariable(@NotNull PsiAssignmentExpression assignmentExpression) {
    PsiElement assignmentParent = assignmentExpression.getParent();
    if (assignmentParent instanceof PsiExpressionStatement) {
      PsiElement candidate = PsiTreeUtil.skipWhitespacesAndCommentsBackward(assignmentParent);
      if (candidate instanceof PsiDeclarationStatement) {
        PsiExpression lExpression = assignmentExpression.getLExpression();
        if (lExpression instanceof PsiReferenceExpression) {
          PsiElement resolved = ((PsiReferenceExpression)lExpression).resolve();
          if (resolved instanceof PsiLocalVariable && resolved.getParent() == candidate) {
            return (PsiLocalVariable)resolved;
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static PsiAssignmentExpression findAssignment(@NotNull PsiVariable variable) {
    PsiDeclarationStatement statement = ObjectUtils.tryCast(variable.getParent(), PsiDeclarationStatement.class);
    PsiElement candidate = PsiTreeUtil.skipWhitespacesAndCommentsForward(statement);
    if (candidate instanceof PsiExpressionStatement) {
      PsiExpression expression = ((PsiExpressionStatement)candidate).getExpression();
      if (expression instanceof PsiAssignmentExpression) {
        return (PsiAssignmentExpression)expression;
      }
    }
    return null;
  }


  private static class JoinDeclarationAndAssignmentFix implements LocalQuickFix {
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.join.declaration.and.assignment.fix.family.name");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      Pair.NonNull<PsiLocalVariable, PsiAssignmentExpression> pair = getPair(descriptor.getPsiElement());
      if (pair != null) {
        PsiLocalVariable variable = pair.getFirst();
        PsiAssignmentExpression assignmentExpression = pair.getSecond();
        PsiExpression initializer = variable.getInitializer();
        if (initializer != null && assignmentExpression.getOperationTokenType() == JavaTokenType.EQ) {
          RemoveInitializerFix.sideEffectAwareRemove(project, initializer, initializer, variable);
        }

        if (!FileModificationService.getInstance().prepareFileForWrite(assignmentExpression.getContainingFile())) return;
        WriteAction.run(() -> {
          PsiExpression initializerExpression = DeclarationJoinLinesHandler.getInitializerExpression(variable, assignmentExpression);
          if (initializerExpression != null) {
            variable.setInitializer(initializerExpression);
            new CommentTracker().deleteAndRestoreComments(assignmentExpression);
          }
        });
      }
    }
  }
}
