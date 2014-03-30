/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.TreeViewUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.roots.impl.DirectoryInfo;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PackageUtil {
  @NotNull
  public static PsiPackage[] getSubpackages(@NotNull PsiPackage aPackage,
                                            @Nullable Module module,
                                            @NotNull Project project,
                                            final boolean searchInLibraries) {
    final PsiDirectory[] dirs = getDirectories(aPackage, project, module, searchInLibraries);
    final Set<PsiPackage> subpackages = new HashSet<PsiPackage>();
    for (PsiDirectory dir : dirs) {
      final PsiDirectory[] subdirectories = dir.getSubdirectories();
      for (PsiDirectory subdirectory : subdirectories) {
        final PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(subdirectory);
        if (psiPackage != null) {
          final String name = psiPackage.getName();
          // skip "default" subpackages as they should be attributed to other modules
          // this is the case when contents of one module is nested into contents of another
          if (name != null && !name.isEmpty()) {
            subpackages.add(psiPackage);
          }
        }
      }
    }
    return subpackages.toArray(new PsiPackage[subpackages.size()]);
  }

  public static void addPackageAsChild(@NotNull Collection<AbstractTreeNode> children,
                                       @NotNull PsiPackage aPackage,
                                       @Nullable Module module,
                                       @NotNull ViewSettings settings,
                                       final boolean inLibrary) {
    final boolean shouldSkipPackage = settings.isHideEmptyMiddlePackages() && isPackageEmpty(aPackage, module, !settings.isFlattenPackages(), inLibrary);
    final Project project = aPackage.getProject();
    if (!shouldSkipPackage) {
      children.add(new PackageElementNode(project, new PackageElement(module, aPackage, inLibrary), settings));
    }
    if (settings.isFlattenPackages() || shouldSkipPackage) {
      final PsiPackage[] subpackages = getSubpackages(aPackage, module, project, inLibrary);
      for (PsiPackage subpackage : subpackages) {
        addPackageAsChild(children, subpackage, module, settings, inLibrary);
      }
    }
  }

  public static boolean isPackageEmpty(@NotNull PsiPackage aPackage,
                                       @Nullable Module module,
                                       boolean strictlyEmpty,
                                       final boolean inLibrary) {
    final Project project = aPackage.getProject();
    final PsiDirectory[] dirs = getDirectories(aPackage, project, module, inLibrary);
    for (final PsiDirectory dir : dirs) {
      if (!TreeViewUtil.isEmptyMiddlePackage(dir, strictlyEmpty)) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  public static PsiDirectory[] getDirectories(@NotNull PsiPackage aPackage,
                                              @NotNull Project project,
                                              @Nullable Module module,
                                              boolean inLibrary) {
    final GlobalSearchScope scopeToShow = getScopeToShow(project, module, inLibrary);
    return aPackage.getDirectories(scopeToShow);
  }

  @NotNull
  private static GlobalSearchScope getScopeToShow(@NotNull Project project, @Nullable Module module, boolean forLibraries) {
    if (module == null) {
      if (forLibraries) {
        return new ProjectLibrariesSearchScope(project);
      }
      return GlobalSearchScope.projectScope(project);
    }
    else {
      if (forLibraries) {
        return new ModuleLibrariesSearchScope(module);
      }
      return GlobalSearchScope.moduleScope(module);
    }
  }


  public static boolean isPackageDefault(@NotNull PsiPackage directoryPackage) {
    final String qName = directoryPackage.getQualifiedName();
    return qName.isEmpty();
  }

  @NotNull
  public static Collection<AbstractTreeNode> createPackageViewChildrenOnFiles(@NotNull List<VirtualFile> sourceRoots,
                                                                              @NotNull Project project,
                                                                              @NotNull ViewSettings settings,
                                                                              @Nullable Module module,
                                                                              final boolean inLibrary) {
    final PsiManager psiManager = PsiManager.getInstance(project);

    final List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
    final Set<PsiPackage> topLevelPackages = new HashSet<PsiPackage>();

    for (final VirtualFile root : sourceRoots) {
      final PsiDirectory directory = psiManager.findDirectory(root);
      if (directory == null) {
        continue;
      }
      final PsiPackage directoryPackage = JavaDirectoryService.getInstance().getPackage(directory);
      if (directoryPackage == null || isPackageDefault(directoryPackage)) {
        // add subpackages
        final PsiDirectory[] subdirectories = directory.getSubdirectories();
        for (PsiDirectory subdirectory : subdirectories) {
          final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(subdirectory);
          if (aPackage != null && !isPackageDefault(aPackage)) {
            topLevelPackages.add(aPackage);
          }
        }
        // add non-dir items
        children.addAll(ProjectViewDirectoryHelper.getInstance(project).getDirectoryChildren(directory, settings, false));
      }
      else {
        topLevelPackages.add(directoryPackage);
      }
    }

    for (final PsiPackage topLevelPackage : topLevelPackages) {
      addPackageAsChild(children, topLevelPackage, module, settings, inLibrary);
    }

    return children;
  }

  @NotNull
  public static String getNodeName(@NotNull ViewSettings settings,
                                   PsiPackage aPackage,
                                   final PsiPackage parentPackageInTree,
                                   @NotNull String defaultShortName,
                                   boolean isFQNameShown) {
    final String name;
    if (isFQNameShown) {
      name = settings.isAbbreviatePackageNames() ?
             aPackage == null ? defaultShortName : TreeViewUtil.calcAbbreviatedPackageFQName(aPackage) :
             aPackage == null ? defaultShortName : aPackage.getQualifiedName();
    }
    else if (parentPackageInTree != null || aPackage != null && aPackage.getParentPackage() != null) {
      PsiPackage parentPackage = aPackage.getParentPackage();
      final StringBuilder buf = new StringBuilder();
      buf.append(aPackage.getName());
      while (parentPackage != null && (parentPackageInTree == null || !parentPackage.equals(parentPackageInTree))) {
        final String parentPackageName = parentPackage.getName();
        if (parentPackageName == null || parentPackageName.isEmpty()) {
          break; // reached default package
        }
        buf.insert(0, ".");
        buf.insert(0, parentPackageName);
        parentPackage = parentPackage.getParentPackage();
      }
      name = buf.toString();
    }
    else {
      name = defaultShortName;
    }
    return name;
  }

  private static class ModuleLibrariesSearchScope extends GlobalSearchScope {
    private final Module myModule;

    public ModuleLibrariesSearchScope(@NotNull Module module) {
      super(module.getProject());
      myModule = module;
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      final OrderEntry orderEntry = ModuleRootManager.getInstance(myModule).getFileIndex().getOrderEntryForFile(file);
      return orderEntry instanceof JdkOrderEntry || orderEntry instanceof LibraryOrderEntry;
    }

    @Override
    public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
      final ModuleFileIndex fileIndex = ModuleRootManager.getInstance(myModule).getFileIndex();
      return Comparing.compare(fileIndex.getOrderEntryForFile(file2), fileIndex.getOrderEntryForFile(file1));
    }

    @Override
    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return false;
    }

    @Override
    public boolean isSearchInLibraries() {
      return true;
    }
  }

  private static class ProjectLibrariesSearchScope extends GlobalSearchScope {
    private final DirectoryIndex myDirectoryIndex;

    public ProjectLibrariesSearchScope(@NotNull Project project) {
      super(project);
      myDirectoryIndex = DirectoryIndex.getInstance(project);
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      VirtualFile dir = file.isDirectory() ? file : file.getParent();
      if (dir == null) return false;

      DirectoryInfo info = myDirectoryIndex.getInfoForDirectory(dir);
      return info != null && info.hasLibraryClassRoot();
    }

    @Override
    public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
      throw new IncorrectOperationException("not implemented");
    }

    @Override
    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return false;
    }

    @Override
    public boolean isSearchInLibraries() {
      return true;
    }
  }
}
