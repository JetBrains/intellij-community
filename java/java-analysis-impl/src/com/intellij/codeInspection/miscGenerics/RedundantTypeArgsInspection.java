/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class RedundantTypeArgsInspection extends GenericsInspectionToolBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.miscGenerics.RedundantTypeArgsInspection");

  public RedundantTypeArgsInspection() {
    myQuickFixAction = new MyQuickFixAction();
  }

  private final LocalQuickFix myQuickFixAction;

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.VERBOSE_GROUP_NAME;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.redundant.type.display.name");
  }

  @Override
  @NotNull
  public String getShortName() {
    return "RedundantTypeArguments";
  }



  @Override
  public ProblemDescriptor[] checkMethod(@NotNull PsiMethod psiMethod, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final PsiCodeBlock body = psiMethod.getBody();
    if (body != null) {
      return getDescriptions(body, manager, isOnTheFly);
    }
    return null;
  }

  @Override
  public ProblemDescriptor[] getDescriptions(@NotNull PsiElement place, @NotNull final InspectionManager inspectionManager, boolean isOnTheFly) {
    final List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
    place.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        final PsiType[] typeArguments = expression.getTypeArguments();
        if (typeArguments.length > 0) {
          checkCallExpression(expression.getMethodExpression(), typeArguments, expression, inspectionManager, problems);
        }
      }

      @Override public void visitNewExpression(PsiNewExpression expression) {
        final PsiType[] typeArguments = expression.getTypeArguments();
        if (typeArguments.length > 0) {
          final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
          if (classReference != null) {
            checkCallExpression(classReference, typeArguments, expression, inspectionManager, problems);
          }
        }
      }

      private void checkCallExpression(final PsiJavaCodeReferenceElement reference,
                                       final PsiType[] typeArguments,
                                       PsiCallExpression expression,
                                       final InspectionManager inspectionManager, final List<ProblemDescriptor> problems) {

        PsiExpressionList argumentList = expression.getArgumentList();
        if (argumentList == null) return;
        final JavaResolveResult resolveResult = reference.advancedResolve(false);

        final PsiElement element = resolveResult.getElement();
        if (element instanceof PsiMethod && resolveResult.isValidResult()) {
          PsiMethod method = (PsiMethod)element;
          final PsiTypeParameter[] typeParameters = method.getTypeParameters();
          if (typeParameters.length == typeArguments.length) {
            final PsiParameter[] parameters = method.getParameterList().getParameters();
            PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(expression.getProject()).getResolveHelper();
            final PsiSubstitutor psiSubstitutor = resolveHelper
              .inferTypeArguments(typeParameters, parameters, argumentList.getExpressions(), PsiSubstitutor.EMPTY, expression, DefaultParameterTypeInferencePolicy.INSTANCE);
            for (int i = 0, length = typeParameters.length; i < length; i++) {
              PsiTypeParameter typeParameter = typeParameters[i];
              final PsiType inferredType = psiSubstitutor.getSubstitutionMap().get(typeParameter);
              if (!typeArguments[i].equals(inferredType)) return;
              if (PsiUtil.resolveClassInType(method.getReturnType()) == typeParameter && PsiPrimitiveType.getUnboxedType(inferredType) != null) return;
            }

            final PsiCallExpression copy = (PsiCallExpression)expression.copy(); //see IDEADEV-8174
            try {
              copy.getTypeArgumentList().delete();
              if (copy.resolveMethod() != element) return;
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
              return;
            }

            final ProblemDescriptor descriptor = inspectionManager.createProblemDescriptor(expression.getTypeArgumentList(),
                                                                                           InspectionsBundle.message("inspection.redundant.type.problem.descriptor"),
                                                                                           myQuickFixAction,
                                                                                           ProblemHighlightType.LIKE_UNUSED_SYMBOL, false);
            problems.add(descriptor);
          }
        }
      }

    });

    if (problems.isEmpty()) return null;
    return problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  private static class MyQuickFixAction implements LocalQuickFix {
    @Override
    @NotNull
    public String getName() {
      return InspectionsBundle.message("inspection.redundant.type.remove.quickfix");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiReferenceParameterList typeArgumentList = (PsiReferenceParameterList)descriptor.getPsiElement();
      if (!FileModificationService.getInstance().preparePsiElementForWrite(typeArgumentList)) return;
      try {
        final PsiMethodCallExpression expr =
          (PsiMethodCallExpression)JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText("foo()", null);
        typeArgumentList.replace(expr.getTypeArgumentList());
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }
  }
}
