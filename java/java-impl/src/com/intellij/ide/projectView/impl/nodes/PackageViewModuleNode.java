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

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;

public class PackageViewModuleNode extends AbstractModuleNode{
  public PackageViewModuleNode(Project project, Module value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  public PackageViewModuleNode(Project project, Object value, ViewSettings viewSettings) {
    this(project, (Module)value, viewSettings);
  }

  @Override
  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    final Collection<AbstractTreeNode> result = PackageUtil.createPackageViewChildrenOnFiles(Arrays.asList(ModuleRootManager.getInstance(getValue()).getSourceRoots()), myProject, getSettings(), getValue(), false);
    if (getSettings().isShowLibraryContents()) {
      result.add(new PackageViewLibrariesNode(getProject(), getValue(),getSettings()));
    }
    return result;

  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    Module module = getValue();
    return module != null && !module.isDisposed() &&
           (ModuleUtilCore.moduleContainsFile(module, file, false) || ModuleUtilCore.moduleContainsFile(module, file, true));
  }

  @Override
  public boolean validate() {
    return getValue() != null;
  }

  @Override
  public boolean someChildContainsFile(VirtualFile file) {
    return true;
  }

  @Override
  public void update(PresentationData presentation) {
    super.update(presentation);
    // Android Studio: Change icon to match with module icon
    Icon icon = getModuleIcon();
    if (icon != null) {
      presentation.setIcon(icon);
    }
  }

  /**
   * Android Studio: Use icon based on type of module
   *
   * @return And icon based in module type if module is not {@code null} and is not disposed, {@code null} otherwise
   */
  @Nullable
  private Icon getModuleIcon() {
    Module module = getValue();
    if (module != null && !module.isDisposed()) {
      VirtualFile virtualFile = module.getModuleFile();
      Icon icon = null;
      if (virtualFile != null) {
        icon = IconUtil.getIcon(virtualFile, 0, module.getProject());
      }
      if (icon == null) {
        icon = ModuleType.get(module).getIcon();
      }
      return icon;
    }
    return null;
  }
}
