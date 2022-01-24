// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.miscGenerics;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInspection.*;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

import static com.siyeh.ig.callMatcher.CallMatcher.exactInstanceCall;

/**
 * @author ven
 */
public class RedundantArrayForVarargsCallInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new RedundantArrayForVarargVisitor(holder);
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.verbose.or.redundant.code.constructs");
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return "RedundantArrayCreation";
  }

  private static final class RedundantArrayForVarargVisitor extends JavaElementVisitor {
    private static final Logger LOG = Logger.getInstance(RedundantArrayForVarargVisitor.class);

    private static final String[] LOGGER_NAMES = new String[]{"debug", "error", "info", "trace", "warn"};

    private static final CallMatcher LOGGER_MESSAGE_CALL = exactInstanceCall("org.slf4j.Logger", LOGGER_NAMES)
      .parameterTypes(String.class.getName(), "java.lang.Object...");

    private static final LocalQuickFix myQuickFixAction = new MyQuickFix();

    private @NotNull final ProblemsHolder myHolder;

    private RedundantArrayForVarargVisitor(@NotNull final ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitCallExpression(PsiCallExpression expression) {
      super.visitCallExpression(expression);
      checkCall(expression);
    }

    @Override
    public void visitEnumConstant(PsiEnumConstant expression) {
      super.visitEnumConstant(expression);
      checkCall(expression);
    }

    private void checkCall(PsiCall expression) {
      final JavaResolveResult resolveResult = expression.resolveMethodGenerics();
      PsiElement element = resolveResult.getElement();
      final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      if (!(element instanceof PsiMethod)) {
        return;
      }
      PsiMethod method = (PsiMethod)element;
      if (!method.isVarArgs() ||
          AnnotationUtil.isAnnotated(method, CommonClassNames.JAVA_LANG_INVOKE_MH_POLYMORPHIC, 0)) {
        return;
      }
      PsiParameter[] parameters = method.getParameterList().getParameters();
      PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      PsiExpression[] args = argumentList.getExpressions();
      if (parameters.length != args.length) {
        return;
      }
      PsiExpression lastArg = PsiUtil.skipParenthesizedExprDown(args[args.length - 1]);
      PsiParameter lastParameter = parameters[args.length - 1];
      if (!lastParameter.isVarArgs()) {
        return;
      }
      PsiType lastParamType = lastParameter.getType();
      LOG.assertTrue(lastParamType instanceof PsiEllipsisType, lastParamType);
      if (!(lastArg instanceof PsiNewExpression)) {
        return;
      }
      final PsiType substitutedLastParamType = substitutor.substitute(((PsiEllipsisType)lastParamType).toArrayType());
      final PsiType lastArgType = lastArg.getType();
      if (lastArgType == null || !lastArgType.equals(substitutedLastParamType) &&
                                 !lastArgType.equals(TypeConversionUtil.erasure(substitutedLastParamType))) {
        return;
      }
      PsiExpression[] initializers = getInitializers((PsiNewExpression)lastArg);
      if (initializers == null) {
        return;
      }
      if (Arrays.stream(initializers).anyMatch(expr -> expr instanceof PsiArrayInitializerExpression)) {
        return;
      }
      if (!isSafeToFlatten(expression, method, initializers)) {
        return;
      }
      final String message = JavaBundle.message("inspection.redundant.array.creation.for.varargs.call.descriptor");
      myHolder.registerProblem(lastArg, message, myQuickFixAction);
    }

    private static boolean isSafeToFlatten(@NotNull final PsiCall callExpression,
                                           @NotNull final PsiMethod oldRefMethod,
                                           @NotNull final PsiExpression @NotNull [] arrayElements) {
      if (callExpression instanceof PsiExpression && LOGGER_MESSAGE_CALL.matches((PsiExpression)callExpression)) {
        return true;
      }
      if (arrayElements.length == 1) {
        PsiType type = arrayElements[0].getType();
        // change foo(new Object[]{array}) to foo(array) is not safe
        if (PsiType.NULL.equals(type) || type instanceof PsiArrayType) return false;
      }
      PsiCall copy = (PsiCall)callExpression.copy();
      PsiExpressionList copyArgumentList = copy.getArgumentList();
      LOG.assertTrue(copyArgumentList != null);
      PsiExpression[] args = copyArgumentList.getExpressions();
      try {
        args[args.length - 1].delete();
        if (arrayElements.length > 0) {
          copyArgumentList.addRange(arrayElements[0], arrayElements[arrayElements.length - 1]);
        }
        final Project project = callExpression.getProject();
        final JavaResolveResult resolveResult;
        if (callExpression instanceof PsiEnumConstant) {
          final PsiEnumConstant enumConstant = (PsiEnumConstant)callExpression;
          final PsiClass containingClass = enumConstant.getContainingClass();
          if (containingClass == null) return false;
          final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
          final PsiClassType classType = facade.getElementFactory().createType(containingClass);
          resolveResult = facade.getResolveHelper().resolveConstructor(classType, copyArgumentList, enumConstant);
          return resolveResult.isValidResult() && resolveResult.getElement() == oldRefMethod;
        }
        else {
          resolveResult = copy.resolveMethodGenerics();
          if (!resolveResult.isValidResult() || resolveResult.getElement() != oldRefMethod) {
            return false;
          }
          if (callExpression.getParent() instanceof PsiExpressionStatement) return true;
          final ExpectedTypeInfo[] expectedTypes = ExpectedTypesProvider.getExpectedTypes((PsiCallExpression)callExpression, false);
          if (expectedTypes.length == 0) return false;
          final PsiType expressionType = ((PsiCallExpression)copy).getType();
          if (expressionType == null) return false;
          for (ExpectedTypeInfo expectedType : expectedTypes) {
            if (expectedType.getType().isAssignableFrom(expressionType)) {
              return true;
            }
          }
          return false;
        }
      }
      catch (IncorrectOperationException e) {
        return false;
      }
    }

    private static PsiExpression @Nullable [] getInitializers(@NotNull final PsiNewExpression newExpression) {
      PsiArrayInitializerExpression initializer = newExpression.getArrayInitializer();
      if (initializer != null) {
        return initializer.getInitializers();
      }
      PsiExpression[] dims = newExpression.getArrayDimensions();
      if (dims.length > 0) {
        PsiExpression firstDimension = dims[0];
        Object value =
          JavaPsiFacade.getInstance(newExpression.getProject()).getConstantEvaluationHelper().computeConstantExpression(firstDimension);
        if (value instanceof Integer && ((Integer)value).intValue() == 0) return PsiExpression.EMPTY_ARRAY;
      }

      return null;
    }

    private static final class MyQuickFix implements LocalQuickFix {
      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiNewExpression arrayCreation = (PsiNewExpression)descriptor.getPsiElement();
        if (arrayCreation == null) return;
        CommonJavaRefactoringUtil.inlineArrayCreationForVarargs(arrayCreation);
      }

      @Override
      @NotNull
      public String getFamilyName() {
        return JavaBundle.message("inspection.redundant.array.creation.quickfix");
      }
    }
  }
}
