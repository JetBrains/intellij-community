// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.java.JavaBundle;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public final class LambdaCanBeMethodCallInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher PREDICATE_TEST = CallMatcher.instanceCall(
    CommonClassNames.JAVA_UTIL_FUNCTION_PREDICATE, "test").parameterTypes("T");
  private static final CallMatcher MATCHER_FIND = CallMatcher.instanceCall("java.util.regex.Matcher", "find").parameterCount(0);
  private static final CallMatcher MATCHER_MATCHES = CallMatcher.instanceCall("java.util.regex.Matcher", "matches").parameterCount(0);
  private static final CallMatcher PATTERN_MATCHER = CallMatcher.instanceCall("java.util.regex.Pattern", "matcher").parameterCount(1);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    boolean java11 = PsiUtil.isLanguageLevel11OrHigher(holder.getFile());
    return new JavaElementVisitor() {
      @Override
      public void visitLambdaExpression(@NotNull PsiLambdaExpression lambda) {
        super.visitLambdaExpression(lambda);
        PsiElement body = lambda.getBody();
        if (body == null) return;
        PsiType type = lambda.getFunctionalInterfaceType();
        if (!(type instanceof PsiClassType)) return;
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(lambda.getParent());
        if(parent instanceof PsiTypeCastExpression &&
           InheritanceUtil.isInheritor(((PsiTypeCastExpression)parent).getType(), CommonClassNames.JAVA_IO_SERIALIZABLE)) return;
        PsiExpression expression = PsiUtil.skipParenthesizedExprDown(LambdaUtil.extractSingleExpressionFromBody(body));
        if (expression == null) return;
        PsiParameter[] parameters = lambda.getParameterList().getParameters();
        if (parameters.length == 1) {
          PsiParameter parameter = parameters[0];
          if (ExpressionUtils.isReferenceTo(expression, parameter)) {
            handleFunctionIdentity(lambda, (PsiClassType)type);
          }
          if (expression instanceof PsiMethodCallExpression call) {
            PsiClass aClass = ((PsiClassType)type).resolve();
            if (aClass != null && CommonClassNames.JAVA_UTIL_FUNCTION_PREDICATE.equals(aClass.getQualifiedName())) {
              handlePredicateIsEqual(lambda, parameter, call);
              handlePatternAsPredicate(lambda, parameter, call);
            }
          }
          handlePatternNegate(lambda, parameter, expression);
        }
      }

      private void handlePatternNegate(PsiLambdaExpression lambda, PsiParameter parameter, PsiExpression expression) {
        if (!BoolUtils.isNegation(expression)) return;
        PsiMethodCallExpression negated = ObjectUtils.tryCast(BoolUtils.getNegated(expression), PsiMethodCallExpression.class);
        if (!PREDICATE_TEST.test(negated)) return;
        if (!ExpressionUtils.isReferenceTo(negated.getArgumentList().getExpressions()[0], parameter)) return;

        PsiExpression qualifier = negated.getMethodExpression().getQualifierExpression();
        if (!ExpressionUtils.isSafelyRecomputableExpression(qualifier) || ExpressionUtils.isReferenceTo(qualifier, parameter)) return;

        PsiType lambdaType = ExpectedTypeUtils.findExpectedType(lambda, false);
        if (lambdaType == null || qualifier.getType() == null || !lambdaType.isAssignableFrom(qualifier.getType())) return;

        registerProblem(lambda, "Pattern.negate()",
                        ParenthesesUtils.getText(qualifier, PsiPrecedenceUtil.METHOD_CALL_PRECEDENCE) + ".negate()");
      }

      private void handleFunctionIdentity(PsiLambdaExpression lambda, PsiClassType type) {
        PsiClass aClass = type.resolve();
        if (aClass == null || !CommonClassNames.JAVA_UTIL_FUNCTION_FUNCTION.equals(aClass.getQualifiedName())) return;
        PsiType[] typeParameters = type.getParameters();
        if (typeParameters.length != 2 || !typeParameters[1].isAssignableFrom(typeParameters[0])) return;
        @NlsSafe String replacement = CommonClassNames.JAVA_UTIL_FUNCTION_FUNCTION + ".identity()";
        if (!LambdaUtil.isSafeLambdaReplacement(lambda, replacement)) return;
        registerProblem(lambda, "Function.identity()", replacement);
      }

      private void handlePatternAsPredicate(PsiLambdaExpression lambda, PsiParameter parameter, PsiMethodCallExpression call) {
        if (!MATCHER_FIND.test(call) && (!java11 || !MATCHER_MATCHES.test(call))) return;
        PsiMethodCallExpression matcherCall = MethodCallUtils.getQualifierMethodCall(call);
        if (!PATTERN_MATCHER.test(matcherCall)) return;
        PsiExpression matcherArg = matcherCall.getArgumentList().getExpressions()[0];
        if (!ExpressionUtils.isReferenceTo(matcherArg, parameter)) return;
        PsiExpression pattern = matcherCall.getMethodExpression().getQualifierExpression();
        if (pattern == null || !LambdaCanBeMethodReferenceInspection.checkQualifier(pattern)) return;
        String methodName = "find".equalsIgnoreCase(call.getMethodExpression().getReferenceName()) ? "asPredicate" : "asMatchPredicate";
        registerProblem(lambda, "Pattern." + methodName + "()",
                        ParenthesesUtils.getText(pattern, ParenthesesUtils.POSTFIX_PRECEDENCE) + "." + methodName + "()");
      }

      private void handlePredicateIsEqual(PsiLambdaExpression lambda, PsiParameter parameter, PsiMethodCallExpression call) {
        if (MethodCallUtils.isCallToStaticMethod(call, "java.util.Objects", "equals", 2)) {
          PsiExpression[] args = call.getArgumentList().getExpressions();
          if (args.length == 2) {
            PsiExpression comparedWith;
            if (ExpressionUtils.isReferenceTo(args[0], parameter)) {
              comparedWith = args[1];
            }
            else if (ExpressionUtils.isReferenceTo(args[1], parameter)) {
              comparedWith = args[0];
            }
            else return;
            if (LambdaCanBeMethodReferenceInspection.checkQualifier(comparedWith)) {
              registerProblem(lambda, "Predicate.isEqual()",
                              CommonClassNames.JAVA_UTIL_FUNCTION_PREDICATE + ".isEqual(" + comparedWith.getText() + ")");
            }
          }
        }
      }

      private void registerProblem(PsiLambdaExpression lambda, @NlsSafe String displayReplacement, @NlsSafe String replacement) {
        holder.registerProblem(lambda, JavaAnalysisBundle.message("inspection.can.be.replaced.with.message", displayReplacement),
                               new ReplaceWithFunctionCallFix(replacement, displayReplacement));
      }
    };
  }

  static final class ReplaceWithFunctionCallFix extends PsiUpdateModCommandQuickFix {
    private final String myDisplayReplacement;
    private final String myReplacement;

    ReplaceWithFunctionCallFix(String replacement, String displayReplacement) {
      myReplacement = replacement;
      myDisplayReplacement = displayReplacement;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return JavaBundle.message("inspection.lambda.to.method.call.fix.name", myDisplayReplacement);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.lambda.to.method.call.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiLambdaExpression)) return;
      PsiElement result = new CommentTracker().replaceAndRestoreComments(element, myReplacement);
      CodeStyleManager.getInstance(project).reformat(JavaCodeStyleManager.getInstance(project).shortenClassReferences(result));
    }
  }
}
