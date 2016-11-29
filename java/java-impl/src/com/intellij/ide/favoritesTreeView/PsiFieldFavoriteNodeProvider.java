/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.ide.favoritesTreeView.smartPointerPsiNodes.FieldSmartPointerNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

public class PsiFieldFavoriteNodeProvider extends FavoriteNodeProvider {
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
        if (element instanceof PsiField) {
          result.add(new FieldSmartPointerNode(project, element, viewSettings));
        }
      }
      return result.isEmpty() ? null : result;
    }
    return null;
  }

  @Override
  public AbstractTreeNode createNode(final Project project, final Object element, final ViewSettings viewSettings) {
    if (element instanceof PsiField) {
      return new FieldSmartPointerNode(project, element, viewSettings);
    }
    return super.createNode(project, element, viewSettings);
  }

  @Override
  public boolean elementContainsFile(final Object element, final VirtualFile vFile) {
    return false;
  }

  @Override
  public int getElementWeight(final Object value, final boolean isSortByType) {
     if (value instanceof PsiField){
      return 4;
    }
    return -1;
  }

  @Override
  public String getElementLocation(final Object element) {
    if (element instanceof PsiField) {
      final PsiClass psiClass = ((PsiField)element).getContainingClass();
      if (psiClass != null) {
        return ClassPresentationUtil.getNameForClass(psiClass, true);
      }
    }
    return null;
  }

  @Override
  public boolean isInvalidElement(final Object element) {
    return element instanceof PsiField && !((PsiField)element).isValid();
  }

  @Override
  @NotNull
  public String getFavoriteTypeId() {
    return "field";
  }

  @Override
  public String getElementUrl(final Object element) {
    if (element instanceof PsiField) {
      final PsiField aField = (PsiField)element;
      return aField.getContainingClass().getQualifiedName() + ";" + aField.getName();
    }
    return null;
  }

  @Override
  public String getElementModuleName(final Object element) {
     if (element instanceof PsiField) {
      final Module module = ModuleUtilCore.findModuleForPsiElement((PsiField)element);
      return module != null ? module.getName() : null;
    }
    return null;
  }

  @Override
  public Object[] createPathFromUrl(final Project project, final String url, final String moduleName) {
    if (DumbService.isDumb(project)) return null;
    final Module module = moduleName != null ? ModuleManager.getInstance(project).findModuleByName(moduleName) : null;
    final GlobalSearchScope scope = module != null ? GlobalSearchScope.moduleScope(module) : GlobalSearchScope.allScope(project);
    final String[] paths = url.split(";");
    if (paths == null || paths.length != 2) return null;
    final PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(paths[0], scope);
    if (aClass == null) return null;
    final PsiField aField = aClass.findFieldByName(paths[1], false);
    return new Object[]{aField};
  }


}