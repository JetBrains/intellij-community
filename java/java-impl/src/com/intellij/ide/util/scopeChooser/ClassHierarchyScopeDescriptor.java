// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.util.scopeChooser;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

public class ClassHierarchyScopeDescriptor extends ScopeDescriptor {
  private SearchScope myCachedScope;
  private final Project myProject;
  private final PsiClass myRootClass;

  public ClassHierarchyScopeDescriptor(final Project project,
                                       @NotNull DataContext dataContext) {
    super(null);
    myProject = project;

    PsiElement element;
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor != null) {
      PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
      element = file != null ? file.findElementAt(editor.getCaretModel().getOffset()) : null;
    }
    else {
      element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    }
    myRootClass = PsiTreeUtil.getParentOfType(element, PsiClass.class, false);
  }

  @Override
  public String getDisplayName() {
    return IdeBundle.message("scope.class.hierarchy");
  }

  @Override
  @Nullable
  public SearchScope getScope() {
    if (myCachedScope == null) {
      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject)
        .createAllProjectScopeChooser(JavaBundle.message("prompt.choose.base.class.of.the.hierarchy"));
      if (myRootClass != null) {
        chooser.select(myRootClass);
      }

      chooser.showDialog();

      PsiClass aClass = chooser.getSelected();
      if (aClass == null) {
        myCachedScope = LocalSearchScope.EMPTY;
      }
      else {
        final List<PsiElement> classesToSearch = new LinkedList<>();
        classesToSearch.add(aClass);

        classesToSearch.addAll(ClassInheritorsSearch.search(aClass).findAll());

        FunctionalExpressionSearch.search(aClass).forEach(expression -> {
          classesToSearch.add(expression);
          return true;
        });

        myCachedScope = new LocalSearchScope(PsiUtilCore.toPsiElementArray(classesToSearch),
                                             JavaBundle.message("scope.hierarchy", ClassPresentationUtil.getNameForClass(aClass, true)));
      }
    }

    return myCachedScope;
  }
}