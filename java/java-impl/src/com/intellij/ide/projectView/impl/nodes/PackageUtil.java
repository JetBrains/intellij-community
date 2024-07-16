// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.TreeViewUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public final class PackageUtil {

  public static boolean isPackageEmpty(@NotNull PsiPackage aPackage,
                                       @Nullable Module module,
                                       boolean strictlyEmpty,
                                       final boolean inLibrary) {
    return new PackageNodeBuilder(module, inLibrary).isPackageEmpty(aPackage, strictlyEmpty);
  }

  public static PsiDirectory @NotNull [] getDirectories(@NotNull PsiPackage aPackage,
                                                        @Nullable Module module,
                                                        boolean inLibrary) {
    final GlobalSearchScope scopeToShow = getScopeToShow(aPackage.getProject(), module, inLibrary);
    return aPackage.getDirectories(scopeToShow);
  }

  static @NotNull GlobalSearchScope getScopeToShow(@NotNull Project project, @Nullable Module module, boolean forLibraries) {
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

  public static @NotNull Collection<AbstractTreeNode<?>> createPackageViewChildrenOnFiles(@NotNull List<? extends VirtualFile> sourceRoots,
                                                                                          @NotNull Project project,
                                                                                          @NotNull ViewSettings settings,
                                                                                          @Nullable Module module,
                                                                                          final boolean inLibrary) {
    return new PackageNodeBuilder(module, inLibrary).createPackageViewChildrenOnFiles(sourceRoots, project, settings);
  }

  public static @NotNull String getNodeName(@NotNull ViewSettings settings,
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
