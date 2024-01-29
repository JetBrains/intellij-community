/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
  @NotNull
  public List<PsiFile> getFiles(@NotNull Project project) {
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

  @Nullable
  @Override
  public OpenFileDescriptor getDescriptor() {
    VirtualFile file = getPreferredFile();
    return file != null ? new OpenFileDescriptor(myProject, file, myLineNumber, 0) : null;
  }

  public List<? extends VirtualFile> getFilesVariants() {
    return myVirtualFiles;
  }

  @Nullable
  private VirtualFile getPreferredFile() {
    return ContainerUtil.getFirstItem(myVirtualFiles);
  }
}
