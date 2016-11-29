/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFunctionalExpression;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

public class ClassHierarchyScopeDescriptor extends ScopeDescriptor {
  private SearchScope myCachedScope;
  private final Project myProject;
  private final PsiClass myRootClass;

  public ClassHierarchyScopeDescriptor(final Project project) {
    super(null);
    myProject = project;
    myRootClass = PsiTreeUtil.getParentOfType(CommonDataKeys.PSI_ELEMENT.getData(DataManager.getInstance().getDataContext()), PsiClass.class, false);
  }

  public String getDisplay() {
    return IdeBundle.message("scope.class.hierarchy");
  }

  @Nullable
  public SearchScope getScope() {
    if (myCachedScope == null) {
      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createAllProjectScopeChooser(IdeBundle.message("prompt.choose.base.class.of.the.hierarchy"));
      if (myRootClass != null) {
        chooser.select(myRootClass);
      }

      chooser.showDialog();

      PsiClass aClass = chooser.getSelected();
      if (aClass == null) {
        myCachedScope = GlobalSearchScope.EMPTY_SCOPE;
      } else {
        final List<PsiElement> classesToSearch = new LinkedList<>();
        classesToSearch.add(aClass);

        classesToSearch.addAll(ClassInheritorsSearch.search(aClass).findAll());

        FunctionalExpressionSearch.search(aClass).forEach(expression -> {
          classesToSearch.add(expression);
          return true;
        });

        myCachedScope = new LocalSearchScope(PsiUtilCore.toPsiElementArray(classesToSearch),
                                             IdeBundle.message("scope.hierarchy", ClassPresentationUtil.getNameForClass(aClass, true)));
      }
    }

    return myCachedScope;
  }
}