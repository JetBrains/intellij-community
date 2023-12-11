// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

public final class PsiMethodFavoriteNodeProvider extends FavoriteNodeProvider implements AbstractUrlFavoriteConverter {
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
        if (element instanceof PsiMethod) {
          result.add(new MethodSmartPointerNode(project, (PsiMethod)element, viewSettings));
        }
      }
      return result.isEmpty() ? null : result;
    }
    return null;
  }

  @Override
  public AbstractTreeNode createNode(final Project project, final Object element, @NotNull final ViewSettings viewSettings) {
    if (element instanceof PsiMethod) {
      return new MethodSmartPointerNode(project, (PsiMethod)element, viewSettings);
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
     if (element instanceof PsiMethod aMethod) {
       if (DumbService.isDumb(aMethod.getProject())) return null;
       return PsiFormatUtil.getExternalName(aMethod);
    }
    return null;
  }

  @Override
  public String getElementModuleName(final Object element) {
     if (element instanceof PsiMethod aMethod) {
       Module module = ModuleUtilCore.findModuleForPsiElement(aMethod);
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
    return RefMethodImpl.findPsiMethod(PsiManager.getInstance(project), url);
  }
}