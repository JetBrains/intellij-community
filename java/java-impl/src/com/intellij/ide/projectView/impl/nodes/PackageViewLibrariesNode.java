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
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
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
    if (!index.isInLibrarySource(file) && !index.isInLibraryClasses(file)) return false;

    return someChildContainsFile(file, false);
  }

  @Override
  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
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
  }


  @Override
  public boolean someChildContainsFile(VirtualFile file) {
    ProjectFileIndex index = ProjectRootManager.getInstance(getProject()).getFileIndex();
    if (!index.isInLibrarySource(file) && !index.isInLibraryClasses(file)) return false;
    return super.someChildContainsFile(file);    
  }

  private static void addModuleLibraryRoots(ModuleRootManager moduleRootManager, List<VirtualFile> roots) {
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
  public void update(final PresentationData presentation) {
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
  public int getWeight() {
    return 60;
  }
}
