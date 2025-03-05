// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    if (!(enclosingElement instanceof PsiMethod method) || enclosingElement instanceof SyntheticElement) {
      return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }

    List<PsiMethod> methods = new ArrayList<>();
    PsiCodeBlock body = method.getBody();
    if (body != null) {
      collectCallees(body, methods);
    }

    PsiElement targetElement = ((CallHierarchyNodeDescriptor)getBaseDescriptor()).getTargetElement();
    PsiClass baseClass = (targetElement instanceof PsiMethod baseMethod) ? baseMethod.getContainingClass() : null;

    Map<PsiMethod, CallHierarchyNodeDescriptor> methodToDescriptorMap = new HashMap<>();
    List<CallHierarchyNodeDescriptor> result = new ArrayList<>();

    // also add overriding methods as children when possible
    Iterable<PsiMethod> allMethods = (baseClass == null) ? methods : ContainerUtil.concat(methods, OverridingMethodsSearch.search(method).asIterable());
    for (PsiMethod callee : allMethods) {
      if (baseClass != null && !isInScope(baseClass, callee, myScopeType)
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
      if (child instanceof PsiMethodCallExpression callExpression) {
        PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
        PsiMethod method = (PsiMethod)methodExpression.resolve();
        if (method != null) {
          methods.add(method);
        }
      }
      else if (child instanceof PsiNewExpression newExpression) {
        PsiMethod method = newExpression.resolveConstructor();
        if (method != null) {
          methods.add(method);
        }
      }
      else if (child instanceof PsiMethodReferenceExpression methodRef && methodRef.resolve() instanceof PsiMethod method) {
        methods.add(method);
      }
    }
  }
}
