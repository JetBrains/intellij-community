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

import com.intellij.codeInspection.reference.RefMethodImpl;
import com.intellij.ide.favoritesTreeView.smartPointerPsiNodes.MethodSmartPointerNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.util.PsiFormatUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

public class PsiMethodFavoriteNodeProvider extends FavoriteNodeProvider {
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
        if (element instanceof PsiMethod) {
          result.add(new MethodSmartPointerNode(project, element, viewSettings));
        }
      }
      return result.isEmpty() ? null : result;
    }
    return null;
  }

  @Override
  public AbstractTreeNode createNode(final Project project, final Object element, final ViewSettings viewSettings) {
    if (element instanceof PsiMethod) {
      return new MethodSmartPointerNode(project, element, viewSettings);
    }
    return super.createNode(project, element, viewSettings);
  }

  @Override
  public boolean elementContainsFile(final Object element, final VirtualFile vFile) {
    return false;
  }

  @Override
  public int getElementWeight(final Object value, final boolean isSortByType) {
    if (value instanceof PsiMethod){
      return 5;
    }
    return -1;
  }

  @Override
  public String getElementLocation(final Object element) {
    if (element instanceof PsiMethod) {
      final PsiClass parent = ((PsiMethod)element).getContainingClass();
      if (parent != null) {
        return ClassPresentationUtil.getNameForClass(parent, true);
      }
    }
    return null;
  }

  @Override
  public boolean isInvalidElement(final Object element) {
    return element instanceof PsiMethod && !((PsiMethod)element).isValid();
  }

  @Override
  @NotNull
  public String getFavoriteTypeId() {
    return "method";
  }

  @Override
  public String getElementUrl(final Object element) {
     if (element instanceof PsiMethod) {
       PsiMethod aMethod = (PsiMethod)element;
       if (DumbService.isDumb(aMethod.getProject())) return null;
       return PsiFormatUtil.getExternalName(aMethod);
    }
    return null;
  }

  @Override
  public String getElementModuleName(final Object element) {
     if (element instanceof PsiMethod) {
      PsiMethod aMethod = (PsiMethod)element;
      Module module = ModuleUtilCore.findModuleForPsiElement(aMethod);
      return module != null ? module.getName() : null;
    }
    return null;
  }

  @Override
  public Object[] createPathFromUrl(final Project project, final String url, final String moduleName) {
    if (DumbService.isDumb(project)) return null;
    final PsiMethod method = RefMethodImpl.findPsiMethod(PsiManager.getInstance(project), url);
    if (method == null) return null;
    return new Object[]{method};
  }
}