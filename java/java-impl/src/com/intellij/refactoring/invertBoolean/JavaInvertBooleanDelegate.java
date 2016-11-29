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
package com.intellij.refactoring.invertBoolean;

import com.intellij.codeInsight.CodeInsightServicesUtil;
import com.intellij.codeInsight.daemon.impl.RecursiveCallLineMarkerProvider;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Query;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class JavaInvertBooleanDelegate extends InvertBooleanDelegate {
  @Override
  public boolean isVisibleOnElement(@NotNull PsiElement element) {
    return element instanceof PsiVariable || element instanceof PsiMethod;
  }

  @Override
  public boolean isAvailableOnElement(@NotNull PsiElement element) {
    if (element instanceof PsiVariable) {
      return PsiType.BOOLEAN.equals(((PsiVariable) element).getType());
    }
    else if (element instanceof PsiMethod) {
      return PsiType.BOOLEAN.equals(((PsiMethod) element).getReturnType());
    }
    return false;
  }

  @Override
  public PsiElement adjustElement(PsiElement element, Project project, Editor editor) {
    if (element instanceof PsiVariable) {
      PsiVariable var = (PsiVariable)element;
      final PsiType returnType = var.getType();
      if (!PsiType.BOOLEAN.equals(returnType)) {
        CommonRefactoringUtil.showErrorHint(project, editor,
                                            RefactoringBundle
                                              .getCannotRefactorMessage(RefactoringBundle.message("invert.boolean.wrong.type")),
                                            InvertBooleanHandler.REFACTORING_NAME, InvertBooleanHandler.INVERT_BOOLEAN_HELP_ID);
        return null;
      }

      if (var instanceof PsiParameter) {
        final PsiElement declarationScope = ((PsiParameter)var).getDeclarationScope();
        if (declarationScope instanceof PsiMethod) {
          final PsiMethod method = (PsiMethod)declarationScope;
          final PsiMethod superMethod = SuperMethodWarningUtil.checkSuperMethod(method, RefactoringBundle.message("to.refactor"));
          if (superMethod == null) {
            return null;
          }
          var = superMethod.getParameterList().getParameters()[method.getParameterList().getParameterIndex((PsiParameter)var)];
        }
        else if (declarationScope instanceof PsiForeachStatement) {
          CommonRefactoringUtil.showErrorHint(project, editor,
                                              RefactoringBundle.message("invert.boolean.foreach"),
                                              InvertBooleanHandler.REFACTORING_NAME, InvertBooleanHandler.INVERT_BOOLEAN_HELP_ID);
          return null;
        }
      }
      return var;
    }
    else if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      final PsiType returnType = method.getReturnType();
      if (!PsiType.BOOLEAN.equals(returnType)) {
        CommonRefactoringUtil.showErrorHint(project, editor,
                                            RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("invert.boolean.wrong.type")),
                                            InvertBooleanHandler.REFACTORING_NAME,
                                            InvertBooleanHandler.INVERT_BOOLEAN_HELP_ID);
        return null;
      }

      return SuperMethodWarningUtil.checkSuperMethod(method, RefactoringBundle.message("to.refactor"));
    }
    return null;
  }

  public void collectRefsToInvert(PsiElement namedElement, Collection<PsiElement> elementsToInvert) {
    final Query<PsiReference> query = namedElement instanceof PsiMethod ?
                                      MethodReferencesSearch.search((PsiMethod)namedElement) :
                                      ReferencesSearch.search(namedElement);
    final Collection<PsiReference> refs = query.findAll();

    for (PsiReference ref : refs) {
      final PsiElement element = ref.getElement();
      PsiElement refElement = getElementToInvert(namedElement, element);
      if (refElement == null) {
        refElement = getForeignElementToInvert(namedElement, element, JavaLanguage.INSTANCE);
      }
      if (refElement != null) {
        elementsToInvert.add(refElement);
      }
    }
  }

  public PsiElement getElementToInvert(PsiElement namedElement, PsiElement element) {
    if (element instanceof PsiReferenceExpression) {
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)element;
      PsiElement parent = refExpr.getParent();
      if (parent instanceof PsiAssignmentExpression && refExpr.equals(((PsiAssignmentExpression)parent).getLExpression())) {
        return ((PsiAssignmentExpression)parent).getRExpression();
      }
      else {
        if (namedElement instanceof PsiParameter) { //filter usages in super method calls
          PsiElement gParent = refExpr.getParent().getParent();
          if (gParent instanceof PsiMethodCallExpression) {
            if (!canInvertReferenceElement(((PsiMethodCallExpression)gParent).getMethodExpression(), true)) {
              return null;
            }
          }
        }
        return refExpr;
      }
    }
    return null;
  }

  private static boolean canInvertReferenceElement(PsiElement expression, boolean recursive) {
    PsiExpression qualifierExpression = expression instanceof PsiReferenceExpression ? ((PsiReferenceExpression)expression).getQualifierExpression() 
                                                                                     : null;
    if (qualifierExpression == null || !"super".equals(qualifierExpression.getText())) {
      PsiElement parent = expression.getParent();
      if (parent instanceof PsiMethodCallExpression) {
        return !(recursive && RecursiveCallLineMarkerProvider.isRecursiveMethodCall((PsiMethodCallExpression)parent));
      } else {
        return true;
      }
    }
    return false;
  }

  @Override
  public void replaceWithNegatedExpression(@NotNull PsiElement expression) {
    if (!(expression instanceof PsiExpression)) {
      return;
    }
    if (expression.getParent() instanceof PsiMethodCallExpression) {
      expression = expression.getParent();
    }
    while (expression.getParent() instanceof PsiPrefixExpression &&
           ((PsiPrefixExpression)expression.getParent()).getOperationTokenType() == JavaTokenType.EXCL) {
      expression = expression.getParent();
    }

    if (expression instanceof PsiMethodReferenceExpression) {
      final PsiExpression callExpression = LambdaRefactoringUtil.convertToMethodCallInLambdaBody((PsiMethodReferenceExpression)expression);
      if (callExpression instanceof PsiCallExpression) {
        callExpression.replace(CodeInsightServicesUtil.invertCondition(callExpression));
      }
    }
    else if (!(expression.getParent() instanceof PsiExpressionStatement)) {
      expression.replace(CodeInsightServicesUtil.invertCondition((PsiExpression)expression));
    }
  }

  @Override
  public void invertElementInitializer(final PsiElement element) {
    if (element instanceof PsiField && ((PsiField)element).getInitializer() == null && !((PsiField)element).hasModifierProperty(PsiModifier.FINAL)) {
      ((PsiField)element).setInitializer(JavaPsiFacade.getElementFactory(element.getProject()).createExpressionFromText("true", element));
    } else if (element instanceof PsiVariable) {
      final PsiExpression initializer = ((PsiVariable)element).getInitializer();
      if (initializer != null) {
        replaceWithNegatedExpression(initializer);
      }
    }
  }

  public void collectRefElements(final PsiElement element,
                                 final RenameProcessor renameProcessor,
                                 @NotNull final String newName,
                                 final Collection<PsiElement> elementsToInvert) {
    collectRefsToInvert(element, elementsToInvert);

    if (element instanceof PsiMethod) {
      final Collection<PsiMethod> overriders = OverridingMethodsSearch.search((PsiMethod)element).findAll();
      if (renameProcessor != null) {
        for (PsiMethod overrider : overriders) {
          renameProcessor.addElement(overrider, newName);
        }
      }

      Collection<PsiMethod> allMethods = new HashSet<>(overriders);
      allMethods.add((PsiMethod)element);

      for (PsiMethod method : allMethods) {
        method.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override public void visitReturnStatement(PsiReturnStatement statement) {
            final PsiExpression returnValue = statement.getReturnValue();
            if (returnValue != null && PsiType.BOOLEAN.equals(returnValue.getType())) {
              elementsToInvert.add(returnValue);
            }
          }

          @Override
          public void visitClass(PsiClass aClass) {}

          @Override
          public void visitLambdaExpression(PsiLambdaExpression expression) {}
        });
      }
    }
    else if (element instanceof PsiParameter && ((PsiParameter)element).getDeclarationScope() instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)((PsiParameter)element).getDeclarationScope();
      int index = method.getParameterList().getParameterIndex((PsiParameter)element);
      assert index >= 0;
      final Query<PsiReference> methodQuery = MethodReferencesSearch.search(method);
      final Collection<PsiReference> methodRefs = methodQuery.findAll();
      for (PsiReference ref : methodRefs) {
        PsiElement parent = ref.getElement().getParent();
        if (parent instanceof PsiAnonymousClass) {
          parent = parent.getParent();
        }
        if (parent instanceof PsiCall) {
          final PsiCall call = (PsiCall)parent;
          final PsiReferenceExpression methodExpression = call instanceof PsiMethodCallExpression ?
                                                          ((PsiMethodCallExpression)call).getMethodExpression() :
                                                          null;
          final PsiExpressionList argumentList = call.getArgumentList();
          if (argumentList != null) {
            final PsiExpression[] args = argumentList.getExpressions();
            if (index < args.length) {
              if (methodExpression == null ||
                  canInvertReferenceElement(methodExpression, 
                                            args[index] instanceof PsiReferenceExpression && 
                                            ((PsiReferenceExpression)args[index]).resolve() == element)) {
                elementsToInvert.add(args[index]);
              }
            }
          }
        }
      }

      final Collection<PsiMethod> overriders = OverridingMethodsSearch.search(method).findAll();
      for (PsiMethod overrider : overriders) {
        final PsiParameter overriderParameter = overrider.getParameterList().getParameters()[index];
        if (renameProcessor != null) {
          renameProcessor.addElement(overriderParameter, newName);
        }
        collectRefsToInvert(overriderParameter, elementsToInvert);
      }
    }
  }

  @Override
  public void findConflicts(UsageInfo[] usageInfos, MultiMap<PsiElement, String> conflicts) {
    for (UsageInfo info : usageInfos) {
      final PsiElement element = info.getElement();
      if (element instanceof PsiMethodReferenceExpression) {
        conflicts.putValue(element, RefactoringBundle.message("expand.method.reference.warning"));
      }
    }
  }
}
