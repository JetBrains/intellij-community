// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.hierarchy.type;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class SubtypesHierarchyTreeStructure extends HierarchyTreeStructure {
  private final String myCurrentScopeType;

  protected SubtypesHierarchyTreeStructure(@NotNull Project project, @NotNull HierarchyNodeDescriptor descriptor, String currentScopeType) {
    super(project, descriptor);
    myCurrentScopeType = currentScopeType;
  }

  public SubtypesHierarchyTreeStructure(@NotNull Project project, @NotNull PsiClass psiClass, String currentScopeType) {
    super(project, new TypeHierarchyNodeDescriptor(project, null, psiClass, true));
    myCurrentScopeType = currentScopeType;
  }

  @Override
  protected final Object @NotNull [] buildChildren(@NotNull HierarchyNodeDescriptor descriptor) {
    Object element = ((TypeHierarchyNodeDescriptor)descriptor).getPsiClass();
    if (!(element instanceof PsiClass)) return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    PsiClass psiClass = (PsiClass)element;
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(psiClass.getQualifiedName())) {
      return new Object[]{JavaBundle.message("node.hierarchy.java.lang.object")};
    }
    if (psiClass instanceof PsiAnonymousClass) return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    if (psiClass.hasModifierProperty(PsiModifier.FINAL)) return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    SearchScope searchScope = psiClass.getUseScope().intersectWith(getSearchScope(myCurrentScopeType, psiClass));
    List<PsiClass> classes = new ArrayList<>(searchInheritors(psiClass, searchScope));
    List<HierarchyNodeDescriptor> descriptors = new ArrayList<>(classes.size());
    for (PsiClass aClass : classes) {
      descriptors.add(new TypeHierarchyNodeDescriptor(myProject, descriptor, aClass, false));
    }
    FunctionalExpressionSearch.search(psiClass, searchScope).forEach(expression -> {
      descriptors.add(new TypeHierarchyNodeDescriptor(myProject, descriptor, expression, false));
      return true;
    });
    return descriptors.toArray(new HierarchyNodeDescriptor[0]);
  }

  @NotNull
  private static Collection<PsiClass> searchInheritors(@NotNull PsiClass psiClass, @NotNull SearchScope searchScope) {
    if (psiClass.isAnnotationType()) {
      Set<PsiClass> result = new HashSet<>();

      AnnotatedElementsSearch.searchPsiClasses(psiClass, searchScope).forEach(processorResult -> {
        if (processorResult.isAnnotationType()) {
          result.add(processorResult);
        }
        return true;
      });

      return result;
    }
    return ClassInheritorsSearch.search(psiClass, searchScope, false).findAll();
  }
}
