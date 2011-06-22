/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class RevealFileAction extends AnAction {
  @Override
  public void update(AnActionEvent e) {
    final VirtualFile file = PlatformDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
    final Presentation presentation = e.getPresentation();
    presentation.setText(getActionName());
    presentation.setEnabled(isLocalFile(file));
  }

  public static boolean isLocalFile(@Nullable final VirtualFile file) {
    return file != null && file.isInLocalFileSystem();
  }

  @NotNull
  public static String getActionName() {
    return SystemInfo.isMac ? "Reveal in Finder" : "Show in " + SystemInfo.nativeFileManagerName;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final VirtualFile file = PlatformDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
    assert file != null;

    revealFile(file);
  }

  public static void revealFile(@NotNull final VirtualFile file) {
    File ioFile = new File(file.getPresentableUrl());
    if (!ioFile.isDirectory()) {
      ioFile = ioFile.getParentFile();
    }
    ShowFilePathAction.open(ioFile, new File(file.getPresentableUrl()));
  }
}
