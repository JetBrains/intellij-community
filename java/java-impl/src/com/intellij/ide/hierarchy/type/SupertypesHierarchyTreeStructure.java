// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.hierarchy.type;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SupertypesHierarchyTreeStructure extends HierarchyTreeStructure {

  public SupertypesHierarchyTreeStructure(Project project, PsiClass aClass) {
    super(project, new TypeHierarchyNodeDescriptor(project, null, aClass, true));
  }

  @Override
  protected Object @NotNull [] buildChildren(@NotNull HierarchyNodeDescriptor descriptor) {
    Object element = ((TypeHierarchyNodeDescriptor)descriptor).getPsiClass();
    if (element instanceof PsiClass) {
      PsiClass psiClass = (PsiClass)element;
      PsiClass[] supers = getSupers(psiClass);
      List<HierarchyNodeDescriptor> descriptors = new ArrayList<>();
      PsiClass objectClass = JavaPsiFacade.getInstance(myProject).findClass(CommonClassNames.JAVA_LANG_OBJECT, psiClass.getResolveScope());
      for (PsiClass aSuper : supers) {
        if (!psiClass.isInterface() || !aSuper.equals(objectClass)) {
          descriptors.add(new TypeHierarchyNodeDescriptor(myProject, descriptor, aSuper, false));
        }
      }
      return descriptors.toArray(HierarchyNodeDescriptor.EMPTY_ARRAY);
    } else if (element instanceof PsiFunctionalExpression) {
      PsiClass functionalInterfaceClass = LambdaUtil.resolveFunctionalInterfaceClass((PsiFunctionalExpression)element);
      if (functionalInterfaceClass != null) {
        return new HierarchyNodeDescriptor[] {new TypeHierarchyNodeDescriptor(myProject, descriptor, functionalInterfaceClass, false)};
      }
    }
    return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
  }

  private static PsiClass @NotNull [] getSupers(@NotNull PsiClass psiClass) {
    if (psiClass.isAnnotationType()) {
      return getMetaAnnotations(psiClass);
    }
    return psiClass.getSupers();
  }

  private static PsiClass @NotNull [] getMetaAnnotations(@NotNull PsiClass psiClass) {
    Set<PsiClass> supers = new HashSet<>();
    PsiModifierList modifierList = psiClass.getModifierList();
    if (modifierList != null) {
      for (PsiAnnotation annotation : modifierList.getAnnotations()) {
        if (isJavaLangAnnotation(annotation)) continue;
        PsiClass aClass = annotation.resolveAnnotationType();
        if (aClass != null) {
          PsiAnnotation.TargetType target = AnnotationTargetUtil
            .findAnnotationTarget(aClass, PsiAnnotation.TargetType.TYPE, PsiAnnotation.TargetType.ANNOTATION_TYPE);
          if (target !=  null && target != PsiAnnotation.TargetType.UNKNOWN) {
              supers.add(aClass);
          }
        }
      }
    }
    return supers.toArray(PsiClass.EMPTY_ARRAY);
  }

  private static boolean isJavaLangAnnotation(@NotNull  PsiAnnotation annotation) {
    String qualifiedName = annotation.getQualifiedName();
    return qualifiedName != null && qualifiedName.startsWith("java.lang.annotation");
  }
}
