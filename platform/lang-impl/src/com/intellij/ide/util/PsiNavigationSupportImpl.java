// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.ide.FileSelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.ide.impl.ProjectViewSelectInTarget;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.intellij.ide.SelectInManager.findSelectInTarget;
import static com.intellij.openapi.wm.ToolWindowId.PROJECT_VIEW;

/**
 * @author yole
 */
public class PsiNavigationSupportImpl extends PsiNavigationSupport {
  @Nullable
  @Override
  public Navigatable getDescriptor(@NotNull PsiElement element) {
    return EditSourceUtil.getDescriptor(element);
  }

  @NotNull
  @Override
  public Navigatable createNavigatable(@NotNull Project project, @NotNull VirtualFile vFile, int offset) {
    return new OpenFileDescriptor(project, vFile, offset);
  }

  @Override
  public boolean canNavigate(@NotNull PsiElement element) {
    return EditSourceUtil.canNavigate(element);
  }

  @Override
  public void navigateToDirectory(@NotNull PsiDirectory psiDirectory, boolean requestFocus) {
    if (Registry.is("ide.navigate.to.directory.into.project.pane")) {
      ProjectViewSelectInTarget.select(psiDirectory.getProject(), this, ProjectViewPane.ID, null, psiDirectory.getVirtualFile(), requestFocus);
    }
    else {
      SelectInTarget target = findSelectInTarget(PROJECT_VIEW, psiDirectory.getProject());
      if (target != null) target.selectIn(new FileSelectInContext(psiDirectory), requestFocus);
    }
  }

  @Override
  public void openDirectoryInSystemFileManager(@NotNull File file) {
    RevealFileAction.openDirectory(file);
  }
}
