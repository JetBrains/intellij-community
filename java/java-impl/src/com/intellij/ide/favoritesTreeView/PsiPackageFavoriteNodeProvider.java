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

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.PackageViewPane;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.ide.projectView.impl.nodes.*;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class PsiPackageFavoriteNodeProvider extends FavoriteNodeProvider {
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
    final Collection<AbstractTreeNode> result = new ArrayList<>();
    if (elements != null) {
      for (PsiElement element : elements) {
        if (element instanceof PsiPackage) {
          final PsiPackage psiPackage = (PsiPackage)element;
          final PsiDirectory[] directories = psiPackage.getDirectories();
          if (directories.length > 0) {
            final VirtualFile firstDir = directories[0].getVirtualFile();
            final boolean isLibraryRoot = ProjectRootsUtil.isLibraryRoot(firstDir, project);
            final PackageElement packageElement = new PackageElement(LangDataKeys.MODULE.getData(context), psiPackage, isLibraryRoot);
            result.add(new PackageElementNode(project, packageElement, viewSettings));
          }
        }
      }
      return result.isEmpty() ? null : result;
    }
    final String currentViewId = ProjectView.getInstance(project).getCurrentViewId();
    final Module[] modules = LangDataKeys.MODULE_CONTEXT_ARRAY.getData(context);
    if (modules != null) {
      for (Module module : modules) {
        if (PackageViewPane.ID.equals(currentViewId)) {
          result.add(new PackageViewModuleNode(project, module, viewSettings));
        }
        else {
          result.add(new ProjectViewModuleNode(project, module, viewSettings));
        }
      }
    } else {
      final ModuleGroup[] data = ModuleGroup.ARRAY_DATA_KEY.getData(context);
      if (data != null) {
        for (ModuleGroup moduleGroup : data) {
          if (PackageViewPane.ID.equals(currentViewId)) {
            result.add(new PackageViewModuleGroupNode(project, moduleGroup, viewSettings));
          }
          else {
            result.add(new ProjectViewModuleGroupNode(project, moduleGroup, viewSettings));
          }
        }
      }
    }
    return null;
  }

  @Override
  public AbstractTreeNode createNode(final Project project, final Object element, final ViewSettings viewSettings) {
    if (element instanceof PackageElement) {
      return new PackageElementNode(project, element, viewSettings);
    }
    return super.createNode(project, element, viewSettings);
  }

  @Override
  public boolean elementContainsFile(final Object element, final VirtualFile vFile) {
    if (element instanceof PackageElement) {
      final Set<Boolean> find = new HashSet<>();
      final ContentIterator contentIterator = new ContentIterator() {
        @Override
        public boolean processFile(VirtualFile fileOrDir) {
          if (fileOrDir != null && fileOrDir.getPath().equals(vFile.getPath())) {
            find.add(Boolean.TRUE);
          }
          return true;
        }
      };
      final PackageElement packageElement = (PackageElement)element;
      final PsiPackage aPackage = packageElement.getPackage();
      final Project project = aPackage.getProject();
      final GlobalSearchScope scope = packageElement.getModule() != null
                                      ? GlobalSearchScope.moduleScope(packageElement.getModule())
                                      : GlobalSearchScope.projectScope(project);
      final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      final PsiDirectory[] directories = aPackage.getDirectories(scope);
      for (PsiDirectory directory : directories) {
        projectFileIndex.iterateContentUnderDirectory(directory.getVirtualFile(), contentIterator);
      }
      return !find.isEmpty();
    }
    return false;
  }

  @Override
  public int getElementWeight(final Object element, final boolean isSortByType) {
    if (element instanceof PackageElement) return 2;
    return -1;
  }

  @Override
  public String getElementLocation(final Object element) {
    if (element instanceof PackageElement) {
      final PackageElement packageElement = ((PackageElement)element);
      final Module module = packageElement.getModule();
      return (module != null ? (module.getName() + ":") : "") + packageElement.getPackage().getQualifiedName();
    }
    return null;
  }

  @Override
  public boolean isInvalidElement(final Object element) {
    return element instanceof PackageElement && !((PackageElement)element).getPackage().isValid();
  }

  @Override
  @NotNull
  public String getFavoriteTypeId() {
    return "package";
  }

  @Override
  public String getElementUrl(final Object element) {
    if (element instanceof PackageElement) {
      PackageElement packageElement = (PackageElement)element;
      PsiPackage aPackage = packageElement.getPackage();
      if (aPackage == null) return null;
      return aPackage.getQualifiedName();
    }
    return null;
  }

  @Override
  public String getElementModuleName(final Object element) {
    if (element instanceof PackageElement) {
      PackageElement packageElement = (PackageElement)element;
      Module module = packageElement.getModule();
      return module == null ? null : module.getName();
    }
    return null;
  }

  @Override
  public Object[] createPathFromUrl(final Project project, final String url, final String moduleName) {
    final Module module = moduleName != null ? ModuleManager.getInstance(project).findModuleByName(moduleName) : null;
    // module can be null if 'show module' turned off
    final PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(url);
    if (aPackage == null) return null;
    PackageElement packageElement = new PackageElement(module, aPackage, false);
    return new Object[]{packageElement};
  }

  @Override
  public PsiElement getPsiElement(final Object element) {
    if (element instanceof PackageElement) {
      return ((PackageElement)element).getPackage();
    }
    return super.getPsiElement(element);
  }
}
