// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.ide.actions.RevealFileAction;
import com.intellij.ide.impl.ProjectViewSelectInTarget;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ProjectFileNavigatorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;


public class PsiNavigationSupportImpl extends PsiNavigationSupport {
  @Override
  public @Nullable Navigatable getDescriptor(@NotNull PsiElement element) {
    return EditSourceUtil.getDescriptor(element);
  }

  @Override
  public @NotNull Navigatable createNavigatable(@NotNull Project project, @NotNull VirtualFile vFile, int offset) {
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
      ProjectFileNavigatorImpl.getInstance(psiDirectory.getProject()).scheduleNavigateInProjectView(psiDirectory.getVirtualFile(), requestFocus);
    }
  }

  @Override
  public void openDirectoryInSystemFileManager(@NotNull File file) {
    RevealFileAction.openDirectory(file);
  }
}
