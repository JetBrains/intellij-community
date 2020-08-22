// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  public SupertypesHierarchyTreeStructure(final Project project, final PsiClass aClass) {
    super(project, new TypeHierarchyNodeDescriptor(project, null, aClass, true));
  }

  @Override
  protected final Object @NotNull [] buildChildren(@NotNull final HierarchyNodeDescriptor descriptor) {
    final Object element = ((TypeHierarchyNodeDescriptor)descriptor).getPsiClass();
    if (element instanceof PsiClass) {
      final PsiClass psiClass = (PsiClass)element;
      final PsiClass[] supers = getSupers(psiClass);
      final List<HierarchyNodeDescriptor> descriptors = new ArrayList<>();
      final PsiClass objectClass = JavaPsiFacade.getInstance(myProject).findClass(CommonClassNames.JAVA_LANG_OBJECT, psiClass.getResolveScope());
      for (PsiClass aSuper : supers) {
        if (!psiClass.isInterface() || !aSuper.equals(objectClass)) {
          descriptors.add(new TypeHierarchyNodeDescriptor(myProject, descriptor, aSuper, false));
        }
      }
      return descriptors.toArray(new HierarchyNodeDescriptor[0]);
    } else if (element instanceof PsiFunctionalExpression) {
      final PsiClass functionalInterfaceClass = LambdaUtil.resolveFunctionalInterfaceClass((PsiFunctionalExpression)element);
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
    final PsiModifierList modifierList = psiClass.getModifierList();
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
    final String qualifiedName = annotation.getQualifiedName();
    return qualifiedName != null && qualifiedName.startsWith("java.lang.annotation");
  }
}
