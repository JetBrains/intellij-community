// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.hierarchy.call;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CalleeMethodsTreeStructure extends HierarchyTreeStructure {
  private final String myScopeType;

  /**
   * Should be called in read action
   */
  public CalleeMethodsTreeStructure(@NotNull Project project, @NotNull PsiMember member, String scopeType) {
    super(project, new CallHierarchyNodeDescriptor(project, null, member, true, false));
    myScopeType = scopeType;
  }

  @Override
  protected Object @NotNull [] buildChildren(@NotNull HierarchyNodeDescriptor descriptor) {
    PsiMember enclosingElement = ((CallHierarchyNodeDescriptor)descriptor).getEnclosingElement();
    if (!(enclosingElement instanceof PsiMethod)) {
      return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }
    PsiMethod method = (PsiMethod)enclosingElement;

    List<PsiMethod> methods = new ArrayList<>();

    PsiCodeBlock body = method.getBody();
    if (body != null) {
      collectCallees(body, methods);
    }

    PsiMethod baseMethod = (PsiMethod)((CallHierarchyNodeDescriptor)getBaseDescriptor()).getTargetElement();
    PsiClass baseClass = baseMethod.getContainingClass();

    Map<PsiMethod,CallHierarchyNodeDescriptor> methodToDescriptorMap = new HashMap<>();

    List<CallHierarchyNodeDescriptor> result = new ArrayList<>();

    // also add overriding methods as children
    Iterable<PsiMethod> methodsToAdd = ContainerUtil.concat(methods, OverridingMethodsSearch.search(method));
    for (PsiMethod callee : methodsToAdd) {
      if (!isInScope(baseClass, callee, myScopeType)
        || JavaCallReferenceProcessor.isRecursiveNode(callee, descriptor)) {
        continue;
      }

      CallHierarchyNodeDescriptor d = methodToDescriptorMap.get(callee);
      if (d == null) {
        d = new CallHierarchyNodeDescriptor(myProject, descriptor, callee, false, false);
        methodToDescriptorMap.put(callee, d);
        result.add(d);
      }
      else {
        d.incrementUsageCount();
      }
    }

    return ArrayUtil.toObjectArray(result);
  }


  private static void collectCallees(@NotNull PsiElement element, @NotNull List<? super PsiMethod> methods) {
    PsiElement[] children = element.getChildren();
    for (PsiElement child : children) {
      collectCallees(child, methods);
      if (child instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression callExpression = (PsiMethodCallExpression)child;
        PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
        PsiMethod method = (PsiMethod)methodExpression.resolve();
        if (method != null) {
          methods.add(method);
        }
      }
      else if (child instanceof PsiNewExpression) {
        PsiNewExpression newExpression = (PsiNewExpression)child;
        PsiMethod method = newExpression.resolveConstructor();
        if (method != null) {
          methods.add(method);
        }
      }
      else if (child instanceof PsiMethodReferenceExpression) {
        PsiElement method = ((PsiMethodReferenceExpression)child).resolve();
        if (method instanceof PsiMethod) {
          methods.add((PsiMethod)method);
        }
      }
    }
  }
}
