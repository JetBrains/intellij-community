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
package com.intellij.refactoring.safeDelete;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.changeSignature.MethodNodeBase;
import com.intellij.refactoring.changeSignature.inCallers.JavaCallerChooser;
import com.intellij.refactoring.changeSignature.inCallers.JavaMethodNode;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteParameterCallHierarchyUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;

import java.util.*;

abstract class SafeDeleteJavaCallerChooser extends JavaCallerChooser {
  private final PsiMethod myMethod;
  private final Project myProject;
  private final ArrayList<UsageInfo> myResult;

  public SafeDeleteJavaCallerChooser(PsiMethod method, Project project, ArrayList<UsageInfo> result) {
    super(method, project, "Select Methods To Propagate Parameter Deletion", null, Consumer.EMPTY_CONSUMER);
    myMethod = method;
    myProject = project;
    myResult = result;
  }

  protected abstract ArrayList<SafeDeleteParameterCallHierarchyUsageInfo> getTopLevelItems();
  protected abstract int getParameterIdx();

  @Override
  protected JavaMethodNode createTreeNode(PsiMethod nodeMethod,
                                          com.intellij.util.containers.HashSet<PsiMethod> called,
                                          Runnable cancelCallback) {
    final SafeDeleteJavaMethodNode node = new SafeDeleteJavaMethodNode(nodeMethod, called, cancelCallback, getParameterIdx(),
                                                                       nodeMethod != null ? nodeMethod.getProject() : myProject);
    if (getTopMethod().equals(nodeMethod)) {
      node.setEnabled(false);
      node.setChecked(true);
    }
    return node;
  }

  @Override
  protected void doOKAction() {
    final List<UsageInfo> foreignMethodUsages = new ArrayList<>();
    final Runnable runnable = () -> {
      final Set<MethodNodeBase<PsiMethod>> nodes = getSelectedNodes();
      for (MethodNodeBase<PsiMethod> node : nodes) {
        final SafeDeleteJavaMethodNode methodNode = (SafeDeleteJavaMethodNode)node;
        final PsiMethod nodeMethod = methodNode.getMethod();
        if (nodeMethod.equals(myMethod)) continue;
        final PsiParameter parameter = nodeMethod.getParameterList().getParameters()[methodNode.myParameterIdx];
        foreignMethodUsages.add(new SafeDeleteParameterCallHierarchyUsageInfo(nodeMethod, parameter, nodeMethod));
        ReferencesSearch.search(nodeMethod).forEach(reference -> {
          final PsiElement element = reference.getElement();
          if (element != null) {
            JavaSafeDeleteDelegate.EP.forLanguage(element.getLanguage())
              .createUsageInfoForParameter(reference, foreignMethodUsages, parameter, nodeMethod);
          }
          return true;
        });
      }
    };

    if (ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> ApplicationManager.getApplication().runReadAction(runnable), "Search for Caller Method Usages...", true, myProject)) {
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
              public void visitReferenceExpression(PsiReferenceExpression expression) {
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
              if (scope instanceof PsiMethod && ((PsiMethod)scope).findDeepestSuperMethods().length == 0) {
                final int scopeParamIdx = ((PsiMethod)scope).getParameterList().getParameterIndex(parameter);
                final Ref<Boolean> ref = new Ref<>(false);
                if (ReferencesSearch.search(parameter, new LocalSearchScope(scope)).forEach(new Processor<PsiReference>() {
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

                  private boolean usedInQualifier(PsiElement element, PsiCallExpression parent, int parameterIndex) {
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

  private class SafeDeleteJavaMethodNode extends JavaMethodNode {

    private final int myParameterIdx;

    public SafeDeleteJavaMethodNode(PsiMethod currentMethod,
                                    HashSet<PsiMethod> called,
                                    Runnable cancelCallback,
                                    int idx,
                                    Project project) {
      super(currentMethod, called, project, cancelCallback);
      myParameterIdx = idx;
    }

    @Override
    protected MethodNodeBase<PsiMethod> createNode(PsiMethod caller, HashSet<PsiMethod> called) {
      return new SafeDeleteJavaMethodNode(caller, called, myCancelCallback, getParameterIndex(caller), myProject);
    }

    @Override
    protected List<PsiMethod> computeCallers() {
      if (getTopMethod().equals(getMethod())) {
        final ArrayList<SafeDeleteParameterCallHierarchyUsageInfo> items = getTopLevelItems();
        return ContainerUtil.map(items, info -> info.getCallerMethod());
      }
      final List<PsiMethod> methods = super.computeCallers();
      methods.remove(getTopMethod());
      return methods;
    }

    @Override
    protected Condition<PsiMethod> getFilter() {
      return method -> !myMethod.equals(method) && getParameter(method) != null;
    }

    private PsiParameter getParameter(PsiMethod caller) {

      //do not change hierarchy
      if (caller.findDeepestSuperMethods().length > 0) {
        return null;
      }

      //find first method call
      final Ref<PsiParameter> ref = new Ref<>();
      ReferencesSearch.search(myMethod, new LocalSearchScope(caller)).forEach(reference -> {
        final PsiElement element = reference.getElement();
        if (element instanceof PsiReferenceExpression) {
          final PsiElement elementParent = element.getParent();
          if (elementParent instanceof PsiCallExpression) {
            ref.set(isTheOnlyOneParameterUsage(elementParent, myParameterIdx, myMethod));
            return false;
          }
        }
        return true;
      });
      return ref.get();
    }

    private int getParameterIndex(PsiMethod caller) {
      final PsiParameter parameter = getParameter(caller);
      return parameter != null ? caller.getParameterList().getParameterIndex(parameter) : -1;
    }
  }
}
