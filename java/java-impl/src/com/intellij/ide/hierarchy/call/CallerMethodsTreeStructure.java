// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.hierarchy.call;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class CallerMethodsTreeStructure extends HierarchyTreeStructure {
  private final String myScopeType;

  /**
   * Should be called in read action
   */
  public CallerMethodsTreeStructure(@NotNull Project project, @NotNull PsiMember member, final String scopeType) {
    super(project, new CallHierarchyNodeDescriptor(project, null, member, true, false));
    myScopeType = scopeType;
  } 
  
  /**
   * @deprecated use CallerMethodsTreeStructure#CallerMethodsTreeStructure(Project, PsiMember, String)
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  public CallerMethodsTreeStructure(@NotNull Project project, @NotNull PsiMethod method, final String scopeType) {
    this(project, ((PsiMember)method), scopeType);
  }

  @NotNull
  @Override
  protected final Object[] buildChildren(@NotNull final HierarchyNodeDescriptor descriptor) {
    PsiMember enclosingElement = ((CallHierarchyNodeDescriptor)descriptor).getEnclosingElement();
    if (enclosingElement == null) return ArrayUtilRt.EMPTY_OBJECT_ARRAY;

    HierarchyNodeDescriptor nodeDescriptor = getBaseDescriptor();
    if (nodeDescriptor == null) return ArrayUtilRt.EMPTY_OBJECT_ARRAY;

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

    PsiMember baseMember = (PsiMember)((CallHierarchyNodeDescriptor)nodeDescriptor).getTargetElement();
    if (baseMember == null) return ArrayUtilRt.EMPTY_OBJECT_ARRAY;

    SearchScope searchScope = getSearchScope(myScopeType, baseMember.getContainingClass());

    PsiMember member = enclosingElement;
    PsiClass originalClass = member.getContainingClass();
    assert originalClass != null;
    PsiClassType originalType = JavaPsiFacade.getElementFactory(myProject).createType(originalClass);
    Set<PsiMethod> methodsToFind = new HashSet<>();

    if (enclosingElement instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)enclosingElement;
      methodsToFind.add(method);
      ContainerUtil.addAll(methodsToFind, method.findDeepestSuperMethods());

      Map<PsiMember, NodeDescriptor> methodToDescriptorMap = new HashMap<>();
      for (PsiMethod methodToFind : methodsToFind) {
        JavaCallHierarchyData data = new JavaCallHierarchyData(originalClass, methodToFind, originalType, method, methodsToFind, descriptor, methodToDescriptorMap, myProject);

        MethodReferencesSearch.search(methodToFind, searchScope, true).forEach(reference -> {
          for (CallReferenceProcessor processor : CallReferenceProcessor.EP_NAME.getExtensions()) {
            if (!processor.process(reference, data)) break;
          }
          return true;
        });
      }

      return ArrayUtil.toObjectArray(methodToDescriptorMap.values());
    }
    
    assert enclosingElement instanceof PsiField;

    Set<PsiMethod> methodsToFindForField = new HashSet<>();
    ReferencesSearch.search(enclosingElement, enclosingElement.getUseScope()).forEach(reference -> {
      PsiMethod method = PsiTreeUtil.getParentOfType(reference.getElement(), PsiMethod.class);
      if (method != null) methodsToFindForField.add(method);
    });

    return JBIterable.from(methodsToFindForField)
      .map(method -> new CallHierarchyNodeDescriptor(myProject, nodeDescriptor, method, false, false))
      .toArray(new CallHierarchyNodeDescriptor[]{});
  }

  private static boolean isLocalOrAnonymousClass(PsiMember enclosingElement) {
    return enclosingElement instanceof PsiClass && ((PsiClass)enclosingElement).getQualifiedName() == null;
  }

  @Override
  public String toString() {
    return "Caller Hierarchy for " + formatBaseElementText();
  }
}
