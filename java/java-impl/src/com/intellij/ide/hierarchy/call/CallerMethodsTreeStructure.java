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
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class CallerMethodsTreeStructure extends HierarchyTreeStructure {
  private final String myScopeType;

  /**
   * Should be called in read action
   */
  public CallerMethodsTreeStructure(@NotNull Project project, @NotNull PsiMethod method, final String scopeType) {
    super(project, new CallHierarchyNodeDescriptor(project, null, method, true, false));
    myScopeType = scopeType;
  }

  @NotNull
  @Override
  protected final Object[] buildChildren(@NotNull final HierarchyNodeDescriptor descriptor) {
    PsiMember enclosingElement = ((CallHierarchyNodeDescriptor)descriptor).getEnclosingElement();
    HierarchyNodeDescriptor nodeDescriptor = getBaseDescriptor();

    if (enclosingElement instanceof PsiMethod) {
      PsiClass clazz = enclosingElement.getContainingClass();
      if (isLocalOrAnonymousClass(clazz)) {
        PsiElement parent = clazz.getParent();
        PsiElement grandParent = parent instanceof PsiNewExpression ? parent.getParent() : null;
        if (grandParent instanceof PsiExpressionList) {
          // for created anonymous class that immediately passed as argument use instantiation point as next call point (IDEA-73312)
          enclosingElement = CallHierarchyNodeDescriptor.getEnclosingElement(grandParent);
        }
      }
    }

    if (!(enclosingElement instanceof PsiMethod) || nodeDescriptor == null) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    final PsiMethod baseMethod = (PsiMethod)((CallHierarchyNodeDescriptor)nodeDescriptor).getTargetElement();
    if (baseMethod == null) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    final SearchScope searchScope = getSearchScope(myScopeType, baseMethod.getContainingClass());

    final PsiMethod method = (PsiMethod)enclosingElement;
    final PsiClass originalClass = method.getContainingClass();
    assert originalClass != null;
    final PsiClassType originalType = JavaPsiFacade.getElementFactory(myProject).createType(originalClass);
    final Set<PsiMethod> methodsToFind = new HashSet<>();
    methodsToFind.add(method);
    ContainerUtil.addAll(methodsToFind, method.findDeepestSuperMethods());

    final Map<PsiMember, NodeDescriptor> methodToDescriptorMap = new HashMap<>();
    for (final PsiMethod methodToFind : methodsToFind) {
      final JavaCallHierarchyData data = new JavaCallHierarchyData(originalClass, methodToFind, originalType, method, methodsToFind, descriptor, methodToDescriptorMap, myProject);

      MethodReferencesSearch.search(methodToFind, searchScope, true).forEach(reference -> {
        for (CallReferenceProcessor processor : CallReferenceProcessor.EP_NAME.getExtensions()) {
          if (!processor.process(reference, data)) break;
        }
        return true;
      });
    }

    return methodToDescriptorMap.values().toArray(new Object[methodToDescriptorMap.size()]);
  }

  private static boolean isLocalOrAnonymousClass(PsiMember enclosingElement) {
    return enclosingElement instanceof PsiClass && ((PsiClass)enclosingElement).getQualifiedName() == null;
  }

  @Override
  public boolean isAlwaysShowPlus() {
    return true;
  }
}
