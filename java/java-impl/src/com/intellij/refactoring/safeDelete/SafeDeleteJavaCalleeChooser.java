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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.MethodNodeBase;
import com.intellij.refactoring.changeSignature.inCallers.JavaCallerChooser;
import com.intellij.refactoring.changeSignature.inCallers.JavaMethodNode;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteMethodCalleeUsageInfo;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceJavaDeleteUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;

abstract class SafeDeleteJavaCalleeChooser extends JavaCallerChooser {
  private final Project myProject;

  public SafeDeleteJavaCalleeChooser(final PsiMethod method, Project project, final ArrayList<UsageInfo> result) {
    super(method, project, "Select Methods To Cascade Safe Delete", null, methods -> result.addAll(ContainerUtil.map(methods, m -> {
      return new SafeDeleteReferenceJavaDeleteUsageInfo(m, m, true);
    })));
    myProject = project;
  }

  @Nullable
  static List<PsiMethod> computeCalleesSafeToDelete(final PsiMethod psiMethod) {
    final PsiCodeBlock body = psiMethod.getBody();
    if (body != null) {
      final PsiClass containingClass = psiMethod.getContainingClass();
      if (containingClass != null) {
        final Set<PsiMethod> methodsToCheck = new HashSet<>();
        body.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            ContainerUtil.addAllNotNull(methodsToCheck, expression.resolveMethod());
          }
        });

        return ContainerUtil.filter(methodsToCheck, m -> containingClass.equals(m.getContainingClass()) &&
                                                     !psiMethod.equals(m) &&
               m.findDeepestSuperMethods().length == 0 &&
                                                     ReferencesSearch.search(m).forEach(new CommonProcessors.CollectProcessor<PsiReference>() {
                 @Override
                 public boolean process(PsiReference reference) {
                   final PsiElement element = reference.getElement();
                   return PsiTreeUtil.isAncestor(psiMethod, element, true) ||
                          PsiTreeUtil.isAncestor(m, element, true);
                 }
               }));
      }
    }
    return null;
  }

  protected abstract ArrayList<SafeDeleteMethodCalleeUsageInfo> getTopLevelItems();

  @Override
  protected JavaMethodNode createTreeNode(PsiMethod nodeMethod,
                                          com.intellij.util.containers.HashSet<PsiMethod> callees,
                                          Runnable cancelCallback) {
    final SafeDeleteJavaMethodNode node = new SafeDeleteJavaMethodNode(nodeMethod, callees, cancelCallback, nodeMethod != null ? nodeMethod.getProject() : myProject);
    if (getTopMethod().equals(nodeMethod)) {
      node.setEnabled(false);
      node.setChecked(true);
    }
    return node;
  }

  @Override
  protected MethodNodeBase<PsiMethod> getCalleeNode(MethodNodeBase<PsiMethod> node) {
    return node;
  }

  @Override
  protected MethodNodeBase<PsiMethod> getCallerNode(MethodNodeBase<PsiMethod> node) {
    return (MethodNodeBase<PsiMethod>)node.getParent();
  }

  private class SafeDeleteJavaMethodNode extends JavaMethodNode {

    public SafeDeleteJavaMethodNode(PsiMethod currentMethod,
                                    HashSet<PsiMethod> callees,
                                    Runnable cancelCallback,
                                    Project project) {
      super(currentMethod, callees, project, cancelCallback);
      
    }

    @Override
    protected MethodNodeBase<PsiMethod> createNode(PsiMethod caller, HashSet<PsiMethod> callees) {
      return new SafeDeleteJavaMethodNode(caller, callees, myCancelCallback, myProject);
    }

    @Override
    protected List<PsiMethod> computeCallers() {
      if (getTopMethod().equals(getMethod())) {
        return ContainerUtil.map(getTopLevelItems(), info -> info.getCalledMethod());
      }

      final List<PsiMethod> callees = computeCalleesSafeToDelete(getMethod());
      if (callees != null) {
        callees.remove(getTopMethod());
        return callees;
      }
      else {
        return Collections.<PsiMethod>emptyList();
      }
    }

    @Override
    protected Condition<PsiMethod> getFilter() {
      return method -> !myMethod.equals(method);
    }
  }
}