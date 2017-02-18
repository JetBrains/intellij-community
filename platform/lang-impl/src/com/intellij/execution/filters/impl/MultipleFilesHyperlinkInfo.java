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

import com.intellij.execution.filters.FileHyperlinkInfo;
import com.intellij.execution.filters.HyperlinkInfoBase;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.ide.util.gotoByName.GotoFileCellRenderer;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
* @author nik
*/
class MultipleFilesHyperlinkInfo extends HyperlinkInfoBase implements FileHyperlinkInfo {
  private final List<VirtualFile> myVirtualFiles;
  private final int myLineNumber;
  private final Project myProject;

  public MultipleFilesHyperlinkInfo(@NotNull List<VirtualFile> virtualFiles, int lineNumber, @NotNull Project project) {
    myVirtualFiles = virtualFiles;
    myLineNumber = lineNumber;
    myProject = project;
  }

  @Override
  public void navigate(@NotNull final Project project, @Nullable RelativePoint hyperlinkLocationPoint) {
    List<PsiFile> currentFiles = new ArrayList<>();

    AccessToken accessToken = ReadAction.start();
    try {
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
    }
    finally {
      accessToken.finish();
    }

    if (currentFiles.isEmpty()) return;

    if (currentFiles.size() == 1) {
      new OpenFileHyperlinkInfo(myProject, currentFiles.get(0).getVirtualFile(), myLineNumber).navigate(project);
    }
    else {
      final JBList list = new JBList(currentFiles);
      int width = WindowManager.getInstance().getFrame(project).getSize().width;
      list.setCellRenderer(new GotoFileCellRenderer(width));
      JBPopup popup = JBPopupFactory.getInstance().createListPopupBuilder(list)
        .setTitle("Choose Target File")
        .setItemChoosenCallback(() -> {
          VirtualFile file = ((PsiFile)list.getSelectedValue()).getVirtualFile();
          new OpenFileHyperlinkInfo(myProject, file, myLineNumber).navigate(project);
        })
        .createPopup();
      if (hyperlinkLocationPoint != null) {
        popup.show(hyperlinkLocationPoint);
      }
      else {
        popup.showInFocusCenter();
      }
    }
  }

  @Nullable
  @Override
  public OpenFileDescriptor getDescriptor() {
    VirtualFile file = getPreferredFile();
    return file != null ? new OpenFileDescriptor(myProject, file, myLineNumber, 0) : null;
  }

  @Nullable
  private VirtualFile getPreferredFile() {
    return ContainerUtil.getFirstItem(myVirtualFiles);
  }
}
