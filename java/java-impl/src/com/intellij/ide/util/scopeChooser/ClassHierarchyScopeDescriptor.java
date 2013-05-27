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

/*
 * User: anna
 * Date: 16-Jan-2008
 */
package com.intellij.ide.util.scopeChooser;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

public class ClassHierarchyScopeDescriptor extends ScopeDescriptor {
  private SearchScope myCachedScope;
  private final Project myProject;

  public ClassHierarchyScopeDescriptor(final Project project) {
    super(null);
    myProject = project;
  }

  public String getDisplay() {
    return IdeBundle.message("scope.class.hierarchy");
  }

  @Nullable
  public SearchScope getScope() {
    if (myCachedScope == null) {
      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createAllProjectScopeChooser(IdeBundle.message("prompt.choose.base.class.of.the.hierarchy"));

      chooser.showDialog();

      PsiClass aClass = chooser.getSelected();
      if (aClass == null) return null;

      List<PsiElement> classesToSearch = new LinkedList<PsiElement>();
      classesToSearch.add(aClass);

      classesToSearch.addAll(ClassInheritorsSearch.search(aClass, true).findAll());

      myCachedScope = new LocalSearchScope(PsiUtilCore.toPsiElementArray(classesToSearch),
                                           IdeBundle.message("scope.hierarchy", ClassPresentationUtil.getNameForClass(aClass, true)));
    }

    return myCachedScope;
  }
}