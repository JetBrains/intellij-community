// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.idea.ActionsBundle;
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
import java.util.Collections;

public abstract class AbstractModuleNode extends ProjectViewNode<Module> implements NavigatableWithText {
  protected AbstractModuleNode(Project project, @NotNull Module module, ViewSettings viewSettings) {
    super(project, module, viewSettings);
  }

  @Override
  public void update(@NotNull PresentationData presentation) {
    Module module = getValue();
    if (module == null || module.isDisposed()) {
      setValue(null);
      return;
    }

    presentation.setPresentableText(module.getName());
    if (showModuleNameInBold()) {
      presentation.addText(module.getName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    }

    presentation.setIcon(ModuleType.get(module).getIcon());

    presentation.setTooltip(ModuleType.get(module).getName());
  }

  protected boolean showModuleNameInBold() {
    return true;
  }

  @Override
  public @NotNull Collection<VirtualFile> getRoots() {
    Module module = getValue();
    return module != null && !module.isDisposed()
           ? Arrays.asList(ModuleRootManager.getInstance(module).getContentRoots())
           : Collections.emptyList();
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    Module module = getValue();
    if (module == null || module.isDisposed()) return false;

    if (file.getFileSystem() instanceof JarFileSystem) {
      VirtualFile local = JarFileSystem.getInstance().getVirtualFileForJar(file);
      if (local == null) return false;
      file = local;
    }

    for (VirtualFile root : ModuleRootManager.getInstance(module).getContentRoots()) {
      if (VfsUtilCore.isAncestor(root, file, false)) return true;
    }

    return false;
  }

  @Override
  public void navigate(final boolean requestFocus) {
    Module module = getValue();
    if (module != null && !module.isDisposed()) {
      ProjectSettingsService.getInstance(myProject).openModuleSettings(module);
    }
  }

  @Override
  public String getNavigateActionText(boolean focusEditor) {
    return ActionsBundle.message("action.ModuleSettings.navigate");
  }

  @Override
  public boolean canNavigate() {
    Module module = getValue();
    return module != null && !module.isDisposed() && ProjectSettingsService.getInstance(myProject).canOpenModuleSettings();
  }

  @SuppressWarnings("deprecation")
  @Override
  public String getTestPresentation() {
    return "Module";
  }
}
