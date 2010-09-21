/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;

public class RevealFileAction extends AnAction {

  @Override
  public void update(AnActionEvent e) {
    VirtualFile file = PlatformDataKeys.VIRTUAL_FILE.getData(e.getDataContext());

    if (file != null && file.isInLocalFileSystem()) {
      if (SystemInfo.isMac) {
        e.getPresentation().setText("Reveal in Finder");
      } else {
        e.getPresentation().setText("Show in Explorer");
      }
    } else {
      e.getPresentation().setEnabled(false);
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    VirtualFile file = PlatformDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
    File ioFile = new File(file.getPresentableUrl());
    if (!ioFile.isDirectory()) {
      ioFile = ioFile.getParentFile();
    }
    ShowFilePathAction.open(ioFile, ioFile);
  }
}

