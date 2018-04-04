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

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMemberReference;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

public class JavaCallReferenceProcessor implements CallReferenceProcessor {
  @Override
  public boolean process(@NotNull PsiReference reference, @NotNull JavaCallHierarchyData data) {
    PsiClass originalClass = data.getOriginalClass();
    PsiMethod method = data.getMethod();
    Set<PsiMethod> methodsToFind = data.getMethodsToFind();
    PsiMethod methodToFind = data.getMethodToFind();
    PsiClassType originalType = data.getOriginalType();
    Map<PsiMember, NodeDescriptor> methodToDescriptorMap = data.getResultMap();
    Project myProject = data.getProject();

    if (reference instanceof PsiReferenceExpression) {
      final PsiExpression qualifier = ((PsiReferenceExpression)reference).getQualifierExpression();
      if (qualifier instanceof PsiSuperExpression) { // filter super.foo() call inside foo() and similar cases (bug 8411)
        final PsiClass superClass = PsiUtil.resolveClassInType(qualifier.getType());
        if (superClass == null || originalClass.isInheritor(superClass, true)) {
          return false;
        }
      }
      if (qualifier != null && !methodToFind.hasModifierProperty(PsiModifier.STATIC)) {
        final PsiType qualifierType = qualifier.getType();
        if (qualifierType instanceof PsiClassType &&
            !TypeConversionUtil.isAssignable(qualifierType, originalType) &&
            methodToFind != method) {
          final PsiClass psiClass = ((PsiClassType)qualifierType).resolve();
          if (psiClass != null) {
            final PsiMethod callee = psiClass.findMethodBySignature(methodToFind, true);
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

      final PsiElement parent = ((PsiElement)reference).getParent();
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

    final PsiElement element = reference.getElement();
    final PsiMember key = CallHierarchyNodeDescriptor.getEnclosingElement(element);

    synchronized (methodToDescriptorMap) {
      CallHierarchyNodeDescriptor d = (CallHierarchyNodeDescriptor)methodToDescriptorMap.get(key);
      if (d == null) {
        d = new CallHierarchyNodeDescriptor(myProject, (CallHierarchyNodeDescriptor)data.getNodeDescriptor(), element, false, true);
        methodToDescriptorMap.put(key, d);
      }
      else if (!d.hasReference(reference)) {
        d.incrementUsageCount();
      }
      d.addReference(reference);
    }
    return false;
  }
}
