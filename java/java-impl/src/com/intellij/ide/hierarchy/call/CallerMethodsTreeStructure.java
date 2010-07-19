/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class CallerMethodsTreeStructure extends HierarchyTreeStructure {
  private final String myScopeType;

  /**
   * Should be called in read action
   */
  public CallerMethodsTreeStructure(final Project project, final PsiMethod method, final String scopeType) {
    super(project, new CallHierarchyNodeDescriptor(project, null, method, true, false));
    myScopeType = scopeType;
  }

  protected final Object[] buildChildren(final HierarchyNodeDescriptor descriptor) {
    final PsiMember enclosingElement = ((CallHierarchyNodeDescriptor)descriptor).getEnclosingElement();
    if (!(enclosingElement instanceof PsiMethod)) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    final PsiMethod method = (PsiMethod)enclosingElement;
    final PsiMethod baseMethod = (PsiMethod)((CallHierarchyNodeDescriptor)getBaseDescriptor()).getTargetElement();
    final SearchScope searchScope = getSearchScope(myScopeType, baseMethod.getContainingClass());

    final PsiClass originalClass = method.getContainingClass();
    assert originalClass != null;
    final PsiClassType originalType = JavaPsiFacade.getElementFactory(myProject).createType(originalClass);
    final Set<PsiMethod> methodsToFind = new HashSet<PsiMethod>();
    methodsToFind.add(method);
    ContainerUtil.addAll(methodsToFind, method.findDeepestSuperMethods());

    final Map<PsiMember, CallHierarchyNodeDescriptor> methodToDescriptorMap = new HashMap<PsiMember, CallHierarchyNodeDescriptor>();
    for (final PsiMethod methodToFind : methodsToFind) {
      MethodReferencesSearch.search(methodToFind, searchScope, true).forEach(new Processor<PsiReference>() {
        public boolean process(final PsiReference reference) {
          if (reference instanceof PsiReferenceExpression) {
            final PsiExpression qualifier = ((PsiReferenceExpression)reference).getQualifierExpression();
            if (qualifier instanceof PsiSuperExpression) { // filter super.foo() call inside foo() and similar cases (bug 8411)
              final PsiClass superClass = PsiUtil.resolveClassInType(qualifier.getType());
              if (originalClass.isInheritor(superClass, true)) {
                return true;
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
                    return true;
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
                return true;
              }
            }
            else if (parent instanceof PsiAnonymousClass) {
              if (((PsiAnonymousClass)parent).getBaseClassReference() != reference) {
                return true;
              }
            }
            else {
              return true;
            }
          }

          final PsiElement element = reference.getElement();
          final PsiMember key = CallHierarchyNodeDescriptor.getEnclosingElement(element);

          synchronized (methodToDescriptorMap) {
            CallHierarchyNodeDescriptor d = methodToDescriptorMap.get(key);
            if (d == null) {
              d = new CallHierarchyNodeDescriptor(myProject, descriptor, element, false, true);
              methodToDescriptorMap.put(key, d);
            }
            else if (!d.hasReference(reference)) {
              d.incrementUsageCount();
            }
            d.addReference(reference);
          }
          return true;
        }
      });
    }

    return methodToDescriptorMap.values().toArray(new Object[methodToDescriptorMap.size()]);
  }

  @Override
  public boolean isAlwaysShowPlus() {
    return true;
  }
}
