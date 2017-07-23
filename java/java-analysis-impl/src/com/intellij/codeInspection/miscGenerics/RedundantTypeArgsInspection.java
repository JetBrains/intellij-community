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
package com.intellij.codeInspection.miscGenerics;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class RedundantTypeArgsInspection extends GenericsInspectionToolBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.miscGenerics.RedundantTypeArgsInspection");

  private final static LocalQuickFix ourQuickFixAction = new MyQuickFixAction();
  public static final String SHORT_NAME = "RedundantTypeArguments";

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
    return SHORT_NAME;
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
    final List<ProblemDescriptor> problems = new ArrayList<>();
    place.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        final PsiType[] typeArguments = expression.getTypeArguments();
        if (typeArguments.length > 0) {
          checkCallExpression(expression.getMethodExpression(), typeArguments, expression, inspectionManager, problems);
        }
      }

      @Override
      public void visitNewExpression(PsiNewExpression expression) {
        super.visitNewExpression(expression);
        final PsiType[] typeArguments = expression.getTypeArguments();
        if (typeArguments.length > 0) {
          final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
          if (classReference != null) {
            checkCallExpression(classReference, typeArguments, expression, inspectionManager, problems);
          }
        }
      }

      @Override
      public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
        super.visitMethodReferenceExpression(expression);
        checkMethodReference(expression, inspectionManager, problems);
      }
    });

    if (problems.isEmpty()) return null;
    return problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  private static void checkCallExpression(final PsiJavaCodeReferenceElement reference,
                                          final PsiType[] typeArguments,
                                          PsiCallExpression expression,
                                          final InspectionManager inspectionManager,
                                          final List<ProblemDescriptor> problems) {
    PsiExpressionList argumentList = expression.getArgumentList();
    if (argumentList == null) return;
    final JavaResolveResult resolveResult = reference.advancedResolve(false);

    final PsiElement element = resolveResult.getElement();
    if (element instanceof PsiMethod && resolveResult.isValidResult()) {
      PsiMethod method = (PsiMethod)element;
      final PsiTypeParameter[] typeParameters = method.getTypeParameters();
      if (typeParameters.length == typeArguments.length) {
        if (PsiDiamondTypeUtil.areTypeArgumentsRedundant(typeArguments, expression, false, method, typeParameters)) {
          final ProblemDescriptor descriptor = inspectionManager.createProblemDescriptor(expression.getTypeArgumentList(),
                                                                                         InspectionsBundle.message(
                                                                                           "inspection.redundant.type.problem.descriptor"),
                                                                                         ourQuickFixAction,
                                                                                         ProblemHighlightType.LIKE_UNUSED_SYMBOL, false);
          problems.add(descriptor);
        }
      }
    }
  }

  private static void checkMethodReference(PsiMethodReferenceExpression expression,
                                           InspectionManager inspectionManager,
                                           List<ProblemDescriptor> problems) {
    final PsiTypeElement qualifierTypeElement = expression.getQualifierType();
    if (qualifierTypeElement != null) {
      final PsiType psiType = qualifierTypeElement.getType();
      if (psiType instanceof PsiClassType && !(((PsiClassType)psiType).isRaw())) {
        PsiClass aClass = ((PsiClassType)psiType).resolve();
        if (aClass == null) return;
        final JavaResolveResult result = expression.advancedResolve(false);
        final PsiElement element = result.getElement();
        if (element instanceof PsiTypeParameterListOwner) {
          PsiMethod method = element instanceof PsiMethod ? (PsiMethod)element : null;
          if (PsiDiamondTypeUtil.areTypeArgumentsRedundant(((PsiClassType)psiType).getParameters(), expression, false, method, aClass.getTypeParameters())) {
            final PsiJavaCodeReferenceElement referenceElement = qualifierTypeElement.getInnermostComponentReferenceElement();
            LOG.assertTrue(referenceElement != null, qualifierTypeElement);
            final PsiReferenceParameterList parameterList = referenceElement.getParameterList();
            LOG.assertTrue(parameterList != null);
            final ProblemDescriptor descriptor = inspectionManager.createProblemDescriptor(parameterList, InspectionsBundle
              .message("inspection.redundant.type.problem.descriptor"), new MyMethodReferenceFixAction(), ProblemHighlightType.LIKE_UNUSED_SYMBOL, false);
            problems.add(descriptor);
          }
        }
      }
    }
  }

  private static class MyQuickFixAction implements LocalQuickFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.redundant.type.remove.quickfix");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiReferenceParameterList)) return;
      final PsiReferenceParameterList typeArgumentList = (PsiReferenceParameterList)element;
      try {
        final PsiMethodCallExpression expr =
          (PsiMethodCallExpression)JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText("foo()", null);
        typeArgumentList.replace(expr.getTypeArgumentList());
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  //separate quickfix is needed to invalidate initial method reference
  //otherwise it would provide inconsistent substitutors to the next chained calls
  private static class MyMethodReferenceFixAction implements LocalQuickFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.redundant.type.remove.quickfix");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiTypeElement typeElement = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiTypeElement.class);
      final PsiMethodReferenceExpression expression = PsiTreeUtil.getParentOfType(typeElement, PsiMethodReferenceExpression.class);
      if (expression != null) {
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(expression.getProject());
        final PsiClass aClass = ((PsiClassType)typeElement.getType()).resolve();
        if (aClass != null) {
          final PsiMethodReferenceExpression copy = (PsiMethodReferenceExpression)expression.copy();
          final PsiTypeElement qualifier = copy.getQualifierType();
          assert qualifier != null;
          qualifier.replace(elementFactory.createReferenceExpression(aClass));
          expression.replace(copy);
        }
      }
    }
  }
}
