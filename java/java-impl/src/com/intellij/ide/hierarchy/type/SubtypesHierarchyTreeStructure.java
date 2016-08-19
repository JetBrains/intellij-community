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
package com.intellij.ide.hierarchy.type;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class SubtypesHierarchyTreeStructure extends HierarchyTreeStructure {
  private final String myCurrentScopeType;

  protected SubtypesHierarchyTreeStructure(final Project project, final HierarchyNodeDescriptor descriptor, String currentScopeType) {
    super(project, descriptor);
    myCurrentScopeType = currentScopeType;
  }

  public SubtypesHierarchyTreeStructure(Project project, PsiClass psiClass, String currentScopeType) {
    super(project, new TypeHierarchyNodeDescriptor(project, null, psiClass, true));
    myCurrentScopeType = currentScopeType;
  }

  @NotNull
  protected final Object[] buildChildren(@NotNull final HierarchyNodeDescriptor descriptor) {
    final Object element = ((TypeHierarchyNodeDescriptor)descriptor).getPsiClass();
    if (!(element instanceof PsiClass)) return ArrayUtil.EMPTY_OBJECT_ARRAY;
    final PsiClass psiClass = (PsiClass)element;
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(psiClass.getQualifiedName())) {
      return new Object[]{IdeBundle.message("node.hierarchy.java.lang.object")};
    }
    if (psiClass instanceof PsiAnonymousClass) return ArrayUtil.EMPTY_OBJECT_ARRAY;
    if (psiClass.hasModifierProperty(PsiModifier.FINAL)) return ArrayUtil.EMPTY_OBJECT_ARRAY;
    final SearchScope searchScope = psiClass.getUseScope().intersectWith(getSearchScope(myCurrentScopeType, psiClass));
    final List<PsiClass> classes = new ArrayList<>(searchInheritors(psiClass, searchScope));
    final List<HierarchyNodeDescriptor> descriptors = new ArrayList<>(classes.size());
    for (PsiClass aClass : classes) {
      descriptors.add(new TypeHierarchyNodeDescriptor(myProject, descriptor, aClass, false));
    }
    FunctionalExpressionSearch.search(psiClass, searchScope).forEach(expression -> {
      descriptors.add(new TypeHierarchyNodeDescriptor(myProject, descriptor, expression, false));
      return true;
    });
    return descriptors.toArray(new HierarchyNodeDescriptor[descriptors.size()]);
  }

  @NotNull
  private static Collection<PsiClass> searchInheritors(@NotNull PsiClass psiClass, @NotNull SearchScope searchScope) {
    if (psiClass.isAnnotationType()) {
      final Set<PsiClass> result = ContainerUtil.newHashSet();

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
