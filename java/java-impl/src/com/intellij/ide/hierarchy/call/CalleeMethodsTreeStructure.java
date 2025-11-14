// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.hierarchy.call;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightDefaultConstructor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    PsiElement targetElement = ((CallHierarchyNodeDescriptor)getBaseDescriptor()).getTargetElement();
    PsiElement base = (targetElement instanceof PsiMethod baseMethod) ? baseMethod.getContainingClass() : targetElement;
    PsiMember enclosingElement = ((CallHierarchyNodeDescriptor)descriptor).getEnclosingElement();
    if (enclosingElement instanceof LightDefaultConstructor constructor) {
      PsiMethod superConstructor = findNoArgSuperConstructor(constructor);
      return superConstructor != null && isInScope(base, superConstructor, myScopeType)
             ? new Object[]{new CallHierarchyNodeDescriptor(myProject, descriptor, superConstructor, false, false)}
             : ArrayUtilRt.EMPTY_OBJECT_ARRAY; 
    }
    if (!(enclosingElement instanceof PsiMethod method) || enclosingElement instanceof SyntheticElement) {
      return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }

    List<PsiMethod> methods = new ArrayList<>();
    PsiCodeBlock body = method.getBody();
    if (body != null) {
      collectCallees(body, methods);
    }
    if (method.isConstructor() && JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(method) == null) {
      PsiMethod superConstructor = findNoArgSuperConstructor(method);
      if (superConstructor != null) methods.add(superConstructor);
    }

    Map<PsiMethod, CallHierarchyNodeDescriptor> methodToDescriptorMap = new HashMap<>();
    List<CallHierarchyNodeDescriptor> result = new ArrayList<>();

    // also add overriding methods as children
    SearchScope scope = getSearchScope(myScopeType, base);
    Iterable<PsiMethod> allMethods = ContainerUtil.concat(methods, OverridingMethodsSearch.search(method, scope, true).findAll());
    for (PsiMethod callee : allMethods) {
      if (!isInScope(base, callee, myScopeType) || JavaCallReferenceProcessor.isRecursiveNode(callee, descriptor)) {
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

  private static @Nullable PsiMethod findNoArgSuperConstructor(PsiMethod method) {
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;
    PsiClass superClass = aClass.getSuperClass();
    if (superClass == null) return null;
    PsiMethod[] constructors = superClass.getConstructors();
    if (constructors.length == 0) {
      return LightDefaultConstructor.create(superClass);
    }
    else {
      for (PsiMethod constructor : constructors) {
        if (constructor.getParameterList().isEmpty()) {
          return constructor;
        }
      }
    }
    return null;
  }

  private static void collectCallees(@NotNull PsiElement element, @NotNull List<? super PsiMethod> methods) {
    for (PsiElement child : element.getChildren()) {
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
