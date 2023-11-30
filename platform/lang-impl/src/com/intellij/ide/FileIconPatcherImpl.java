// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.ide;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.PsiIconUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

final class FileIconPatcherImpl implements FileIconProvider {
  @Override
  public @Nullable Icon getIcon(@NotNull VirtualFile file, int flags, Project project) {
    PsiFileSystemItem psiFile = PsiUtilCore.findFileSystemItem(project, file);
    return psiFile == null ? null : PsiIconUtil.getProvidersIcon(psiFile, flags);
  }
}