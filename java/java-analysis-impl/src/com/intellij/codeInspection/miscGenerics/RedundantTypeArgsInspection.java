// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.miscGenerics;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.compiler.JavacQuirksInspectionVisitor;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class RedundantTypeArgsInspection extends GenericsInspectionToolBase {
  private static final Logger LOG = Logger.getInstance(RedundantTypeArgsInspection.class);

  private static final LocalQuickFix ourQuickFixAction = new MyQuickFixAction();
  public static final String SHORT_NAME = "RedundantTypeArguments";

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.verbose.or.redundant.code.constructs");
  }

  @Override
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  @Override
  public ProblemDescriptor[] getDescriptions(@NotNull PsiElement place, @NotNull final InspectionManager inspectionManager, boolean isOnTheFly) {
    final List<ProblemDescriptor> problems = new ArrayList<>();
    place.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        final PsiType[] typeArguments = expression.getTypeArguments();
        if (typeArguments.length > 0) {
          checkCallExpression(expression.getMethodExpression(), typeArguments, expression, inspectionManager, problems, isOnTheFly);
        }
      }

      @Override
      public void visitNewExpression(@NotNull PsiNewExpression expression) {
        super.visitNewExpression(expression);
        final PsiType[] typeArguments = expression.getTypeArguments();
        if (typeArguments.length > 0) {
          final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
          if (classReference != null) {
            checkCallExpression(classReference, typeArguments, expression, inspectionManager, problems, isOnTheFly);
          }
        }
      }

      @Override
      public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
        super.visitMethodReferenceExpression(expression);
        checkMethodReference(expression, inspectionManager, problems, isOnTheFly);
      }
    });

    if (problems.isEmpty()) return null;
    return problems.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  private static void checkCallExpression(final PsiJavaCodeReferenceElement reference,
                                          final PsiType[] typeArguments,
                                          PsiCallExpression expression,
                                          final InspectionManager inspectionManager,
                                          final List<? super ProblemDescriptor> problems, boolean isOnTheFly) {
    PsiExpressionList argumentList = expression.getArgumentList();
    if (argumentList == null) return;
    PsiReferenceParameterList typeArgumentList = expression.getTypeArgumentList();
    for (PsiTypeElement typeElement : typeArgumentList.getTypeParameterElements()) {
      if (typeElement.getAnnotations().length > 0) {
        return;
      }
    }
    final JavaResolveResult resolveResult = reference.advancedResolve(false);

    final PsiElement element = resolveResult.getElement();
    if (element instanceof PsiMethod method && resolveResult.isValidResult()) {
      final PsiTypeParameter[] typeParameters = method.getTypeParameters();
      if (typeParameters.length > 0 &&
          JavacQuirksInspectionVisitor.isSuspicious(expression.getArgumentList().getExpressions(), method)) {
        return;
      }
      if (typeParameters.length == typeArguments.length &&
          PsiDiamondTypeUtil.areTypeArgumentsRedundant(typeArguments, expression, false, method, typeParameters) ||
          typeParameters.length == 0) {
        String key = typeParameters.length == 0 ? "inspection.redundant.type.no.generics.problem.descriptor"
                                                : "inspection.redundant.type.problem.descriptor";
        final ProblemDescriptor descriptor =
          inspectionManager.createProblemDescriptor(typeArgumentList,
                                                    JavaAnalysisBundle.message(key),
                                                    ourQuickFixAction,
                                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly);
        problems.add(descriptor);
      }
    }
  }

  private static void checkMethodReference(PsiMethodReferenceExpression expression,
                                           InspectionManager inspectionManager,
                                           List<? super ProblemDescriptor> problems,
                                           boolean isOnTheFly) {
    final PsiTypeElement qualifierTypeElement = expression.getQualifierType();
    if (qualifierTypeElement != null) {
      final PsiType psiType = qualifierTypeElement.getType();
      if (psiType instanceof PsiClassType && !((PsiClassType)psiType).isRaw()) {
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
            for (PsiTypeElement typeElement : parameterList.getTypeParameterElements()) {
              if (typeElement.getAnnotations().length > 0) {
                return;
              }
            }
            final ProblemDescriptor descriptor = inspectionManager.createProblemDescriptor(parameterList, JavaAnalysisBundle
              .message("inspection.redundant.type.problem.descriptor"), ourQuickFixAction, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly);
            problems.add(descriptor);
          }
        }
      }
    }
    else {
      PsiType[] typeArguments = expression.getTypeParameters();
      PsiReferenceParameterList parameterList = expression.getParameterList();
      if (typeArguments.length > 0 && parameterList != null) {
        PsiElement resolve = expression.resolve();
        if (resolve == null) return;
        PsiTypeParameter[] typeParameters = resolve instanceof PsiClass ? PsiTypeParameter.EMPTY_ARRAY : ((PsiMethod)resolve).getTypeParameters();
        if (typeParameters.length == 0 ||
            typeParameters.length == typeArguments.length &&
            PsiDiamondTypeUtil.areTypeArgumentsRedundant(typeArguments, expression, false, (PsiMethod)resolve, typeParameters)) {
          String key = typeParameters.length == 0 ? "inspection.redundant.type.no.generics.method.reference.problem.descriptor"
                                                  : "inspection.redundant.type.problem.descriptor";
          final ProblemDescriptor descriptor =
            inspectionManager.createProblemDescriptor(parameterList,
                                                      JavaAnalysisBundle.message(key),
                                                      new MyQuickFixAction(), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly);
          problems.add(descriptor);
        }
      }
    }
  }

  private static class MyQuickFixAction extends PsiUpdateModCommandQuickFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return JavaAnalysisBundle.message("inspection.redundant.type.remove.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiReferenceParameterList typeArgumentList)) return;
      try {
        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
        PsiMethodReferenceExpression ref = PsiTreeUtil.getParentOfType(typeArgumentList, PsiMethodReferenceExpression.class);
        PsiTypeElement qualifierType = ref != null ? ref.getQualifierType() : null;
        if (PsiTreeUtil.isAncestor(qualifierType, typeArgumentList, false)) {
          PsiClass targetClass = PsiUtil.resolveClassInType(qualifierType.getType());
          if (targetClass != null) {
            new CommentTracker().replaceAndRestoreComments(qualifierType, elementFactory.createReferenceExpression(targetClass));
          }
        }
        else {
          final PsiMethodCallExpression expr =
            (PsiMethodCallExpression)elementFactory.createExpressionFromText("foo()", null);
          new CommentTracker().replaceAndRestoreComments(typeArgumentList, expr.getTypeArgumentList());
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }
}
