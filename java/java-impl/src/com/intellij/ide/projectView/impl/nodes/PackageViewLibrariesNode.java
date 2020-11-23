// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.*;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeUi;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PackageViewLibrariesNode extends ProjectViewNode<LibrariesElement>{
  public PackageViewLibrariesNode(final Project project, Module module, final ViewSettings viewSettings) {
    super(project, new LibrariesElement(module, project), viewSettings);
  }

  @Override
  public boolean contains(@NotNull final VirtualFile file) {
    ProjectFileIndex index = ProjectRootManager.getInstance(getProject()).getFileIndex();
    if (!index.isInLibrary(file)) return false;

    return someChildContainsFile(file, false);
  }

  @Override
  @NotNull
  public Collection<AbstractTreeNode<?>> getChildren() {
    return AbstractTreeUi.calculateYieldingToWriteAction(() -> {
      final ArrayList<VirtualFile> roots = new ArrayList<>();
      Module myModule = getValue().getModule();
      if (myModule == null) {
        final Module[] modules = ModuleManager.getInstance(getProject()).getModules();
        for (Module module : modules) {
          addModuleLibraryRoots(ModuleRootManager.getInstance(module), roots);
        }
      }
      else {
        addModuleLibraryRoots(ModuleRootManager.getInstance(myModule), roots);
      }
      return PackageUtil.createPackageViewChildrenOnFiles(roots, getProject(), getSettings(), null, true);
    });
  }

  @Override
  public boolean someChildContainsFile(VirtualFile file) {
    ProjectFileIndex index = ProjectRootManager.getInstance(getProject()).getFileIndex();
    if (!index.isInLibrary(file)) return false;
    return super.someChildContainsFile(file);
  }

  private static void addModuleLibraryRoots(ModuleRootManager moduleRootManager, List<? super VirtualFile> roots) {
    final VirtualFile[] files = moduleRootManager.orderEntries().withoutModuleSourceEntries().withoutDepModules().classes().getRoots();
    for (final VirtualFile file : files) {
      if (file.getFileSystem() instanceof JarFileSystem && file.getParent() != null) {
        // skip entries inside jars
        continue;
      }
      roots.add(file);
    }
  }

  @Override
  public void update(@NotNull final PresentationData presentation) {
    presentation.setPresentableText(IdeBundle.message("node.projectview.libraries"));
    presentation.setIcon(PlatformIcons.LIBRARY_ICON);
  }

  @Override
  public String getTestPresentation() {
    return "Libraries";
  }

  @Override
  public boolean shouldUpdateData() {
    return true;
  }

  @Override
  public @NotNull NodeSortOrder getSortOrder(@NotNull NodeSortSettings settings) {
    return NodeSortOrder.LIBRARY_ROOT;
  }
}
