// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.invertBoolean;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.impl.RecursiveCallLineMarkerProvider;
import com.intellij.codeInspection.dataFlow.ContractReturnValue;
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.codeInspection.dataFlow.StandardMethodContract;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.BoolUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public final class JavaInvertBooleanDelegate extends InvertBooleanDelegate {
  @Override
  public boolean isVisibleOnElement(@NotNull PsiElement element) {
    return element instanceof PsiVariable || element instanceof PsiMethod;
  }

  @Override
  public boolean isAvailableOnElement(@NotNull PsiElement element) {
    if (element instanceof PsiVariable var) {
      return PsiTypes.booleanType().equals(var.getType());
    }
    else if (element instanceof PsiMethod method) {
      return PsiTypes.booleanType().equals(method.getReturnType());
    }
    return false;
  }

  @Override
  public PsiElement adjustElement(PsiElement element, Project project, Editor editor) {
    if (element instanceof PsiVariable var) {
      if (!PsiTypes.booleanType().equals(var.getType())) {
        String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("invert.boolean.wrong.type"));
        CommonRefactoringUtil.showErrorHint(project, editor, message, InvertBooleanHandler.getRefactoringName(),
                                            InvertBooleanHandler.INVERT_BOOLEAN_HELP_ID);
        return null;
      }

      if (var instanceof PsiParameter parameter) {
        final PsiElement declarationScope = parameter.getDeclarationScope();
        if (declarationScope instanceof PsiMethod method) {
          final PsiMethod superMethod = SuperMethodWarningUtil.checkSuperMethod(method);
          if (superMethod == null) {
            return null;
          }
          var = superMethod.getParameterList().getParameters()[method.getParameterList().getParameterIndex(parameter)];
        }
        else if (declarationScope instanceof PsiForeachStatement) {
          CommonRefactoringUtil.showErrorHint(project, editor,
                                              JavaRefactoringBundle.message("invert.boolean.foreach"),
                                              InvertBooleanHandler.getRefactoringName(), InvertBooleanHandler.INVERT_BOOLEAN_HELP_ID);
          return null;
        }
      }
      return var;
    }
    else if (element instanceof PsiMethod method) {
      if (!PsiTypes.booleanType().equals(method.getReturnType())) {
        String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("invert.boolean.wrong.type"));
        CommonRefactoringUtil.showErrorHint(project, editor, message, InvertBooleanHandler.getRefactoringName(),
                                            InvertBooleanHandler.INVERT_BOOLEAN_HELP_ID);
        return null;
      }

      return SuperMethodWarningUtil.checkSuperMethod(method);
    }
    return null;
  }

  public void collectRefsToInvert(PsiElement namedElement, Collection<? super PsiElement> elementsToInvert) {
    final Query<PsiReference> query =
      namedElement instanceof PsiMethod m ? MethodReferencesSearch.search(m) : ReferencesSearch.search(namedElement);

    for (PsiReference ref : query.findAll()) {
      final PsiElement element = ref.getElement();
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(element.getParent());
      if (parent instanceof PsiReturnStatement) {
        PsiMethod method = PsiTreeUtil.getParentOfType(parent, PsiMethod.class, true, PsiMember.class);
        if (method != null && JavaPsiRecordUtil.getRecordComponentForAccessor(method) != null) {
          continue; // skip record accessor return statements
        }
      }
      if (!collectElementsToInvert(namedElement, element, elementsToInvert)) {
        collectForeignElementsToInvert(namedElement, element, JavaLanguage.INSTANCE, elementsToInvert);
      }
    }
  }

  @Override
  public boolean collectElementsToInvert(PsiElement namedElement, PsiElement expression, Collection<? super PsiElement> elementsToInvert) {
    boolean toInvert = super.collectElementsToInvert(namedElement, expression, elementsToInvert);
    PsiElement parent = expression.getParent();
    if (parent instanceof PsiAssignmentExpression && !(parent.getParent() instanceof PsiExpressionStatement) || isOperatorAssignment(parent)) {
      elementsToInvert.add(parent);
    }
    return toInvert;
  }

  @Override
  public PsiElement getElementToInvert(PsiElement namedElement, PsiElement element) {
    if (element instanceof PsiReferenceExpression refExpr) {
      PsiElement parent = refExpr.getParent();
      if (parent instanceof PsiAssignmentExpression assign && refExpr.equals(assign.getLExpression())) {
        return assign.getOperationTokenType() == JavaTokenType.EQ ? assign.getRExpression() : parent;
      }
      else {
        if (namedElement instanceof PsiParameter) { //filter usages in super method calls
          PsiElement gParent = refExpr.getParent().getParent();
          if (gParent instanceof PsiMethodCallExpression call && !canInvertReferenceElement(call.getMethodExpression(), true)) {
            return null;
          }
        }
        return refExpr;
      }
    }
    return null;
  }

  private static boolean canInvertReferenceElement(PsiElement expression, boolean recursive) {
    PsiExpression qualifierExpression = expression instanceof PsiReferenceExpression ref ? ref.getQualifierExpression() : null;
    if (qualifierExpression == null || !"super".equals(qualifierExpression.getText())) {
      PsiElement parent = expression.getParent();
      if (parent instanceof PsiMethodCallExpression call) {
        return !(recursive && RecursiveCallLineMarkerProvider.isRecursiveMethodCall(call));
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
    while (expression.getParent() instanceof PsiPrefixExpression prefix && prefix.getOperationTokenType() == JavaTokenType.EXCL) {
      expression = expression.getParent();
    }

    if (expression instanceof PsiMethodReferenceExpression ref) {
      final PsiExpression callExpression = LambdaRefactoringUtil.convertToMethodCallInLambdaBody(ref);
      if (callExpression instanceof PsiCallExpression) {
        PsiExpression negatedExpression = JavaPsiFacade.getElementFactory(callExpression.getProject())
          .createExpressionFromText(BoolUtils.getNegatedExpressionText(callExpression), callExpression);
        callExpression.replace(negatedExpression);
      }
    }
    else if (!(expression.getParent() instanceof PsiExpressionStatement) || isOperatorAssignment(expression)) {
      PsiExpression negatedExpression = JavaPsiFacade.getElementFactory(expression.getProject())
          .createExpressionFromText(BoolUtils.getNegatedExpressionText((PsiExpression)expression), expression);
      expression.replace(negatedExpression);
    }
  }

  private static boolean isOperatorAssignment(@NotNull PsiElement expression) {
    return expression instanceof PsiAssignmentExpression assignment && assignment.getOperationTokenType() != JavaTokenType.EQ;
  }

  @Override
  public void invertElementInitializer(final PsiElement element) {
    if (element instanceof PsiField field && field.getInitializer() == null && !field.hasModifierProperty(PsiModifier.FINAL)) {
      field.setInitializer(JavaPsiFacade.getElementFactory(element.getProject()).createExpressionFromText("true", element));
    }
    else if (element instanceof PsiVariable variable) {
      final PsiExpression initializer = variable.getInitializer();
      if (initializer != null) {
        replaceWithNegatedExpression(initializer);
      }
    }
    else if (element instanceof PsiMethod m) {
      updateContract(m);
    }
  }

  private static void updateContract(@NotNull PsiMethod method) {
    PsiAnnotation annotation = JavaMethodContractUtil.findContractAnnotation(method);
    if (annotation == null || annotation.getOwner() != method.getModifierList()) return;
    String text = AnnotationUtil.getStringAttributeValue(annotation, null);
    if (text == null || text.trim().isEmpty()) return;
    List<StandardMethodContract> contracts;
    try {
      contracts = StandardMethodContract.parseContract(text);
    }
    catch (StandardMethodContract.ParseException ignore) {
      return;
    }
    List<StandardMethodContract> newContracts = ContainerUtil.map(contracts, contract -> {
      ContractReturnValue value = contract.getReturnValue();
      return value instanceof ContractReturnValue.BooleanReturnValue v ? contract.withReturnValue(v.negate()) : contract;
    });
    if (newContracts.equals(contracts)) return;
    PsiAnnotation newAnnotation = JavaMethodContractUtil.updateContract(annotation, newContracts);
    if (newAnnotation != null) {
      annotation.replace(newAnnotation);
    }
  }

  @Override
  public void collectRefElements(final PsiElement element,
                                 final @Nullable RenameProcessor renameProcessor,
                                 final @NotNull String newName,
                                 final Collection<? super PsiElement> elementsToInvert) {
    collectRefsToInvert(element, elementsToInvert);

    if (element instanceof PsiRecordComponent component) {
      if (renameProcessor != null) {
        PsiMethod accessor = JavaPsiRecordUtil.getAccessorForRecordComponent(component);
        if (accessor != null) renameProcessor.addElement(accessor, newName);
      }

      PsiElement parent = component.getParent();
      if (parent instanceof PsiRecordHeader header) {
        PsiRecordComponent @NotNull [] components = header.getRecordComponents();
        int index = -1;
        for (int i = 0; i < components.length; i++) {
          if (component == components[i]) {
            index = i;
            break;
          }
        }
        if (index < 0) return;
        PsiClass recordClass = header.getContainingClass();
        if (recordClass == null) return;
        collectConstructorParameterReferences(JavaPsiRecordUtil.findCanonicalConstructor(recordClass), index, elementsToInvert);
      }
    }
    if (element instanceof PsiMethod method) {
      final Collection<PsiMethod> overriders = OverridingMethodsSearch.search(method).findAll();
      if (renameProcessor != null) {
        for (PsiMethod overrider : overriders) {
          renameProcessor.addElement(overrider, newName);
        }
      }

      Collection<PsiMethod> allMethods = new HashSet<>(overriders);
      allMethods.add(method);

      for (PsiMethod m : allMethods) {
        m.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
            final PsiExpression returnValue = statement.getReturnValue();
            if (returnValue != null && PsiTypes.booleanType().equals(returnValue.getType())) {
              elementsToInvert.add(returnValue);
            }
          }

          @Override public void visitClass(@NotNull PsiClass aClass) {}
          @Override public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {}
        });
      }
    }
    else if (element instanceof PsiParameter param && param.getDeclarationScope() instanceof PsiMethod method) {
      int index = method.getParameterList().getParameterIndex(param);
      assert index >= 0;
      for (PsiReference reference : MethodReferencesSearch.search(method).findAll()) {
        PsiElement parent = reference.getElement().getParent();
        if (parent instanceof PsiAnonymousClass) {
          parent = parent.getParent();
        }
        if (parent instanceof PsiCall call) {
          final PsiReferenceExpression methodExpression = call instanceof PsiMethodCallExpression m ? m.getMethodExpression() : null;
          final PsiExpressionList argumentList = call.getArgumentList();
          if (argumentList != null) {
            final PsiExpression[] args = argumentList.getExpressions();
            if (index < args.length) {
              if (methodExpression == null ||
                  canInvertReferenceElement(methodExpression,
                                            args[index] instanceof PsiReferenceExpression ref && ref.resolve() == element)) {
                elementsToInvert.add(args[index]);
              }
            }
          }
        }
      }

      for (PsiMethod overrider : OverridingMethodsSearch.search(method).findAll()) {
        final PsiParameter overriderParameter = overrider.getParameterList().getParameters()[index];
        if (renameProcessor != null) {
          renameProcessor.addElement(overriderParameter, newName);
        }
        collectRefsToInvert(overriderParameter, elementsToInvert);
      }
    }
  }

  private static void collectConstructorParameterReferences(PsiMethod constructor, int index, Collection<? super PsiElement> result) {
    if (constructor == null) return;
    for (PsiReference reference : MethodReferencesSearch.search(constructor).findAll()) {
      PsiElement refElement = reference.getElement().getParent();
      if (!(refElement instanceof PsiCall call)) continue;
      PsiExpressionList argumentList = call.getArgumentList();
      if (argumentList == null) continue;
      PsiExpression expression = argumentList.getExpressions()[index];
      if (isThisCall(refElement) && expression instanceof PsiReferenceExpression ref && ref.resolve() instanceof PsiParameter param) {
        PsiElement scope = param.getDeclarationScope();
        if (scope instanceof PsiMethod method) {
          int newIndex = method.getParameterList().getParameterIndex(param);
          collectConstructorParameterReferences(method, newIndex, result);
        }
      }
      else {
        result.add(expression);
      }
    }
  }

  private static boolean isThisCall(PsiElement element) {
    return element instanceof PsiMethodCallExpression call &&
           call.getMethodExpression().getLastChild() instanceof PsiKeyword keyword &&
           PsiUtil.isJavaToken(keyword, JavaTokenType.THIS_KEYWORD);
  }

  @Override
  public void findConflicts(UsageInfo[] usageInfos, MultiMap<PsiElement, String> conflicts) {
    for (UsageInfo info : usageInfos) {
      final PsiElement element = info.getElement();
      if (element instanceof PsiMethodReferenceExpression) {
        conflicts.putValue(element, JavaRefactoringBundle.message("expand.method.reference.warning"));
      }
    }
  }
}
