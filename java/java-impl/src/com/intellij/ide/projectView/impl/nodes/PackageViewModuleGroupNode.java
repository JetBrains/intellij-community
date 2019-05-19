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

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class PackageViewModuleGroupNode extends ModuleGroupNode {

  public PackageViewModuleGroupNode(final Project project, @NotNull ModuleGroup value, final ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @NotNull
  @Override
  protected AbstractTreeNode createModuleNode(@NotNull Module module) {
    return new PackageViewModuleNode(module.getProject(), module, getSettings());
  }

  @NotNull
  @Override
  protected ModuleGroupNode createModuleGroupNode(@NotNull ModuleGroup moduleGroup) {
    return new PackageViewModuleGroupNode(getProject(), moduleGroup, getSettings());
  }

  @NotNull
  @Override
  protected List<Module> getModulesByFile(@NotNull VirtualFile file) {
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    Module module = fileIndex.getModuleForFile(file, false);
    if (module != null) {
      return Collections.singletonList(module);
    }
    List<OrderEntry> entriesForFile = fileIndex.getOrderEntriesForFile(file);
    return ContainerUtil.map(entriesForFile, OrderEntry::getOwnerModule);
  }
}
