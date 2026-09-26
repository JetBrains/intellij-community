// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.hierarchy.type;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.util.SlowOperations;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public final class TypeHierarchyTreeStructure extends SubtypesHierarchyTreeStructure {
  public TypeHierarchyTreeStructure(@NotNull Project project, @NotNull PsiClass aClass, String currentScopeType) {
    super(project, buildHierarchyElement(project, aClass), currentScopeType);
    setBaseElement(myBaseDescriptor); //to set myRoot
  }

  private static @NotNull HierarchyNodeDescriptor buildHierarchyElement(@NotNull Project project, @NotNull PsiClass aClass) {
    try (AccessToken ignore = SlowOperations.knownIssue("IDEA-345476, EA-700938")) {
      return buildHierarchyElementInner(project, aClass);
    }
  }

  private static @NotNull HierarchyNodeDescriptor buildHierarchyElementInner(@NotNull Project project, @NotNull PsiClass aClass) {
    HierarchyNodeDescriptor descriptor = null;
    PsiClass[] superClasses = createSuperClasses(aClass);
    for(int i = superClasses.length - 1; i >= 0; i--){
      PsiClass superClass = superClasses[i];
      HierarchyNodeDescriptor newDescriptor = new TypeHierarchyNodeDescriptor(project, descriptor, superClass, false);
      if (descriptor != null){
        descriptor.setCachedChildren(new HierarchyNodeDescriptor[] {newDescriptor});
      }
      descriptor = newDescriptor;
    }
    HierarchyNodeDescriptor newDescriptor = new TypeHierarchyNodeDescriptor(project, descriptor, aClass, true);
    if (descriptor != null) {
      descriptor.setCachedChildren(new HierarchyNodeDescriptor[] {newDescriptor});
    }
    return newDescriptor;
  }

  private static @NotNull PsiClass[] createSuperClasses(@NotNull PsiClass aClass) {
    if (!aClass.isValid()) return PsiClass.EMPTY_ARRAY;
    if (aClass.isInterface()) return PsiClass.EMPTY_ARRAY;

    ArrayList<PsiClass> superClasses = new ArrayList<>();
    while (!CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName())) {
      PsiClass aClass1 = aClass;
      PsiClass[] superTypes = aClass1.getSupers();
      PsiClass superType = null;
      for (PsiClass type : superTypes) {
        if (!type.isInterface()) {
          superType = type;
          break;
        }
      }
      if (superType == null) break;
      if (superClasses.contains(superType)) break;
      superClasses.add(superType);
      aClass = superType;
    }

    return superClasses.toArray(PsiClass.EMPTY_ARRAY);
  }

  @Override
  public String toString() {
    return "Type Hierarchy for " + formatBaseElementText();
  }
}
