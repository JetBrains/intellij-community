/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.util.gotoByName;

import com.intellij.navigation.GotoClassContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class DefaultClassNavigationContributor implements GotoClassContributor {
  @Override
  @NotNull
  public String[] getNames(Project project, boolean includeNonProjectItems) {
    return PsiShortNamesCache.getInstance(project).getAllClassNames();
  }

  @Override
  @NotNull
  public NavigationItem[] getItemsByName(String name, final String pattern, Project project, boolean includeNonProjectItems) {
    final GlobalSearchScope scope = includeNonProjectItems ? GlobalSearchScope.allScope(project) : GlobalSearchScope.projectScope(project);
    return filterUnshowable(PsiShortNamesCache.getInstance(project).getClassesByName(name, scope), pattern);
  }

  private static NavigationItem[] filterUnshowable(PsiClass[] items, final String pattern) {
    boolean isAnnotation = pattern.startsWith("@");
    ArrayList<NavigationItem> list = new ArrayList<NavigationItem>(items.length);
    for (PsiClass item : items) {
      if (item.getContainingFile().getVirtualFile() == null) continue;
      if (isAnnotation && !item.isAnnotationType()) continue;
      list.add(item);
    }
    return list.toArray(new NavigationItem[list.size()]);
  }

  @Override
  public String getQualifiedName(final NavigationItem item) {
    if (item instanceof PsiClass) {
      final PsiClass psiClass = (PsiClass)item;
      final String qName = psiClass.getQualifiedName();
      if (qName != null) return qName;

      final String containerText = SymbolPresentationUtil.getSymbolContainerText(psiClass);
      return containerText + "." + psiClass.getName();
    }
    return null;
  }

  @Override
  public String getQualifiedNameSeparator() {
    return ".";
  }
}