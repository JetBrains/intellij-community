/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class RedundantArrayForVarargsCallInspection extends GenericsInspectionToolBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.miscGenerics.RedundantArrayForVarargsCallInspection");
  private final LocalQuickFix myQuickFixAction = new MyQuickFix();

  private static class MyQuickFix implements LocalQuickFix {
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiNewExpression arrayCreation = (PsiNewExpression) descriptor.getPsiElement();
      if (arrayCreation == null || !arrayCreation.isValid()) return;
      if (!CodeInsightUtilBase.prepareFileForWrite(arrayCreation.getContainingFile())) return;
      InlineUtil.inlineArrayCreationForVarargs(arrayCreation);
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.redundant.array.creation.quickfix");
    }
  }

  public ProblemDescriptor[] getDescriptions(PsiElement place, final InspectionManager manager, final boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel5OrHigher(place)) return null;
    final List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
    place.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitCallExpression(PsiCallExpression expression) {
        super.visitCallExpression(expression);
        checkCall(expression);
      }

      @Override public void visitEnumConstant(PsiEnumConstant enumConstant) {
        super.visitEnumConstant(enumConstant);
        checkCall(enumConstant);
      }

      @Override public void visitClass(PsiClass aClass) {
        //do not go inside to prevent multiple signals of the same problem
      }

      private void checkCall(PsiCall expression) {
        final JavaResolveResult resolveResult = expression.resolveMethodGenerics();
        PsiElement element = resolveResult.getElement();
        final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
        if (element instanceof PsiMethod && ((PsiMethod)element).isVarArgs()) {
          PsiMethod method = (PsiMethod)element;
          PsiParameter[] parameters = method.getParameterList().getParameters();
          PsiExpressionList argumentList = expression.getArgumentList();
          if (argumentList != null) {
            PsiExpression[] args = argumentList.getExpressions();
            if (parameters.length == args.length) {
              PsiExpression lastArg = args[args.length - 1];
              PsiParameter lastParameter = parameters[args.length - 1];
              PsiType lastParamType = lastParameter.getType();
              LOG.assertTrue(lastParamType instanceof PsiEllipsisType);
              if (lastArg instanceof PsiNewExpression &&
                  substitutor.substitute(((PsiEllipsisType) lastParamType).toArrayType()).equals(lastArg.getType())) {
                PsiExpression[] initializers = getInitializers((PsiNewExpression)lastArg);
                if (initializers != null) {
                  if (isSafeToFlatten(expression, method, initializers)) {
                    final ProblemDescriptor descriptor = manager.createProblemDescriptor(lastArg,
                                                                                         InspectionsBundle.message("inspection.redundant.array.creation.for.varargs.call.descriptor"),
                                                                                         myQuickFixAction,
                                                                                         ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                                                         isOnTheFly);

                    problems.add(descriptor);
                  }
                }
              }
            }
          }
        }
      }

      private boolean isSafeToFlatten(PsiCall callExpression, PsiMethod oldRefMethod, PsiExpression[] arrayElements) {
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
          } else {
            resolveResult = copy.resolveMethodGenerics();
            if (!resolveResult.isValidResult() || resolveResult.getElement() != oldRefMethod) {
              return false;
            }
            final ExpectedTypeInfo[] expectedTypes = ExpectedTypesProvider.getExpectedTypes((PsiCallExpression) callExpression, false);
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

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.VERBOSE_GROUP_NAME;
  }

  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.redundant.array.creation.display.name");
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return "RedundantArrayCreation";
  }
}
