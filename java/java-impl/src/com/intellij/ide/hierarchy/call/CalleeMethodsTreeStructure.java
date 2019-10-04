// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.hierarchy.call;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
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
  public CalleeMethodsTreeStructure(@NotNull Project project, @NotNull PsiMember member, final String scopeType) {
    super(project, new CallHierarchyNodeDescriptor(project, null, member, true, false));
    myScopeType = scopeType;
  }
  
  /**
   * @deprecated use CalleeMethodsTreeStructure#CalleeMethodsTreeStructure(Project, PsiMember, String)
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  public CalleeMethodsTreeStructure(@NotNull Project project, @NotNull PsiMethod method, final String scopeType) {
    this(project, ((PsiMember)method), scopeType);
  }

  @Override
  @NotNull
  protected final Object[] buildChildren(@NotNull final HierarchyNodeDescriptor descriptor) {
    final PsiMember enclosingElement = ((CallHierarchyNodeDescriptor)descriptor).getEnclosingElement();
    if (!(enclosingElement instanceof PsiMethod)) {
      return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }
    final PsiMethod method = (PsiMethod)enclosingElement;

    List<PsiMethod> methods = new ArrayList<>();

    final PsiCodeBlock body = method.getBody();
    if (body != null) {
      visitor(body, methods);
    }

    final PsiMethod baseMethod = (PsiMethod)((CallHierarchyNodeDescriptor)getBaseDescriptor()).getTargetElement();
    final PsiClass baseClass = baseMethod.getContainingClass();

    Map<PsiMethod,CallHierarchyNodeDescriptor> methodToDescriptorMap = new HashMap<>();

    List<CallHierarchyNodeDescriptor> result = new ArrayList<>();

    // also add overriding methods as children
    Iterable<PsiMethod> methodsToAdd = ContainerUtil.concat(methods, OverridingMethodsSearch.search(method));
    for (final PsiMethod calledMethod : methodsToAdd) {
      if (!isInScope(baseClass, calledMethod, myScopeType)) continue;

      CallHierarchyNodeDescriptor d = methodToDescriptorMap.get(calledMethod);
      if (d == null) {
        d = new CallHierarchyNodeDescriptor(myProject, descriptor, calledMethod, false, false);
        methodToDescriptorMap.put(calledMethod, d);
        result.add(d);
      }
      else {
        d.incrementUsageCount();
      }
    }

    return ArrayUtil.toObjectArray(result);
  }


  private static void visitor(final PsiElement element, List<? super PsiMethod> methods) {
    final PsiElement[] children = element.getChildren();
    for (final PsiElement child : children) {
      visitor(child, methods);
      if (child instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression callExpression = (PsiMethodCallExpression)child;
        final PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
        final PsiMethod method = (PsiMethod)methodExpression.resolve();
        if (method != null) {
          methods.add(method);
        }
      }
      else if (child instanceof PsiNewExpression) {
        final PsiNewExpression newExpression = (PsiNewExpression)child;
        final PsiMethod method = newExpression.resolveConstructor();
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
