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
package com.intellij.ide.hierarchy.method;

import com.intellij.ide.hierarchy.HierarchyBrowserManager;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class MethodHierarchyTreeStructure extends HierarchyTreeStructure {
  private final SmartPsiElementPointer myMethod;

  /**
   * Should be called in read action
   */
  public MethodHierarchyTreeStructure(final Project project, final PsiMethod method) {
    super(project, null);
    myBaseDescriptor = buildHierarchyElement(project, method);
    ((MethodHierarchyNodeDescriptor)myBaseDescriptor).setTreeStructure(this);
    myMethod = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(method);
    setBaseElement(myBaseDescriptor); //to set myRoot
  }

  private HierarchyNodeDescriptor buildHierarchyElement(final Project project, final PsiMethod method) {
    final PsiClass suitableBaseClass = findSuitableBaseClass(method);

    HierarchyNodeDescriptor descriptor = null;
    final ArrayList<PsiClass> superClasses = createSuperClasses(suitableBaseClass);

    if (!suitableBaseClass.equals(method.getContainingClass())) {
      superClasses.add(0, suitableBaseClass);
    }

    // remove from the top of the branch the classes that contain no 'method'
    for(int i = superClasses.size() - 1; i >= 0; i--){
      final PsiClass psiClass = superClasses.get(i);

      if (MethodHierarchyUtil.findBaseMethodInClass(method, psiClass, false) == null) {
        superClasses.remove(i);
      }
      else {
        break;
      }
    }

    for(int i = superClasses.size() - 1; i >= 0; i--){
      final PsiClass superClass = superClasses.get(i);
      final HierarchyNodeDescriptor newDescriptor = new MethodHierarchyNodeDescriptor(project, descriptor, superClass, false, this);
      if (descriptor != null){
        descriptor.setCachedChildren(new HierarchyNodeDescriptor[] {newDescriptor});
      }
      descriptor = newDescriptor;
    }
    final HierarchyNodeDescriptor newDescriptor = new MethodHierarchyNodeDescriptor(project, descriptor, method.getContainingClass(), true, this);
    if (descriptor != null) {
      descriptor.setCachedChildren(new HierarchyNodeDescriptor[] {newDescriptor});
    }
    return newDescriptor;
  }

  private static ArrayList<PsiClass> createSuperClasses(PsiClass aClass) {
    if (!aClass.isValid()) {
      return new ArrayList<>();
    }

    final ArrayList<PsiClass> superClasses = new ArrayList<>();
    while (!isJavaLangObject(aClass)) {
      final PsiClass aClass1 = aClass;
      final PsiClass[] superTypes = aClass1.getSupers();
      PsiClass superType = null;
      // find class first
      for (final PsiClass type : superTypes) {
        if (!type.isInterface() && !isJavaLangObject(type)) {
          superType = type;
          break;
        }
      }
      // if we haven't found a class, try to find an interface
      if (superType == null) {
        for (final PsiClass type : superTypes) {
          if (!isJavaLangObject(type)) {
            superType = type;
            break;
          }
        }
      }
      if (superType == null) break;
      if (superClasses.contains(superType)) break;
      superClasses.add(superType);
      aClass = superType;
    }

    return superClasses;
  }

  private static boolean isJavaLangObject(final PsiClass aClass) {
    return CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName());
  }

  private static PsiClass findSuitableBaseClass(final PsiMethod method) {
    final PsiClass containingClass = method.getContainingClass();

    if (containingClass instanceof PsiAnonymousClass) {
      return containingClass;
    }

    final PsiClass superClass = containingClass.getSuperClass();
    if (superClass == null) {
      return containingClass;
    }

    if (MethodHierarchyUtil.findBaseMethodInClass(method, superClass, true) == null) {
      for (final PsiClass anInterface : containingClass.getInterfaces()) {
        if (MethodHierarchyUtil.findBaseMethodInClass(method, anInterface, true) != null) {
          return anInterface;
        }
      }
    }

    return containingClass;
  }

  @Nullable
  public final PsiMethod getBaseMethod() {
    final PsiElement element = myMethod.getElement();
    return element instanceof PsiMethod ? (PsiMethod)element : null;
  }


  @NotNull
  @Override
  protected final Object[] buildChildren(@NotNull final HierarchyNodeDescriptor descriptor) {
    final PsiElement psiElement = ((MethodHierarchyNodeDescriptor)descriptor).getPsiClass();
    if (!(psiElement instanceof PsiClass)) return ArrayUtil.EMPTY_OBJECT_ARRAY;
    final PsiClass psiClass = (PsiClass)psiElement;
    final Collection<PsiClass> subclasses = getSubclasses(psiClass);

    final List<HierarchyNodeDescriptor> descriptors = new ArrayList<>(subclasses.size());
    for (final PsiClass aClass : subclasses) {
      if (HierarchyBrowserManager.getInstance(myProject).getState().HIDE_CLASSES_WHERE_METHOD_NOT_IMPLEMENTED) {
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

    return descriptors.toArray(new HierarchyNodeDescriptor[descriptors.size()]);
  }

  private static Collection<PsiClass> getSubclasses(final PsiClass psiClass) {
    if (psiClass instanceof PsiAnonymousClass || psiClass.hasModifierProperty(PsiModifier.FINAL)) {
      return Collections.emptyList();
    }

    return ClassInheritorsSearch.search(psiClass, false).findAll();
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

  private boolean isAbstract(final PsiModifierListOwner owner) {
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
