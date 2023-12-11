/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.hierarchy.call;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMemberReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

public final class JavaCallReferenceProcessor implements CallReferenceProcessor {
  @Override
  public boolean process(@NotNull PsiReference reference, @NotNull JavaCallHierarchyData data) {
    PsiClass originalClass = data.getOriginalClass();
    PsiMethod method = data.getMethod();
    Set<? extends PsiMethod> methodsToFind = data.getMethodsToFind();
    PsiMethod methodToFind = data.getMethodToFind();
    PsiClassType originalType = data.getOriginalType();
    Map<PsiMember, NodeDescriptor<?>> methodToDescriptorMap = data.getResultMap();
    Project myProject = data.getProject();

    if (reference instanceof PsiReferenceExpression) {
      PsiExpression qualifier = ((PsiReferenceExpression)reference).getQualifierExpression();
      if (qualifier instanceof PsiSuperExpression) { // filter super.foo() call inside foo() and similar cases (bug 8411)
        PsiClass superClass = PsiUtil.resolveClassInType(qualifier.getType());
        if (superClass == null || originalClass.isInheritor(superClass, true)) {
          return false;
        }
      }
      if (qualifier != null && !methodToFind.hasModifierProperty(PsiModifier.STATIC)) {
        PsiType qualifierType = qualifier.getType();
        if (qualifierType instanceof PsiClassType &&
            !TypeConversionUtil.isAssignable(qualifierType, originalType) &&
            methodToFind != method) {
          PsiClass psiClass = ((PsiClassType)qualifierType).resolve();
          if (psiClass != null) {
            PsiMethod callee = psiClass.findMethodBySignature(methodToFind, true);
            if (callee != null && !methodsToFind.contains(callee)) {
              // skip sibling methods
              return false;
            }
          }
        }
      }
    }
    else {
      if (!(reference instanceof PsiElement)) {
        return true;
      }

      PsiElement parent = ((PsiElement)reference).getParent();
      if (parent instanceof PsiNewExpression) {
        if (((PsiNewExpression)parent).getClassReference() != reference) {
          return false;
        }
      }
      else if (parent instanceof PsiAnonymousClass) {
        if (((PsiAnonymousClass)parent).getBaseClassReference() != reference) {
          return false;
        }
      }
      else if (!(reference instanceof LightMemberReference)) {
        return true;
      }
    }

    PsiElement element = reference.getElement();
    PsiMember key = CallHierarchyNodeDescriptor.getEnclosingElement(element);
    CallHierarchyNodeDescriptor parentDescriptor = (CallHierarchyNodeDescriptor) data.getNodeDescriptor();
    if (isRecursiveNode(method, parentDescriptor)) return false;

    synchronized (methodToDescriptorMap) {
      CallHierarchyNodeDescriptor d = (CallHierarchyNodeDescriptor)methodToDescriptorMap.get(key);
      if (d == null) {
        d = new CallHierarchyNodeDescriptor(myProject, parentDescriptor, element, false, true);
        methodToDescriptorMap.put(key, d);
      }
      else if (!d.hasReference(reference)) {
        d.incrementUsageCount();
      }
      d.addReference(reference);
    }
    return false;
  }

  private static PsiMember getEnclosingElement(PsiElement element) {
    return PsiTreeUtil.getNonStrictParentOfType(element, PsiField.class, PsiMethod.class, PsiClass.class);
  }

  static boolean isRecursiveNode(@NotNull PsiMethod method, @NotNull HierarchyNodeDescriptor parentDescriptor) {
    // detect recursion
    // the current call-site calls *method*
    // Thus, we already have a node that represents *method*
    // Check whether we have any other node along the parent-chain that represents that same method

    NodeDescriptor<?> ancestorDescriptor = parentDescriptor;
    // Start check on grandparent
    while ((ancestorDescriptor = ancestorDescriptor.getParentDescriptor()) != null) {
      if (ancestorDescriptor instanceof HierarchyNodeDescriptor) {
        PsiMember ancestorCallSite = getEnclosingElement(((HierarchyNodeDescriptor)ancestorDescriptor).getPsiElement());
        if (ancestorCallSite == method) {
          // We have at least two occurrences in the parent chain of method already
          // Don't search any deeper
          return true;
        }
      }
    }
    return false;
  }
}
