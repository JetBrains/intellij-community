/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class CollectionAddAllCanBeReplacedWithConstructorInspection extends BaseJavaBatchLocalInspectionTool {
  private final static Logger LOG = Logger.getInstance(CollectionAddAllCanBeReplacedWithConstructorInspection.class);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        final String methodName = expression.getMethodExpression().getReferenceName();
        if ("addAll".equals(methodName)) {
          if (expression.getArgumentList().getExpressions().length != 1) {
            return;
          }
          final PsiExpression qualifierExpression = expression.getMethodExpression().getQualifierExpression();
          if (!(qualifierExpression instanceof PsiReferenceExpression)) {
            return;
          }
          final PsiElement resolvedReference = ((PsiReferenceExpression)qualifierExpression).resolve();
          if (!(resolvedReference instanceof PsiLocalVariable)) {
            return;
          }
          PsiLocalVariable variable = (PsiLocalVariable)resolvedReference;
          final PsiType variableType = variable.getType();
          if (!(variableType instanceof PsiClassType)) {
            return;
          }
          final PsiClass variableClass = ((PsiClassType)variableType).resolve();
          if (variableClass == null || !InheritanceUtil.isInheritor(variableClass, CommonClassNames.JAVA_UTIL_COLLECTION)) {
            return;
          }
          PsiNewExpression assignmentExpression;
          final Pair<Boolean, PsiNewExpression> pair = isProperAssignmentStatementFound(variable, expression);
          if (pair.getFirst()) {
            assignmentExpression = pair.getSecond();
            if (assignmentExpression == null) {
              if (checkLocalVariableAssignmentOrInitializer(variable.getInitializer())) {
                assignmentExpression = (PsiNewExpression)variable.getInitializer();
              } else {
                return;
              }
            }
          } else {
            return;
          }
          if (!checkUsages(variable, expression, assignmentExpression)) {
            return;
          }
          final PsiMethod method = expression.resolveMethod();
          if (method != null) {
            //noinspection DialogTitleCapitalization
            holder.registerProblem(expression, QuickFixBundle.message("collection.addall.can.be.replaced.with.constructor.fix.description"),
                                   new ReplaceAddAllWithConstructorFix(assignmentExpression, expression));
          }
        }
      }
    };
  }

  private static boolean checkLocalVariableAssignmentOrInitializer(PsiExpression initializer) {
    if (!(initializer instanceof PsiNewExpression)) {
      return false;
    }
    final PsiNewExpression newExpression = (PsiNewExpression)initializer;
    final PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
    if (classReference == null) {
      return false;
    }
    final PsiClass initializerClass = (PsiClass)classReference.resolve();
    if (initializerClass == null || !hasProperConstructor(initializerClass)) {
      return false;
    }
    final PsiExpressionList argumentList = newExpression.getArgumentList();
    return argumentList != null && argumentList.getExpressions().length == 0;
  }

  private static boolean hasProperConstructor(PsiClass psiClass) {
    for (PsiMethod psiMethod : psiClass.getConstructors()) {
      PsiParameterList parameterList = psiMethod.getParameterList();
      if(parameterList.getParametersCount() == 1) {
        PsiParameter parameter = parameterList.getParameters()[0];
        PsiTypeElement typeElement = parameter.getTypeElement();
        if (typeElement != null) {
          PsiType type = typeElement.getType();
          if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_COLLECTION)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static Pair<Boolean, PsiNewExpression> isProperAssignmentStatementFound(PsiLocalVariable localVariable, PsiMethodCallExpression addAllExpression) {
    PsiStatement currentStatement = PsiTreeUtil.getParentOfType(addAllExpression, PsiStatement.class);
    final PsiStatement localVariableDefinitionStatement = PsiTreeUtil.getParentOfType(localVariable, PsiStatement.class);
    while (currentStatement != null) {
      currentStatement = PsiTreeUtil.getPrevSiblingOfType(currentStatement, PsiStatement.class);
      if (currentStatement == localVariableDefinitionStatement) {
        return Pair.create(true, null);
      }
      for (PsiAssignmentExpression expression : PsiTreeUtil.findChildrenOfType(currentStatement, PsiAssignmentExpression.class)) {
        final PsiExpression lExpression = expression.getLExpression();
        if (lExpression instanceof PsiReferenceExpression && ((PsiReferenceExpression)lExpression).isReferenceTo(localVariable)) {
          final PsiExpression rExpression = expression.getRExpression();
          final boolean isValid = checkLocalVariableAssignmentOrInitializer(rExpression);
          return Pair.create(isValid, isValid ? (PsiNewExpression)rExpression : null);
        }
      }
    }
    return Pair.create(true, null);
  }

  private static class ReplaceAddAllWithConstructorFix implements LocalQuickFix {
    private final SmartPsiElementPointer<PsiMethodCallExpression> myMethodCallExpression;
    private final SmartPsiElementPointer<PsiNewExpression> myAssignmentExpression;

    private ReplaceAddAllWithConstructorFix(PsiNewExpression assignmentExpression, PsiMethodCallExpression expression) {
      final SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(assignmentExpression.getProject());
      myMethodCallExpression = smartPointerManager.createSmartPsiElementPointer(expression);
      myAssignmentExpression = smartPointerManager.createSmartPsiElementPointer(assignmentExpression);
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return getFamilyName();
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return QuickFixBundle.message("collection.addall.can.be.replaced.with.constructor.fix.title");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiMethodCallExpression methodCallExpression = myMethodCallExpression.getElement();
      LOG.assertTrue(methodCallExpression != null);
      final PsiElement parameter = methodCallExpression.getArgumentList().getExpressions()[0].copy();
      final PsiNewExpression element = myAssignmentExpression.getElement();
      LOG.assertTrue(element != null);
      final PsiExpressionList constructorArguments = element.getArgumentList();
      LOG.assertTrue(constructorArguments != null);
      constructorArguments.add(parameter);
      methodCallExpression.delete();
    }
  }

  private static List<PsiElement> extractReferencedElementsFromParameter(PsiMethodCallExpression expression) {
    final PsiExpression psiExpression = expression.getArgumentList().getExpressions()[0];
    final Collection<PsiReferenceExpression> references =
      new ArrayList<PsiReferenceExpression>(PsiTreeUtil.findChildrenOfType(psiExpression, PsiReferenceExpression.class));
    if (psiExpression instanceof PsiReferenceExpression) {
      references.add((PsiReferenceExpression)psiExpression);
    }
    return ContainerUtil.mapNotNull(references, new NullableFunction<PsiReferenceExpression, PsiElement>() {
      @Nullable
      @Override
      public PsiElement fun(PsiReferenceExpression expression) {
        return expression.resolve();
      }
    });
  }

  private static boolean isReferenceToOneOf(PsiReferenceExpression reference, List<PsiElement> elements) {
    for (PsiElement element : elements) {
      if (reference.isReferenceTo(element)) {
        return true;
      }
    }
    return false;
  }

  private static boolean checkUsages(PsiLocalVariable variable,
                                     PsiMethodCallExpression methodCallExpression,
                                     PsiNewExpression variableAssignmentExpression) {
    final PsiCodeBlock variableAssignmentBlock = PsiTreeUtil.getParentOfType(variableAssignmentExpression, PsiCodeBlock.class);
    final PsiCodeBlock methodCallBlock = PsiTreeUtil.getParentOfType(methodCallExpression, PsiCodeBlock.class);
    if (variableAssignmentBlock == null || variableAssignmentBlock != methodCallBlock) {
      return false;
    }
    final PsiStatement variableDeclarationStatement = PsiTreeUtil.getParentOfType(variableAssignmentExpression, PsiStatement.class);
    final PsiStatement methodCallStatement = PsiTreeUtil.getParentOfType(methodCallExpression, PsiStatement.class);
    if (variableDeclarationStatement == null ||
        methodCallStatement == null ||
        variableDeclarationStatement.getParent() != methodCallStatement.getParent()) {
      return false;
    }
    PsiElement nextStatement = variableDeclarationStatement;
    final List<PsiElement> referencedElementsFromParameter = extractReferencedElementsFromParameter(methodCallExpression);

    while (nextStatement != null) {
      nextStatement = PsiTreeUtil.getNextSiblingOfType(nextStatement, PsiStatement.class);
      if (nextStatement == methodCallStatement) {
        return true;
      }
      else {
        for (PsiReferenceExpression referenceExpression : PsiTreeUtil.findChildrenOfType(nextStatement, PsiReferenceExpression.class)) {
          if (referenceExpression.isReferenceTo(variable) || isReferenceToOneOf(referenceExpression, referencedElementsFromParameter)) {
            return false;
          }
        }
        for (PsiLocalVariable localVariable : PsiTreeUtil.findChildrenOfType(nextStatement, PsiLocalVariable.class)) {
          if (referencedElementsFromParameter.contains(localVariable)) {
            return false;
          }
        }
      }
    }
    return false;
  }
}