// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.hierarchy.call;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class CallerMethodsTreeStructure extends HierarchyTreeStructure {
  private final String myScopeType;

  /**
   * Should be called in read action
   */
  public CallerMethodsTreeStructure(@NotNull Project project, @NotNull PsiMember member, String scopeType) {
    super(project, new CallHierarchyNodeDescriptor(project, null, member, true, false));
    myScopeType = scopeType;
  }

  @Override
  protected Object @NotNull [] buildChildren(@NotNull HierarchyNodeDescriptor descriptor) {
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
    
    if (originalClass == null) return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    
    PsiClassType originalType = JavaPsiFacade.getElementFactory(myProject).createType(originalClass);

    if (enclosingElement instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)enclosingElement;

      Map<PsiMember, NodeDescriptor<?>> methodToDescriptorMap = new ConcurrentHashMap<>();
      JavaCallHierarchyData data = new JavaCallHierarchyData(originalClass, method, originalType, method, Set.of(method), descriptor, methodToDescriptorMap, myProject);

      MethodReferencesSearch.search(method, searchScope, true).forEach(reference -> {
        // references in javadoc really couldn't "call" anything
        if (PsiUtil.isInsideJavadocComment(reference.getElement())) {
          return true;
        }
        // Method search sometimes finds references to the base method
        // They shouldn't be shown because we are interested in exact results only
        // (Do not remove constructor usages because reference to the default constructor really is a class reference)
        if (!method.isConstructor() && !reference.isReferenceTo(method)) {
          return true;
        }
        for (CallReferenceProcessor processor : CallReferenceProcessor.EP_NAME.getExtensions()) {
          if (!processor.process(reference, data)) break;
        }
        return true;
      });

      return ArrayUtil.toObjectArray(methodToDescriptorMap.values());
    }
    
    assert enclosingElement instanceof PsiField : "Enclosing element should be a field, but was " + enclosingElement.getClass() + ", text: " + enclosingElement.getText();

    return ReferencesSearch
      .search(enclosingElement, enclosingElement.getUseScope()).findAll().stream()
      .map(PsiReference::getElement)
      .distinct()
      .map(e -> new CallHierarchyNodeDescriptor(myProject, nodeDescriptor, e, false, false)).toArray();
  }

  private static boolean isLocalOrAnonymousClass(PsiMember enclosingElement) {
    return enclosingElement instanceof PsiClass && ((PsiClass)enclosingElement).getQualifiedName() == null;
  }

  @Override
  public String toString() {
    return "Caller Hierarchy for " + formatBaseElementText();
  }
}
