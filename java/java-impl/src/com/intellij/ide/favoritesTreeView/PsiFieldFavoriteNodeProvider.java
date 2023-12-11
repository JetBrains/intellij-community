// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

public final class PsiFieldFavoriteNodeProvider extends FavoriteNodeProvider implements AbstractUrlFavoriteConverter {
  @Override
  public Collection<AbstractTreeNode<?>> getFavoriteNodes(final DataContext context, @NotNull final ViewSettings viewSettings) {
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
      final Collection<AbstractTreeNode<?>> result = new ArrayList<>();
      for (PsiElement element : elements) {
        if (element instanceof PsiField) {
          result.add(new FieldSmartPointerNode(project, (PsiField)element, viewSettings));
        }
      }
      return result.isEmpty() ? null : result;
    }
    return null;
  }

  @Override
  public AbstractTreeNode createNode(final Project project, final Object element, @NotNull final ViewSettings viewSettings) {
    if (element instanceof PsiField) {
      return new FieldSmartPointerNode(project, (PsiField)element, viewSettings);
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
    if (element instanceof PsiField aField) {
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
    var context = createBookmarkContext(project, url, moduleName);
    return context == null ? null : new Object[]{context};
  }

  @Override
  public @Nullable Object createBookmarkContext(@NotNull Project project, @NotNull String url, @Nullable String moduleName) {
    final Module module = moduleName != null ? ModuleManager.getInstance(project).findModuleByName(moduleName) : null;
    final GlobalSearchScope scope = module != null ? GlobalSearchScope.moduleScope(module) : GlobalSearchScope.allScope(project);
    final String[] paths = url.split(";");
    if (paths.length != 2) return null;
    final PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(paths[0], scope);
    if (aClass == null) return null;
    return aClass.findFieldByName(paths[1], false);
  }


}