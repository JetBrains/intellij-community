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
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.changeSignature.MethodNodeBase;
import com.intellij.refactoring.changeSignature.inCallers.JavaCallerChooser;
import com.intellij.refactoring.changeSignature.inCallers.JavaMethodNode;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteParameterCallHierarchyUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class SafeDeleteJavaCallerChooser extends JavaCallerChooser {
  private final PsiMethod myMethod;
  private final Project myProject;
  private final int myParameterIndex;
  private final ArrayList<UsageInfo> myResult;

  public SafeDeleteJavaCallerChooser(PsiMethod method, Project project, int parameterIndex, ArrayList<UsageInfo> result) {
    super(method, project, "Select Methods To Propagate Parameter Deletion", null, Consumer.EMPTY_CONSUMER);
    myMethod = method;
    myProject = project;
    myParameterIndex = parameterIndex;
    myResult = result;
  }

  @Override
  protected JavaMethodNode createTreeNode(PsiMethod nodeMethod,
                                          com.intellij.util.containers.HashSet<PsiMethod> called,
                                          Runnable cancelCallback) {
    return new SafeDeleteJavaMethodNode(nodeMethod, called, cancelCallback, myParameterIndex, nodeMethod != null ? nodeMethod.getProject() : myProject);
  }

  @Override
  protected void doOKAction() {
    final List<UsageInfo> foreignMethodUsages = new ArrayList<UsageInfo>();
    final Runnable runnable = new Runnable() {
      public void run() {
        final Set<MethodNodeBase<PsiMethod>> nodes = getSelectedNodes();
        for (MethodNodeBase<PsiMethod> node : nodes) {
          final SafeDeleteJavaMethodNode methodNode = (SafeDeleteJavaMethodNode)node;
          final PsiMethod nodeMethod = methodNode.myCurrentMethod;
          if (nodeMethod.equals(myMethod)) continue;
          final PsiParameter parameter = nodeMethod.getParameterList().getParameters()[methodNode.myParameterIdx];
          foreignMethodUsages.add(new SafeDeleteParameterCallHierarchyUsageInfo(nodeMethod, parameter));
          ReferencesSearch.search(nodeMethod).forEach(new Processor<PsiReference>() {
            public boolean process(final PsiReference reference) {
              final PsiElement element = reference.getElement();
              if (element != null) {
                JavaSafeDeleteDelegate.EP.forLanguage(element.getLanguage())
                  .createUsageInfoForParameter(reference, foreignMethodUsages, parameter, nodeMethod);
              }
              return true;
            }
          });
        }
      }
    };

    if (ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
                                                                            @Override
                                                                            public void run() {
                                                                              ApplicationManager.getApplication().runReadAction(runnable);
                                                                            }
                                                                          }, "Search for caller method usages...", true, myProject)) {
      myResult.addAll(foreignMethodUsages);
    }
    super.doOKAction();
  }

  /**
   * @return parameter if it is used inside method only as argument in nodeMethod call at parameterIndex
   */
  static PsiParameter isTheOnlyOneParameterUsage(PsiElement call, int parameterIndex, final PsiMethod nodeMethod) {
    if (call instanceof PsiCallExpression) {
      final PsiExpressionList argumentList = ((PsiCallExpression)call).getArgumentList();
      if (argumentList != null) {
        final PsiExpression[] expressions = argumentList.getExpressions();
        if (expressions.length > parameterIndex) {
          final PsiExpression expression = PsiUtil.deparenthesizeExpression(expressions[parameterIndex]);
          if (expression instanceof PsiReferenceExpression) {
            final PsiElement resolve = ((PsiReferenceExpression)expression).resolve();
            if (resolve instanceof PsiParameter && !((PsiParameter)resolve).isVarArgs()) {
              final PsiElement scope = ((PsiParameter)resolve).getDeclarationScope();
              if (scope instanceof PsiMethod) {
                if (ReferencesSearch.search(resolve, new LocalSearchScope(scope)).forEach(new Processor<PsiReference>() {
                  @Override
                  public boolean process(PsiReference reference) {
                    final PsiElement element = reference.getElement();
                    if (element instanceof PsiReferenceExpression) {
                      final PsiElement parent = element.getParent();
                      if (parent instanceof PsiExpressionList) {
                        final PsiElement gParent = parent.getParent();
                        if (gParent instanceof PsiCallExpression &&
                            nodeMethod.equals(((PsiCallExpression)gParent).resolveMethod())) {
                          return true;
                        }
                      }
                    }
                    return false;
                  }
                })) {
                  return (PsiParameter)resolve;
                }
              }
            }
          }
        }
      }
    }
    return null;
  }

  private static class SafeDeleteJavaMethodNode extends JavaMethodNode {

    private final PsiMethod myCurrentMethod;
    private final int myParameterIdx;

    public SafeDeleteJavaMethodNode(PsiMethod currentMethod,
                                    HashSet<PsiMethod> called,
                                    Runnable cancelCallback,
                                    int idx,
                                    Project project) {
      super(currentMethod, called, project, cancelCallback);
      myCurrentMethod = currentMethod;
      myParameterIdx = idx;
    }

    @Override
    protected MethodNodeBase<PsiMethod> createNode(PsiMethod caller, HashSet<PsiMethod> called) {
      return new SafeDeleteJavaMethodNode(caller, called, myCancelCallback, getParameterIndex(caller), myProject);
    }

    @Override
    protected Condition<PsiMethod> getFilter() {
      return new Condition<PsiMethod>() {
        @Override
        public boolean value(PsiMethod method) {
          return getParameter(method) != null;
        }
      };
    }

    private PsiParameter getParameter(PsiMethod caller) {

      //do not change hierarchy
      if (caller.findDeepestSuperMethods().length > 0) {
        return null;
      }

      //find first method call
      final Ref<PsiParameter> ref = new Ref<PsiParameter>();
      ReferencesSearch.search(myCurrentMethod, new LocalSearchScope(caller)).forEach(new Processor<PsiReference>() {
        @Override
        public boolean process(PsiReference reference) {
          final PsiElement element = reference.getElement();
          if (element instanceof PsiReferenceExpression) {
            final PsiElement elementParent = element.getParent();
            if (elementParent instanceof PsiCallExpression) {
              ref.set(isTheOnlyOneParameterUsage(elementParent, myParameterIdx, myCurrentMethod));
              return false;
            }
          }
          return true;
        }
      });
      return ref.get();
    }

    private int getParameterIndex(PsiMethod caller) {
      final PsiParameter parameter = getParameter(caller);
      return parameter != null ? caller.getParameterList().getParameterIndex(parameter) : -1;
    }
  }
}
