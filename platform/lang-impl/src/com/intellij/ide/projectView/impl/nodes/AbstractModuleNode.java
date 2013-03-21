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
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.NavigatableWithText;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

public abstract class AbstractModuleNode extends ProjectViewNode<Module> implements NavigatableWithText {
  protected AbstractModuleNode(Project project, Module module, ViewSettings viewSettings) {
    super(project, module, viewSettings);
  }

  @Override
  public void update(PresentationData presentation) {
    if (getValue().isDisposed()) {
      setValue(null);
      return;
    }
    presentation.setPresentableText(getValue().getName());
    if (showModuleNameInBold()) {
      presentation.addText(getValue().getName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    }

    presentation.setIcon(ModuleType.get(getValue()).getIcon());
  }

  protected boolean showModuleNameInBold() {
    return true;
  }


  @Override
  public String getTestPresentation() {
    return "Module";
  }

  @Override
  public Collection<VirtualFile> getRoots() {
    return Arrays.asList(ModuleRootManager.getInstance(getValue()).getContentRoots());
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    Module module = getValue();
    if (module == null || module.isDisposed()) return false;

    final VirtualFile testee;
    if (file.getFileSystem() instanceof JarFileSystem) {
      testee = JarFileSystem.getInstance().getVirtualFileForJar(file);
      if (testee == null) return false;
    }
    else {
      testee = file;
    }
    for (VirtualFile root : ModuleRootManager.getInstance(module).getContentRoots()) {
      if (VfsUtilCore.isAncestor(root, testee, false)) return true;
    }
    return false;
  }

  @Override
  public String getToolTip() {
    final Module module = getValue();
    return ModuleType.get(module).getName();
  }

  @Override
  public void navigate(final boolean requestFocus) {
    Module module = getValue();
    if (module != null) {
      ProjectSettingsService.getInstance(myProject).openModuleSettings(module);
    }
  }

  @Override
  public String getNavigateActionText(boolean focusEditor) {
    return "Open Module Settings";
  }

  @Override
  public boolean canNavigate() {
    return ProjectSettingsService.getInstance(myProject).canOpenModuleSettings() && getValue() != null;
  }
}
