// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.editorActions.DeclarationJoinLinesHandler;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Function;

import static com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
import static com.intellij.codeInspection.ProblemHighlightType.INFORMATION;
import static com.intellij.psi.util.PsiTreeUtil.getNextSiblingOfType;
import static com.intellij.psi.util.PsiTreeUtil.skipWhitespacesAndCommentsForward;
import static com.siyeh.ig.psiutils.VariableAccessUtils.variableIsUsed;

/**
 * It's called "Java" inspection because the name without "Java" already exists.
 */
public class JoinDeclarationAndAssignmentJavaInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitAssignmentExpression(@NotNull PsiAssignmentExpression assignmentExpression) {
        super.visitAssignmentExpression(assignmentExpression);

        visitLocation(assignmentExpression);
      }

      @Override
      public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
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
          String message = JavaBundle.message("inspection.join.declaration.and.assignment.message", context.myName);
          JoinDeclarationAndAssignmentFix fix = new JoinDeclarationAndAssignmentFix();

          if (isOnTheFly && (context.myIsUpdate || isInformationLevel(location))) {
            ProblemHighlightType highlightType = context.myIsUpdate ? INFORMATION : GENERIC_ERROR_OR_WARNING;
            holder.registerProblem(location, message, highlightType, fix);
          }
          else if (location == assignment && !context.myIsUpdate) {
            holder.registerProblem(assignment.getLExpression(), message, fix);
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
      if (element instanceof PsiAssignmentExpression assignment) {
        return getContext(findVariable(assignment), assignment);
      }
      if (element instanceof PsiLocalVariable variable) {
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
      if (rExpression != null) {
        for (PsiLocalVariable aVar = variable; aVar != null; aVar = getNextSiblingOfType(aVar, PsiLocalVariable.class)) {
          if (variableIsUsed(aVar, rExpression)) {
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
    if (lExpression instanceof PsiReferenceExpression reference &&
        !reference.isQualified() && // optimization: locals aren't qualified
        reference.resolve() instanceof PsiLocalVariable variable) {
      PsiDeclarationStatement declarationStatement = ObjectUtils.tryCast(variable.getParent(), PsiDeclarationStatement.class);
      PsiExpressionStatement expressionStatement = ObjectUtils.tryCast(assignmentExpression.getParent(), PsiExpressionStatement.class);
      if (declarationStatement != null && declarationStatement.getParent() instanceof PsiCodeBlock &&
          expressionStatement != null && expressionStatement.getParent() == declarationStatement.getParent()) {
        return findOccurrence(expressionStatement, variable,
                              PsiTreeUtil::skipWhitespacesAndCommentsBackward,
                              (candidate, unused) -> candidate == declarationStatement ? variable : null);
      }
    }
    return null;
  }

  @Nullable
  private static PsiAssignmentExpression findAssignment(@NotNull PsiLocalVariable variable) {
    return findOccurrence(variable.getParent(), variable,
                          PsiTreeUtil::skipWhitespacesAndCommentsForward,
                          JoinDeclarationAndAssignmentJavaInspection::findAssignment);
  }

  @Nullable
  private static <T> T findOccurrence(@Nullable PsiElement start,
                                      @NotNull PsiLocalVariable variable,
                                      @NotNull Function<? super PsiElement, ? extends PsiElement> advance,
                                      @NotNull BiFunction<? super PsiElement, ? super PsiLocalVariable, ? extends T> search) {
    PsiElement candidate = advance.apply(start);
    T result = search.apply(candidate, variable);
    if (result != null) {
      return result;
    }
    if (canRemoveDeclaration(variable)) {
      for (; candidate != null; candidate = advance.apply(candidate)) {
        result = search.apply(candidate, variable);
        if (result != null) {
          return result;
        }
        if (variableIsUsed(variable, candidate)) {
          break;
        }
      }
    }
    return null;
  }

  @Contract("null,_ -> null")
  @Nullable
  private static PsiAssignmentExpression findAssignment(@Nullable PsiElement candidate, @NotNull PsiVariable variable) {
    if (candidate instanceof PsiExpressionStatement exprStatement &&
        exprStatement.getExpression() instanceof PsiAssignmentExpression assignmentExpression &&
        ExpressionUtils.isReferenceTo(assignmentExpression.getLExpression(), variable)) {
      return assignmentExpression;
    }
    return null;
  }

  @Nullable
  private static PsiAssignmentExpression findNextAssignment(@Nullable PsiElement element, @NotNull PsiVariable variable) {
    PsiElement candidate = skipWhitespacesAndCommentsForward(element);
    return findAssignment(candidate, variable);
  }

  private static boolean canRemoveDeclaration(@NotNull PsiVariable variable) {
    PsiExpression initializer = variable.getInitializer();
    return initializer == null || !SideEffectChecker.checkSideEffects(initializer, null);
  }


  private static class JoinDeclarationAndAssignmentFix implements LocalQuickFix {
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.join.declaration.and.assignment.fix.family.name");
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
          String textAfter = PsiExpressionTrimRenderer.render(initializer) + ";<br>" +
                             variable.getTypeElement().getText() + ' ' + variable.getName();
          if (!RemoveInitializerFix.sideEffectAwareRemove(project, initializer, initializer, variable, textAfter)) return;
        }

        if (!FileModificationService.getInstance().prepareFileForWrite(assignmentExpression.getContainingFile())) return;
        WriteAction.run(() -> DeclarationJoinLinesHandler.joinDeclarationAndAssignment(context.myVariable, context.myAssignment));
      }
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      Context context = getContext(previewDescriptor.getPsiElement());
      if (context != null) {
        DeclarationJoinLinesHandler.joinDeclarationAndAssignment(context.myVariable, context.myAssignment);
        return IntentionPreviewInfo.DIFF;
      } else {
        return IntentionPreviewInfo.EMPTY;
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
      myIsUpdate = !JavaTokenType.EQ.equals(myAssignment.getOperationTokenType()) ||
                   findNextAssignment(myAssignment.getParent(), myVariable) != null;
    }
  }
}
