// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.editorActions.DeclarationJoinLinesHandler;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
import static com.intellij.codeInspection.ProblemHighlightType.INFORMATION;

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

        visitLocation(assignmentExpression);
      }

      @Override
      public void visitLocalVariable(PsiLocalVariable variable) {
        super.visitLocalVariable(variable);

        // At the "information" level only bare minimal set of elements are visited when the file is being edited.
        // If the caret is at the variable declaration the assignment expression can be not visited so we do the same check here.
        if (isOnTheFly && isInformationLevel(variable)) {
          visitLocation(variable);
        }
      }

      public void visitLocation(@Nullable PsiElement location) {
        Context context = getContext(location);
        if (context != null) {
          PsiLocalVariable variable = context.myVariable;
          PsiAssignmentExpression assignment = context.myAssignment;
          assert location == variable || location == assignment : "context location";
          String message = InspectionsBundle.message("inspection.join.declaration.and.assignment.message", context.myName);
          JoinDeclarationAndAssignmentFix fix = new JoinDeclarationAndAssignmentFix();

          ProblemHighlightType highlightType = context.myIsUpdate ? INFORMATION : GENERIC_ERROR_OR_WARNING;
          if (isOnTheFly && (context.myIsUpdate || isInformationLevel(location))) {
            holder.registerProblem(location, message, highlightType, fix);
          }
          else if (location == assignment) {
            holder.registerProblem(assignment.getLExpression(), message, highlightType, fix);
          }
        }
      }

      private boolean isInformationLevel(@NotNull PsiElement element) {
        return InspectionProjectProfileManager.isInformationLevel(getShortName(), element);
      }
    };
  }

  @Contract("null -> null")
  @Nullable
  private static Context getContext(@Nullable PsiElement element) {
    if (element != null) {
      if (!(element instanceof PsiAssignmentExpression) && !(element instanceof PsiLocalVariable)) {
        element = element.getParent();
      }
      if (element instanceof PsiAssignmentExpression) {
        PsiAssignmentExpression assignment = (PsiAssignmentExpression)element;
        return getContext(findVariable(assignment), assignment);
      }
      if (element instanceof PsiLocalVariable) {
        PsiLocalVariable variable = (PsiLocalVariable)element;
        return getContext(variable, findAssignment(variable));
      }
    }
    return null;
  }

  @Contract("null,_ -> null; _,null -> null")
  @Nullable
  private static Context getContext(@Nullable PsiLocalVariable variable, @Nullable PsiAssignmentExpression assignment) {
    if (variable != null && assignment != null) {
      String variableName = variable.getName();
      PsiExpression rExpression = assignment.getRExpression();
      if (variableName != null && rExpression != null) {
        for (PsiLocalVariable aVar = variable; aVar != null; aVar = PsiTreeUtil.getNextSiblingOfType(aVar, PsiLocalVariable.class)) {
          if (VariableAccessUtils.variableIsUsed(aVar, rExpression)) {
            return null;
          }
        }
        return new Context(variable, assignment, variableName);
      }
    }
    return null;
  }

  @Nullable
  private static PsiLocalVariable findVariable(@NotNull PsiAssignmentExpression assignmentExpression) {
    PsiExpression lExpression = PsiUtil.skipParenthesizedExprDown(assignmentExpression.getLExpression());
    if (lExpression instanceof PsiReferenceExpression) {
      PsiReferenceExpression reference = (PsiReferenceExpression)lExpression;
      if (!reference.isQualified()) { // optimization: locals aren't qualified
        PsiExpressionStatement statement = ObjectUtils.tryCast(assignmentExpression.getParent(), PsiExpressionStatement.class);
        PsiElement candidate = PsiTreeUtil.skipWhitespacesAndCommentsBackward(statement);
        if (candidate instanceof PsiDeclarationStatement) {
          PsiElement resolved = reference.resolve();
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
        PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
        PsiExpression lExpression = PsiUtil.skipParenthesizedExprDown(assignmentExpression.getLExpression());
        if (lExpression instanceof PsiReferenceExpression) {
          PsiReferenceExpression reference = (PsiReferenceExpression)lExpression;
          if (!reference.isQualified() && // optimization: locals aren't qualified
              reference.isReferenceTo(variable)) {
            return assignmentExpression;
          }
        }
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
      Context context = getContext(descriptor.getPsiElement());
      if (context != null) {
        PsiLocalVariable variable = context.myVariable;
        PsiAssignmentExpression assignmentExpression = context.myAssignment;
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

  private static class Context {
    @NotNull final PsiLocalVariable myVariable;
    @NotNull final PsiAssignmentExpression myAssignment;
    @NotNull final String myName;
    final boolean myIsUpdate;

    Context(@NotNull PsiLocalVariable variable, @NotNull PsiAssignmentExpression assignment, @NotNull String name) {
      myAssignment = assignment;
      myVariable = variable;
      myName = name;
      myIsUpdate = !JavaTokenType.EQ.equals(myAssignment.getOperationTokenType());
    }
  }
}
