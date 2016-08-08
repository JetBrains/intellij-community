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
package com.intellij.codeInspection.miscGenerics;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author ven
 */
public class RedundantArrayForVarargsCallInspection extends GenericsInspectionToolBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.miscGenerics.RedundantArrayForVarargsCallInspection");
  private static final LocalQuickFix myQuickFixAction = new MyQuickFix();

  private static class MyQuickFix implements LocalQuickFix {
    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiNewExpression arrayCreation = (PsiNewExpression)descriptor.getPsiElement();
      if (arrayCreation == null || !arrayCreation.isValid()) return;
      if (!FileModificationService.getInstance().prepareFileForWrite(arrayCreation.getContainingFile())) return;
      InlineUtil.inlineArrayCreationForVarargs(arrayCreation);
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.redundant.array.creation.quickfix");
    }
  }

  @Override
  public ProblemDescriptor[] getDescriptions(@NotNull PsiElement place,
                                             @NotNull final InspectionManager manager,
                                             final boolean isOnTheFly) {
    final List<ProblemDescriptor> problems = new ArrayList<>();
    place.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitCallExpression(PsiCallExpression expression) {
        super.visitCallExpression(expression);
        checkCall(expression);
      }

      @Override
      public void visitEnumConstant(PsiEnumConstant enumConstant) {
        super.visitEnumConstant(enumConstant);
        checkCall(enumConstant);
      }

      @Override
      public void visitClass(PsiClass aClass) {
        //do not go inside to prevent multiple signals of the same problem
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
            AnnotationUtil.isAnnotated(method, Collections.singletonList("java.lang.invoke.MethodHandle.PolymorphicSignature"))) {
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
        PsiExpression lastArg = args[args.length - 1];
        PsiParameter lastParameter = parameters[args.length - 1];
        PsiType lastParamType = lastParameter.getType();
        LOG.assertTrue(lastParamType instanceof PsiEllipsisType);
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
        if (!isSafeToFlatten(expression, method, initializers)) {
          return;
        }
        String message = InspectionsBundle.message("inspection.redundant.array.creation.for.varargs.call.descriptor");
        ProblemDescriptor descriptor = manager.createProblemDescriptor(lastArg, message, myQuickFixAction,
                                                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly);
        problems.add(descriptor);
      }

      private boolean isSafeToFlatten(@NotNull PsiCall callExpression, @NotNull PsiMethod oldRefMethod, @NotNull PsiExpression[] arrayElements) {
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
            final ExpectedTypeInfo[] expectedTypes = ExpectedTypesProvider.getExpectedTypes((PsiCallExpression)callExpression, false);
            final PsiType expressionType = ((PsiCallExpression)copy).getType();
            for (ExpectedTypeInfo expectedType : expectedTypes) {
              if (!expectedType.getType().isAssignableFrom(expressionType)) {
                return false;
              }
            }
            return true;
          }
        }
        catch (IncorrectOperationException e) {
          return false;
        }
      }
    });
    if (problems.isEmpty()) return null;
    return problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  @Nullable
  private static PsiExpression[] getInitializers(final PsiNewExpression newExpression) {
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

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.VERBOSE_GROUP_NAME;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.redundant.array.creation.display.name");
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return "RedundantArrayCreation";
  }
}
