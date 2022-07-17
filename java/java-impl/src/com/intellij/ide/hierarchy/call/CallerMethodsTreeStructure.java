// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.hierarchy.call;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
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

    PsiClass enclosingClass = enclosingElement.getContainingClass();
    PsiClass expectedQualifierClass; // we'll compare reference qualifier class against this to filter out irrelevant usages
    if (enclosingElement instanceof PsiMethod && isLocalOrAnonymousClass(enclosingClass)) {
      PsiElement parent = enclosingClass.getParent();
      PsiElement grandParent = parent instanceof PsiNewExpression ? parent.getParent() : null;
      if (grandParent instanceof PsiExpressionList) {
        // for created anonymous class that immediately passed as argument use instantiation point as next call point (IDEA-73312)
        enclosingElement = CallHierarchyNodeDescriptor.getEnclosingElement(grandParent);
        enclosingClass = enclosingElement == null ? null : enclosingElement.getContainingClass();
      }
      if (enclosingClass instanceof PsiAnonymousClass) {
        expectedQualifierClass = enclosingClass.getSuperClass();
      }
      else {
        expectedQualifierClass = enclosingClass;
      }
    }
    else {
      expectedQualifierClass = enclosingClass;
    }

    PsiMember baseMember = (PsiMember)((CallHierarchyNodeDescriptor)nodeDescriptor).getTargetElement();
    if (baseMember == null) return ArrayUtilRt.EMPTY_OBJECT_ARRAY;

    SearchScope searchScope = getSearchScope(myScopeType, baseMember.getContainingClass());

    PsiMember member = enclosingElement;
    PsiClass originalClass = member.getContainingClass();
    
    if (originalClass == null) return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    
    PsiClassType originalType = JavaPsiFacade.getElementFactory(myProject).createType(originalClass);
    Set<PsiMethod> methodsToFind = new HashSet<>();

    if (enclosingElement instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)enclosingElement;
      methodsToFind.add(method);
      ContainerUtil.addAll(methodsToFind, method.findDeepestSuperMethods());

      Map<PsiMember, NodeDescriptor<?>> methodToDescriptorMap = new HashMap<>();
      for (PsiMethod methodToFind : methodsToFind) {
        JavaCallHierarchyData data = new JavaCallHierarchyData(originalClass, methodToFind, originalType, method, methodsToFind, descriptor, methodToDescriptorMap, myProject);

        MethodReferencesSearch.search(methodToFind, searchScope, true).forEach(reference -> {
          // references in javadoc really couldn't "call" anything
          if (PsiUtil.isInsideJavadocComment(reference.getElement())) {
            return true;
          }
          PsiClass receiverClass = null;
          if (reference instanceof PsiQualifiedReference) {
            PsiElement qualifier = ((PsiQualifiedReference)reference).getQualifier();
            if (qualifier instanceof PsiExpression) {
              PsiType type = ((PsiExpression)qualifier).getType();
              receiverClass = PsiUtil.resolveClassInClassTypeOnly(type);
            }
          }
          if (receiverClass == null) {
            PsiElement resolved = reference.resolve();
            if (resolved instanceof PsiMethod) {
              receiverClass = ((PsiMethod)resolved).getContainingClass();
            }
          }

          if (receiverClass != null
              && expectedQualifierClass != null
              && !areClassesRelated(expectedQualifierClass, receiverClass)
          ) {
            // ignore impossible candidates. E.g. when A < B,A < C and we invoked call hierarchy for method in C we should filter out methods in B because B and C are assignment-incompatible
            return true;
          }
          for (CallReferenceProcessor processor : CallReferenceProcessor.EP_NAME.getExtensions()) {
            if (!processor.process(reference, data)) break;
          }
          return true;
        });
      }

      return ArrayUtil.toObjectArray(methodToDescriptorMap.values());
    }
    
    assert enclosingElement instanceof PsiField : "Enclosing element should be a field, but was " + enclosingElement.getClass() + ", text: " + enclosingElement.getText();

    return ReferencesSearch
      .search(enclosingElement, enclosingElement.getUseScope()).findAll().stream()
      .map(PsiReference::getElement)
      .distinct()
      .map(e -> new CallHierarchyNodeDescriptor(myProject, nodeDescriptor, e, false, false)).toArray();
  }

  private static boolean areClassesRelated(@NotNull PsiClass expectedQualifierClass, @NotNull PsiClass receiverClass) {
    if (areClassesDirectlyRelated(expectedQualifierClass, receiverClass)) {
      return true;
    }
    if (receiverClass instanceof PsiTypeParameter) {
      // in case of "T extends S", it should be related to SImpl, even though T is not superclass of SImpl
      for (PsiClass receiverExtends : PsiClassImplUtil.getAllSuperClassesRecursively(receiverClass)) {
        if (areClassesDirectlyRelated(expectedQualifierClass, receiverExtends)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean areClassesDirectlyRelated(@NotNull PsiClass expectedQualifierClass, @NotNull PsiClass receiverClass) {
    return InheritanceUtil.isInheritorOrSelf(expectedQualifierClass, receiverClass, true)
           || InheritanceUtil.isInheritorOrSelf(receiverClass, expectedQualifierClass, true);
  }

  private static boolean isLocalOrAnonymousClass(PsiMember enclosingElement) {
    return enclosingElement instanceof PsiClass && ((PsiClass)enclosingElement).getQualifiedName() == null;
  }

  @Override
  public String toString() {
    return "Caller Hierarchy for " + formatBaseElementText();
  }
}
