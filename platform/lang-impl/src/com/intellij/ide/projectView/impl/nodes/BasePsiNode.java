// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public abstract class BasePsiNode <T extends PsiElement> extends AbstractPsiBasedNode<T> {
  private final @Nullable VirtualFile myVirtualFile;

  protected BasePsiNode(Project project, @NotNull T value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
    myVirtualFile = PsiUtilCore.getVirtualFile(value);
  }

  @Override
  public FileStatus getFileStatus() {
    return computeFileStatus(getVirtualFile(), Objects.requireNonNull(getProject()));
  }

  @Override
  public @Nullable VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  @Override
  protected @Nullable PsiElement extractPsiFromValue() {
    return getValue();
  }
}
