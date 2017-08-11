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

import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * @author Tagir Valeev
 */
public class SimplifyOptionalCallChainsInspection extends BaseJavaBatchLocalInspectionTool {
  private static final CallMatcher OPTIONAL_OR_ELSE =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_OPTIONAL, "orElse").parameterCount(1);
  private static final CallMatcher OPTIONAL_OR_ELSE_GET =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_OPTIONAL, "orElseGet").parameterCount(1);
  private static final CallMatcher OPTIONAL_MAP =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_OPTIONAL, "map").parameterCount(1);
  private static final CallMatcher OPTIONAL_OF_NULLABLE =
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_OPTIONAL, "ofNullable").parameterCount(1);


  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        PsiExpression falseArg = null;
        boolean useOrElseGet = false;
        if (OPTIONAL_OR_ELSE.test(call)) {
          falseArg = call.getArgumentList().getExpressions()[0];
        }
        else if (OPTIONAL_OR_ELSE_GET.test(call)) {
          useOrElseGet = true;
          PsiLambdaExpression lambda = getLambda(call.getArgumentList().getExpressions()[0]);
          if (lambda == null || lambda.getParameterList().getParametersCount() != 0) return;
          falseArg = LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
        }
        if (falseArg == null) return;
        handleMapOrElse(call, useOrElseGet, falseArg);
        handleOfNullableOrElse(call, falseArg);
      }

      private void handleOfNullableOrElse(PsiMethodCallExpression call, PsiExpression falseArg) {
        if (!ExpressionUtils.isNullLiteral(falseArg)) return;
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(call.getParent());
        if (!(parent instanceof PsiExpressionList)) return;
        PsiMethodCallExpression parentCall = ObjectUtils.tryCast(parent.getParent(), PsiMethodCallExpression.class);
        if (!OPTIONAL_OF_NULLABLE.test(parentCall)) return;
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (qualifier == null ||
            !EquivalenceChecker.getCanonicalPsiEquivalence().typesAreEquivalent(qualifier.getType(), parentCall.getType())) {
          return;
        }
        holder.registerProblem(Objects.requireNonNull(parentCall.getMethodExpression().getReferenceNameElement()),
                               "Unnecessary Optional rewrapping",
                               new SimplifyOptionalChainFix(qualifier.getText(), "Unwrap"));
      }

      private void handleMapOrElse(PsiMethodCallExpression call, boolean useOrElseGet, PsiExpression falseArg) {
        PsiMethodCallExpression qualifierCall = MethodCallUtils.getQualifierMethodCall(call);
        if (!OPTIONAL_MAP.test(qualifierCall)) return;
        PsiLambdaExpression lambda = getLambda(qualifierCall.getArgumentList().getExpressions()[0]);
        if (lambda == null) return;
        PsiExpression trueArg = LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
        if (trueArg == null) return;
        PsiParameter[] parameters = lambda.getParameterList().getParameters();
        if (parameters.length != 1) return;
        PsiExpression qualifier = qualifierCall.getMethodExpression().getQualifierExpression();
        if (qualifier == null) return;
        String opt = qualifier.getText();
        PsiParameter parameter = parameters[0];
        String proposed = OptionalUtil.generateOptionalUnwrap(opt, parameter, trueArg, falseArg, call.getType(), useOrElseGet);
        String canonicalOrElse;
        if (useOrElseGet && !ExpressionUtils.isSimpleExpression(falseArg)) {
          canonicalOrElse = ".orElseGet(() -> " + falseArg.getText() + ")";
        }
        else {
          canonicalOrElse = ".orElse(" + falseArg.getText() + ")";
        }
        String canonical = opt + ".map(" + LambdaUtil.createLambda(parameter, trueArg) + ")" + canonicalOrElse;
        if (proposed.length() < canonical.length()) {
          String displayCode;
          if(proposed.equals(opt)) {
            displayCode = "";
          } else if(opt.length() > 10) {
            // should be a parseable expression
            opt = "(($))";
            String template = OptionalUtil.generateOptionalUnwrap(opt, parameter, trueArg, falseArg, call.getType(), useOrElseGet);
            displayCode =
              PsiExpressionTrimRenderer.render(JavaPsiFacade.getElementFactory(holder.getProject()).createExpressionFromText(template, call));
            displayCode = displayCode.replaceFirst(Pattern.quote(opt), "..");
          } else {
            displayCode =
              PsiExpressionTrimRenderer.render(JavaPsiFacade.getElementFactory(holder.getProject()).createExpressionFromText(proposed, call));
          }
          String message = displayCode.isEmpty() ? "Remove redundant steps from optional chain" :
                     "Simplify optional chain to '" + displayCode + "'";
          holder.registerProblem(Objects.requireNonNull(call.getMethodExpression().getReferenceNameElement()),
                                 "Optional chain can be simplified",
                                 new SimplifyOptionalChainFix(proposed, message));
        }
      }
    };
  }

  private static PsiLambdaExpression getLambda(PsiExpression initializer) {
    PsiExpression expression = PsiUtil.skipParenthesizedExprDown(initializer);
    if (expression instanceof PsiLambdaExpression) {
      return (PsiLambdaExpression)expression;
    }
    if (expression instanceof PsiMethodReferenceExpression) {
      return LambdaRefactoringUtil.createLambda((PsiMethodReferenceExpression)expression, true);
    }
    return null;
  }

  private static class SimplifyOptionalChainFix implements LocalQuickFix {
    private final String myReplacement;
    private final String myMessage;

    public SimplifyOptionalChainFix(String replacement, String message) {
      myReplacement = replacement;
      myMessage = message;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return myMessage;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Simplify optional call chain";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiMethodCallExpression.class);
      if (call == null) return;
      PsiExpression replacementExpression = JavaPsiFacade.getElementFactory(project).createExpressionFromText(myReplacement, call);
      PsiElement result = call.replace(replacementExpression);
      LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
      PsiDiamondTypeUtil.removeRedundantTypeArguments(result);
    }
  }
}
