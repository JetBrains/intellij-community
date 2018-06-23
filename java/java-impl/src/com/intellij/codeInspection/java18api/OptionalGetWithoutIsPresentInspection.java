// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.java18api;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.DfaFactType;
import com.intellij.codeInspection.dataFlow.DfaOptionalSupport;
import com.intellij.codeInspection.util.LambdaGenerationUtil;
import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class OptionalGetWithoutIsPresentInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        PsiElement nameElement = call.getMethodExpression().getReferenceNameElement();
        if (nameElement == null) return;
        String methodName = nameElement.getText();
        PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(call.getMethodExpression().getQualifierExpression());
        if (qualifier == null) return;
        PsiClass optionalClass = PsiUtil.resolveClassInClassTypeOnly(qualifier.getType());
        if (optionalClass == null) return;
        if (DfaOptionalSupport.isOptionalGetMethodName(methodName) &&
            call.getArgumentList().isEmpty() &&
            TypeUtils.isOptional(optionalClass)) {
          CommonDataflow.DataflowResult result = CommonDataflow.getDataflowResult(qualifier);
          if (result != null &&
              result.expressionWasAnalyzed(qualifier) &&
              result.getExpressionFact(qualifier, DfaFactType.OPTIONAL_PRESENCE) == null &&
              !isPresentCallWithSameQualifierExists(qualifier)) {
            holder.registerProblem(nameElement,
                                   InspectionsBundle.message("inspection.optional.get.without.is.present.message", optionalClass.getName()),
                                   tryCreateFix(call));
          }
        }
      }

      public boolean isPresentCallWithSameQualifierExists(PsiExpression qualifier) {
        // Conservatively skip the results of method calls if there's an isPresent() call with the same qualifier in the method
        if (qualifier instanceof PsiMethodCallExpression) {
          PsiElement context = PsiTreeUtil.getParentOfType(qualifier, PsiMember.class, PsiLambdaExpression.class);
          if (context != null) {
            return !PsiTreeUtil.processElements(context, e -> {
              if (e == qualifier || !(e instanceof PsiMethodCallExpression)) return true;
              PsiMethodCallExpression call = (PsiMethodCallExpression)e;
              if (!"isPresent".equals(call.getMethodExpression().getReferenceName()) || !call.getArgumentList().isEmpty()) return true;
              PsiExpression isPresentQualifier = call.getMethodExpression().getQualifierExpression();
              return isPresentQualifier == null || !PsiEquivalenceUtil.areElementsEquivalent(qualifier, isPresentQualifier);
            });
          }
        }
        return false;
      }
    };
  }

  private static LocalQuickFix tryCreateFix(PsiMethodCallExpression call) {
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if (qualifier == null) return null;
    PsiClass optionalClass = PsiUtil.resolveClassInClassTypeOnly(qualifier.getType());
    if (optionalClass == null || !CommonClassNames.JAVA_UTIL_OPTIONAL.equals(optionalClass.getQualifiedName())) return null;
    PsiType optionalElementType = OptionalUtil.getOptionalElementType(qualifier.getType());
    if (optionalElementType == null) return null;
    PsiMethodCallExpression nextCall = ExpressionUtils.getCallForQualifier(call);
    if (nextCall != null) {
      if (optionalClass.equals(PsiUtil.resolveClassInClassTypeOnly(nextCall.getType()))) {
        if (!LambdaGenerationUtil.canBeUncheckedLambda(nextCall)) {
          // Probably qualifier accesses non-final vars or throws exception: we will replace qualifier, so this is not a problem
          PsiMethodCallExpression copy = (PsiMethodCallExpression)nextCall.copy();
          PsiExpression copyQualifier = Objects.requireNonNull(copy.getMethodExpression().getQualifierExpression());
          try {
            copyQualifier.replace(JavaPsiFacade.getElementFactory(call.getProject())
                                               .createExpressionFromText("((" + optionalElementType.getCanonicalText() + ")null)",
                                                                         copyQualifier));
          }
          catch (IncorrectOperationException e) {
            return null;
          }
          if (!LambdaGenerationUtil.canBeUncheckedLambda(copy)) {
            return null;
          }
        }
        return new UseFlatMapFix();
      }
    }
    return null;
  }

  private static class UseFlatMapFix implements LocalQuickFix {
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return "Use 'flatMap'";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiMethodCallExpression.class);
      if (call == null) return;
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if (qualifier == null) return;
      PsiType elementType = OptionalUtil.getOptionalElementType(qualifier.getType());
      PsiMethodCallExpression nextCall = ExpressionUtils.getCallForQualifier(call);
      if (nextCall == null) return;
      JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(project);
      SuggestedNameInfo info = manager.suggestVariableName(VariableKind.PARAMETER, null, qualifier, elementType, true);
      String name = info.names.length == 0 ? "value" : info.names[0];
      name = manager.suggestUniqueVariableName(name, call, true);
      CommentTracker ct = new CommentTracker();
      PsiReferenceExpression methodExpression = nextCall.getMethodExpression();
      ct.markRangeUnchanged(Objects.requireNonNull(methodExpression.getQualifierExpression()).getNextSibling(),
                            methodExpression.getLastChild());
      ct.markRangeUnchanged(methodExpression.getNextSibling(), nextCall.getLastChild());
      PsiMethodCallExpression newNextCall = (PsiMethodCallExpression)nextCall.copy();
      PsiExpression newQualifier = Objects.requireNonNull(newNextCall.getMethodExpression().getQualifierExpression());
      newQualifier.replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(name, newNextCall));
      String lambda = name + "->" + newNextCall.getText();
      String replacement = ct.text(qualifier) + ".flatMap(" + lambda + ")";
      PsiMethodCallExpression result = (PsiMethodCallExpression)ct.replaceAndRestoreComments(nextCall, replacement);
      LambdaCanBeMethodReferenceInspection.replaceLambdaWithMethodReference(
        (PsiLambdaExpression)result.getArgumentList().getExpressions()[0]);
    }
  }
}
