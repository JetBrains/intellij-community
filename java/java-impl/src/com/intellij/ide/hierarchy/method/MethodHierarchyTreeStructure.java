// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.hierarchy.method;

import com.intellij.ide.hierarchy.HierarchyBrowserManager;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class MethodHierarchyTreeStructure extends HierarchyTreeStructure {
  private final SmartPsiElementPointer<PsiMethod> myMethod;
  private final String myScopeType;

  /**
   * Should be called in read action
   */
  public MethodHierarchyTreeStructure(@NotNull Project project, @NotNull PsiMethod method, String type) {
    super(project, null);
    myScopeType = type;
    myBaseDescriptor = buildHierarchyElement(project, method);
    ((MethodHierarchyNodeDescriptor)myBaseDescriptor).setTreeStructure(this);
    myMethod = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(method);
    setBaseElement(myBaseDescriptor); //to set myRoot
  }

  private HierarchyNodeDescriptor buildHierarchyElement(final Project project, final PsiMethod method) {
    List<PsiClass> superClasses = buildSuperChain(method);
    PsiClass containingClass = method.getContainingClass();
    assert containingClass != null;

    HierarchyNodeDescriptor descriptor = null;
    for (final PsiClass superClass : superClasses) {
      final HierarchyNodeDescriptor newDescriptor = new MethodHierarchyNodeDescriptor(project, descriptor, superClass, false, this);
      if (descriptor != null) {
        descriptor.setCachedChildren(new HierarchyNodeDescriptor[]{newDescriptor});
      }
      descriptor = newDescriptor;
    }
    final HierarchyNodeDescriptor newDescriptor = new MethodHierarchyNodeDescriptor(project, descriptor, containingClass, true, this);
    if (descriptor != null) {
      descriptor.setCachedChildren(new HierarchyNodeDescriptor[] {newDescriptor});
    }
    return newDescriptor;
  }

  private static List<PsiClass> buildSuperChain(PsiMethod method) {
    List<PsiClass> superClasses = new ArrayList<>();
    while (true) {
      PsiMethod superMethod = ArrayUtil.getFirstElement(method.findSuperMethods());
      if (superMethod == null || superClasses.contains(superMethod.getContainingClass())) break;
      superClasses.addAll(0, findInheritanceChain(method.getContainingClass(), superMethod.getContainingClass()));
      method = superMethod;
    }
    return superClasses;
  }

  private static List<PsiClass> findInheritanceChain(PsiClass subClass, PsiClass superClass) {
    Map<PsiClass, PsiClass> inheritanceMap = new HashMap<>();
    Queue<PsiClass> workQueue = new ArrayDeque<>();
    workQueue.add(subClass);
    while (!workQueue.isEmpty()) {
      PsiClass cls = workQueue.poll();
      for (PsiClass sup : StreamEx.of(cls.getInterfaces()).prepend(cls.getSuperClass()).nonNull()) {
        if (!inheritanceMap.containsKey(sup)) {
          inheritanceMap.put(sup, cls);
          workQueue.offer(sup);
          if (sup == superClass) {
            return StreamEx.iterate(superClass, c -> c != subClass, inheritanceMap::get).toList();
          }
        }
      }
    }
    return Collections.emptyList();
  }

  @Nullable
  public final PsiMethod getBaseMethod() {
    return myMethod.getElement();
  }

  @NotNull
  @Override
  protected final Object[] buildChildren(@NotNull final HierarchyNodeDescriptor descriptor) {
    final PsiElement psiElement = ((MethodHierarchyNodeDescriptor)descriptor).getPsiClass();
    if (!(psiElement instanceof PsiClass)) return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    final PsiClass psiClass = (PsiClass)psiElement;
    final Collection<PsiClass> subclasses = getSubclasses(psiClass);

    final List<HierarchyNodeDescriptor> descriptors = new ArrayList<>(subclasses.size());
    HierarchyBrowserManager.State state = HierarchyBrowserManager.getInstance(myProject).getState();
    boolean hideNotImplemented = state != null && state.HIDE_CLASSES_WHERE_METHOD_NOT_IMPLEMENTED;
    for (final PsiClass aClass : subclasses) {
      if (hideNotImplemented) {
        if (shouldHideClass(aClass)) {
          continue;
        }
      }

      final MethodHierarchyNodeDescriptor d = new MethodHierarchyNodeDescriptor(myProject, descriptor, aClass, false, this);
      descriptors.add(d);
    }

    final PsiMethod existingMethod = ((MethodHierarchyNodeDescriptor)descriptor).getMethod(psiClass, false);
    if (existingMethod != null) {
      FunctionalExpressionSearch.search(existingMethod).forEach(expression -> {
        descriptors.add(new MethodHierarchyNodeDescriptor(myProject, descriptor, expression, false, this));
        return true;
      });
    }

    return descriptors.toArray(new HierarchyNodeDescriptor[0]);
  }

  private Collection<PsiClass> getSubclasses(final PsiClass psiClass) {
    if (psiClass instanceof PsiAnonymousClass || psiClass.hasModifierProperty(PsiModifier.FINAL)) {
      return Collections.emptyList();
    }

    final SearchScope searchScope = getSearchScope(myScopeType, psiClass);
    return ClassInheritorsSearch.search(psiClass, searchScope, false).findAll();
  }

  private boolean shouldHideClass(final PsiClass psiClass) {
    if (getMethod(psiClass, false) != null || isSuperClassForBaseClass(psiClass)) {
      return false;
    }

    if (hasBaseClassMethod(psiClass) || isAbstract(psiClass)) {
      for (final PsiClass subclass : getSubclasses(psiClass)) {
        if (!shouldHideClass(subclass)) {
          return false;
        }
      }
      return true;
    }

    return false;
  }

  private static boolean isAbstract(final PsiModifierListOwner owner) {
    return owner.hasModifierProperty(PsiModifier.ABSTRACT);
  }

  private boolean hasBaseClassMethod(final PsiClass psiClass) {
    final PsiMethod baseClassMethod = getMethod(psiClass, true);
    return baseClassMethod != null && !isAbstract(baseClassMethod);
  }

  private PsiMethod getMethod(final PsiClass aClass, final boolean checkBases) {
    return MethodHierarchyUtil.findBaseMethodInClass(getBaseMethod(), aClass, checkBases);
  }

  boolean isSuperClassForBaseClass(final PsiClass aClass) {
    final PsiMethod baseMethod = getBaseMethod();
    if (baseMethod == null) {
      return false;
    }
    final PsiClass baseClass = baseMethod.getContainingClass();
    if (baseClass == null) {
      return false;
    }
    // NB: parameters here are at CORRECT places!!!
    return baseClass.isInheritor(aClass, true);
  }
}
