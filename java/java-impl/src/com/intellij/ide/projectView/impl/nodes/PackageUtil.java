// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.TreeViewUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class PackageUtil {
  static PsiPackage @NotNull [] getSubpackages(@NotNull PsiPackage aPackage,
                                               @Nullable Module module,
                                               final boolean searchInLibraries) {
    final GlobalSearchScope scopeToShow = getScopeToShow(aPackage.getProject(), module, searchInLibraries);
    List<PsiPackage> result = new ArrayList<>();
    for (PsiPackage psiPackage : aPackage.getSubPackages(scopeToShow)) {
      // skip "default" subpackages as they should be attributed to other modules
      // this is the case when contents of one module is nested into contents of another
      final String name = psiPackage.getName();
      if (name != null && !name.isEmpty()) {
        result.add(psiPackage);
      }
    }
    return result.toArray(PsiPackage.EMPTY_ARRAY);
  }

  static void addPackageAsChild(@NotNull Collection<? super AbstractTreeNode<?>> children,
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
      final PsiPackage[] subpackages = getSubpackages(aPackage, module, inLibrary);
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
    final GlobalSearchScope scopeToShow = getScopeToShow(project, module, inLibrary);
    PsiElement[] children = aPackage.getFiles(scopeToShow);
    if (children.length > 0) {
      return false;
    }
    PsiPackage[] subPackages = aPackage.getSubPackages(scopeToShow);
    if (strictlyEmpty) {
      return subPackages.length == 1;
    }
    return subPackages.length > 0;
  }

  public static PsiDirectory @NotNull [] getDirectories(@NotNull PsiPackage aPackage,
                                                        @Nullable Module module,
                                                        boolean inLibrary) {
    final GlobalSearchScope scopeToShow = getScopeToShow(aPackage.getProject(), module, inLibrary);
    return aPackage.getDirectories(scopeToShow);
  }

  @NotNull
  static GlobalSearchScope getScopeToShow(@NotNull Project project, @Nullable Module module, boolean forLibraries) {
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
  public static Collection<AbstractTreeNode<?>> createPackageViewChildrenOnFiles(@NotNull List<? extends VirtualFile> sourceRoots,
                                                                       @NotNull Project project,
                                                                       @NotNull ViewSettings settings,
                                                                       @Nullable Module module,
                                                                       final boolean inLibrary) {
    final PsiManager psiManager = PsiManager.getInstance(project);

    final List<AbstractTreeNode<?>> children = new ArrayList<>();
    final Set<PsiPackage> topLevelPackages = new HashSet<>();

    for (final VirtualFile root : sourceRoots) {
      ProgressManager.checkCanceled();
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
      if (parentPackageInTree != null && aPackage != null) {
        String prefix = parentPackageInTree.getQualifiedName();
        String string = aPackage.getQualifiedName();
        int length = prefix.length();
        if (length == 0) {
          if (!string.isEmpty()) return string;
        }
        else if (string.startsWith(prefix)) {
          if (length < string.length() && '.' == string.charAt(length)) length++;
          if (length < string.length()) return string.substring(length);
        }
      }
      PsiPackage parentPackage = aPackage.getParentPackage();
      final StringBuilder buf = new StringBuilder();
      buf.append(aPackage.getName());
      while (parentPackage != null && !parentPackage.equals(parentPackageInTree)) {
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

    ModuleLibrariesSearchScope(@NotNull Module module) {
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
    private final ProjectFileIndex myFileIndex;

    ProjectLibrariesSearchScope(@NotNull Project project) {
      super(project);
      myFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return myFileIndex.isInLibraryClasses(file);
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
