// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters.impl;

import com.intellij.execution.filters.HyperlinkInfoFactory;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public final class MultipleFilesHyperlinkInfo extends MultipleFilesHyperlinkInfoBase {
  private final List<? extends VirtualFile> myVirtualFiles;

  MultipleFilesHyperlinkInfo(@NotNull List<? extends VirtualFile> virtualFiles, int lineNumber, @NotNull Project project) {
    this(virtualFiles, lineNumber, project, null);
  }

  MultipleFilesHyperlinkInfo(@NotNull List<? extends VirtualFile> virtualFiles,
                             int lineNumber,
                             @NotNull Project project,
                             @Nullable HyperlinkInfoFactory.HyperlinkHandler action) {
    super(lineNumber, project, action);
    myVirtualFiles = virtualFiles;
  }

  @Override
  public @NotNull List<PsiFile> getFiles(@NotNull Project project) {
    List<PsiFile> currentFiles = new ArrayList<>();
    for (VirtualFile file : myVirtualFiles) {
      if (!file.isValid()) continue;

      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile != null) {
        PsiElement navigationElement = psiFile.getNavigationElement(); // Sources may be downloaded.
        if (navigationElement instanceof PsiFile) {
          currentFiles.add((PsiFile)navigationElement);
          continue;
        }
        currentFiles.add(psiFile);
      }
    }
    return currentFiles;
  }

  @Override
  public @Nullable OpenFileDescriptor getDescriptor() {
    VirtualFile file = getPreferredFile();
    return file != null ? new OpenFileDescriptor(myProject, file, myLineNumber, 0) : null;
  }

  public List<? extends VirtualFile> getFilesVariants() {
    return myVirtualFiles;
  }

  private @Nullable VirtualFile getPreferredFile() {
    return ContainerUtil.getFirstItem(myVirtualFiles);
  }
}
