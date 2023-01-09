// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.changeSignature.MemberNodeBase;
import com.intellij.refactoring.changeSignature.inCallers.JavaCallerChooser;
import com.intellij.refactoring.changeSignature.inCallers.JavaMethodNode;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteParameterCallHierarchyUsageInfo;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceJavaDeleteUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.EmptyConsumer;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

abstract class SafeDeleteJavaCallerChooser extends JavaCallerChooser {
  private final PsiMethod myMethod;
  private final List<? super UsageInfo> myResult;

  SafeDeleteJavaCallerChooser(@NotNull PsiMethod method, @NotNull Project project, @NotNull List<? super UsageInfo> result) {
    super(method, project, JavaRefactoringBundle.message("safe.delete.select.methods.to.propagate.delete.parameters.dialog.title"), null, EmptyConsumer.getInstance());
    myMethod = method;
    myResult = result;
  }

  protected abstract @NotNull List<SafeDeleteParameterCallHierarchyUsageInfo> getTopLevelItems();
  protected abstract int getParameterIdx();

  @Override
  protected JavaMethodNode createTreeNodeFor(PsiMethod nodeMethod,
                                             HashSet<PsiMethod> called,
                                             Runnable cancelCallback) {
    final SafeDeleteJavaMethodNode node = new SafeDeleteJavaMethodNode(nodeMethod, called, cancelCallback, getParameterIdx(),
                                                                       nodeMethod != null ? nodeMethod.getProject() : myProject);
    if (getTopMember().equals(nodeMethod)) {
      node.setEnabled(false);
      node.setChecked(true);
    }
    return node;
  }

  @Override
  protected void doOKAction() {
    final List<UsageInfo> foreignMethodUsages = new ArrayList<>();
    final Runnable runnable = () -> {
      final Set<MemberNodeBase<PsiMethod>> nodes = getSelectedNodes();
      for (MemberNodeBase<PsiMethod> node : nodes) {
        final SafeDeleteJavaMethodNode methodNode = (SafeDeleteJavaMethodNode)node;
        final PsiMethod nodeMethod = methodNode.getMember();
        if (nodeMethod.equals(myMethod)) continue;
        int parameterIdx = methodNode.myParameterIdx;
        final PsiParameter parameter = nodeMethod.getParameterList().getParameters()[parameterIdx];
        foreignMethodUsages.add(new SafeDeleteParameterCallHierarchyUsageInfo(nodeMethod, parameter, nodeMethod, parameter));
        ReferencesSearch.search(nodeMethod).forEach(reference -> {
          final PsiElement element = reference.getElement();
          JavaSafeDeleteDelegate safeDeleteDelegate = JavaSafeDeleteDelegate.EP.forLanguage(element.getLanguage());
          if (safeDeleteDelegate != null) {
            safeDeleteDelegate.createUsageInfoForParameter(reference, foreignMethodUsages, parameter, parameterIdx, parameter.isVarArgs());
          }
          return true;
        });

        ReferencesSearch.search(parameter).forEach(reference -> {
          PsiElement element = reference.getElement();
          final PsiDocTag docTag = PsiTreeUtil.getParentOfType(element, PsiDocTag.class);
          if (docTag != null) {
            foreignMethodUsages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(docTag, parameter, true));
          }
          return true;
        });
      }
    };

