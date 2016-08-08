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
 * Date: 21-Jan-2008
 */
package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.favoritesTreeView.smartPointerPsiNodes.ClassSmartPointerNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ClassTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

public class PsiClassFavoriteNodeProvider extends FavoriteNodeProvider {
  @Override
  public Collection<AbstractTreeNode> getFavoriteNodes(final DataContext context, final ViewSettings viewSettings) {
    final Project project = CommonDataKeys.PROJECT.getData(context);
    if (project == null) return null;
    PsiElement[] elements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(context);
    if (elements == null) {
      final PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(context);
      if (element != null) {
        elements = new PsiElement[]{element};
      }
    }
    if (elements != null) {
      final Collection<AbstractTreeNode> result = new ArrayList<>();
      for (PsiElement element : elements) {
        if (element instanceof PsiClass && checkClassUnderSources(element, project)) {
          result.add(new ClassSmartPointerNode(project, element, viewSettings));
        }
      }
      return result.isEmpty() ? null : result;
    }
    return null;
  }

  private boolean checkClassUnderSources(final PsiElement element, final Project project) {
    final PsiFile file = element.getContainingFile();
    if (file != null && file.getVirtualFile() != null) {
      final FileIndexFacade indexFacade = FileIndexFacade.getInstance(project);
      final VirtualFile vf = file.getVirtualFile();
      return indexFacade.isInSource(vf) || indexFacade.isInSourceContent(vf);
    }
    return false;
  }

  @Override
  public AbstractTreeNode createNode(final Project project, final Object element, final ViewSettings viewSettings) {
    if (element instanceof PsiClass && checkClassUnderSources((PsiElement)element, project)) {
      return new ClassSmartPointerNode(project, element, viewSettings);
    }
    return super.createNode(project, element, viewSettings);
  }

  @Override
  public boolean elementContainsFile(final Object element, final VirtualFile vFile) {
    if (element instanceof PsiClass) {
      final PsiFile file = ((PsiClass)element).getContainingFile();
      if (file != null && Comparing.equal(file.getVirtualFile(), vFile)) return true;
    }
    return false;
  }

  @Override
  public int getElementWeight(final Object value, final boolean isSortByType) {
     if (value instanceof PsiClass){
      return isSortByType ? ClassTreeNode.getClassPosition((PsiClass)value) : 3;
    }

    return -1;
  }

  @Override
  public String getElementLocation(final Object element) {
    if (element instanceof PsiClass) {
      return ClassPresentationUtil.getNameForClass((PsiClass)element, true);
    }
    return null;
  }

  @Override
  public boolean isInvalidElement(final Object element) {
    return element instanceof PsiClass && !((PsiClass)element).isValid();
  }

  @Override
  @NotNull
  public String getFavoriteTypeId() {
    return "class";
  }

  @Override
  public String getElementUrl(final Object element) {
    if (element instanceof PsiClass) {
      PsiClass aClass = (PsiClass)element;
      return aClass.getQualifiedName();
    }
    return null;
  }

  @Override
  public String getElementModuleName(final Object element) {
    if (element instanceof PsiClass) {
      PsiClass aClass = (PsiClass)element;
      Module module = ModuleUtilCore.findModuleForPsiElement(aClass);
      return module != null ? module.getName() : null;
    }
    return null;
  }

  @Override
  public Object[] createPathFromUrl(final Project project, final String url, final String moduleName) {
    if (DumbService.isDumb(project)) {
      return null;
    }

    GlobalSearchScope scope = null;
    if (moduleName != null) {
      final Module module = ModuleManager.getInstance(project).findModuleByName(moduleName);
      if (module != null) {
        scope = GlobalSearchScope.moduleScope(module);
      }
    }
    if (scope == null) {
      scope = GlobalSearchScope.allScope(project);
    }
    final PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(url, scope);
    if (aClass == null) return null;
    return new Object[]{aClass};
  }
}