    if (ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> ApplicationManager.getApplication().runReadAction(runnable),
                                                                          JavaRefactoringBundle.message(
                                                                            "safe.delete.search.for.caller.method.usages.progress"), true, myProject)) {
      myResult.addAll(foreignMethodUsages);
    }
    super.doOKAction();
  }

  /**
   * @return parameter if it is used inside method only as argument in nodeMethod call at parameterIndex
   */
  static PsiParameter isTheOnlyOneParameterUsage(PsiElement call, final int parameterIndex, final PsiMethod nodeMethod) {
    if (call instanceof PsiCallExpression) {
      final PsiExpressionList argumentList = ((PsiCallExpression)call).getArgumentList();
      if (argumentList != null) {
        final PsiExpression[] expressions = argumentList.getExpressions();
        if (expressions.length > parameterIndex) {
          final PsiExpression expression = PsiUtil.deparenthesizeExpression(expressions[parameterIndex]);
          if (expression != null) {
            final Set<PsiParameter> paramRefs = new HashSet<>();
            expression.accept(new JavaRecursiveElementWalkingVisitor() {
              @Override
              public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
                super.visitReferenceExpression(expression);
                final PsiElement resolve = expression.resolve();
                if (resolve instanceof PsiParameter) {
                  paramRefs.add((PsiParameter)resolve);
                }
              }
            });

            final PsiParameter parameter = ContainerUtil.getFirstItem(paramRefs);
            if (parameter != null && !parameter.isVarArgs()) {
              final PsiElement scope = parameter.getDeclarationScope();
              if (scope instanceof PsiMethod && ((PsiMethod)scope).findDeepestSuperMethods().length == 0 &&
                  OverridingMethodsSearch.search((PsiMethod)scope).findFirst() == null) {
                final int scopeParamIdx = ((PsiMethod)scope).getParameterList().getParameterIndex(parameter);
                final Ref<Boolean> ref = new Ref<>(false);
                if (ReferencesSearch.search(parameter, new LocalSearchScope(scope)).forEach(new Processor<>() {
                  @Override
                  public boolean process(PsiReference reference) {
                    final PsiElement element = reference.getElement();
                    if (element instanceof PsiReferenceExpression) {
                      PsiCallExpression parent = PsiTreeUtil.getParentOfType(element, PsiCallExpression.class);
                      while (parent != null) {
                        final PsiMethod resolved = parent.resolveMethod();
                        if (scope.equals(resolved)) {
                          if (usedInQualifier(element, parent, scopeParamIdx)) return false;
                          return true;
                        }
                        if (nodeMethod.equals(resolved)) {
                          if (usedInQualifier(element, parent, parameterIndex)) return false;
                          ref.set(true);
                          return true;
                        }
                        parent = PsiTreeUtil.getParentOfType(parent, PsiCallExpression.class, true);
                      }
                      return false;
                    }
                    return true;
                  }

                  private static boolean usedInQualifier(PsiElement element, PsiCallExpression parent, int parameterIndex) {
                    PsiExpression qualifier = null;
                    if (parent instanceof PsiMethodCallExpression) {
                      qualifier = ((PsiMethodCallExpression)parent).getMethodExpression();
                    }
                    else if (parent instanceof PsiNewExpression) {
                      qualifier = ((PsiNewExpression)parent).getQualifier();
                    }

                    if (PsiTreeUtil.isAncestor(qualifier, element, true)) {
                      return true;
                    }

                    final PsiExpressionList list = parent.getArgumentList();
                    return list != null && !PsiTreeUtil.isAncestor(list.getExpressions()[parameterIndex], element, false);
                  }
                }) && ref.get()) {
                  return parameter;
                }
              }
            }
          }
        }
      }
    }
    return null;
  }

  protected PsiParameter getParameterInCaller(PsiMethod called, int paramIdx, PsiMethod caller) {
    //do not change hierarchy
    if (caller.findDeepestSuperMethods().length > 0) {
      return null;
    }

    //find first method call
    final Ref<PsiParameter> ref = new Ref<>();
    ReferencesSearch.search(called, new LocalSearchScope(caller)).forEach(reference -> {
      final PsiElement element = reference.getElement();
      if (element instanceof PsiJavaCodeReferenceElement) {
        final PsiElement elementParent = element.getParent();
        if (elementParent instanceof PsiCallExpression) {
          ref.set(isTheOnlyOneParameterUsage(elementParent, paramIdx, called));
          return false;
        }
      }
      return true;
    });
    return ref.get();
  }

  private int getCallerParameterIndex(PsiMethod called, int paramIdx, PsiMethod caller) {
    final PsiParameter parameter = getParameterInCaller(called, paramIdx, caller);
    return parameter != null ? caller.getParameterList().getParameterIndex(parameter) : -1;
  }
  
  private class SafeDeleteJavaMethodNode extends JavaMethodNode {

    private final int myParameterIdx;

    SafeDeleteJavaMethodNode(PsiMethod currentMethod,
                                    HashSet<PsiMethod> called,
                                    Runnable cancelCallback,
                                    int idx,
                                    Project project) {
      super(currentMethod, called, project, cancelCallback);
      myParameterIdx = idx;
    }

    @Override
    protected MemberNodeBase<PsiMethod> createNode(PsiMethod caller, HashSet<PsiMethod> called) {
      return new SafeDeleteJavaMethodNode(caller, called, myCancelCallback, getCallerParameterIndex(myMethod, myParameterIdx, caller), myProject);
    }

    @Override
    protected List<PsiMethod> computeCallers() {
      if (getTopMember().equals(getMember())) {
        List<SafeDeleteParameterCallHierarchyUsageInfo> items = getTopLevelItems();
        return ContainerUtil.map(items, info -> info.getCallerMethod());
      }
      final List<PsiMethod> methods = super.computeCallers();
      methods.remove(getTopMember());
      return methods;
    }

    @Override
    protected Condition<PsiMethod> getFilter() {
      return method -> !myMethod.equals(method) && getParameterInCaller(myMethod, myParameterIdx, method) != null;
    }
    
  }
}
