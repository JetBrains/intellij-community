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
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class RevealFileAction extends DumbAwareAction {

  public RevealFileAction() {
    getTemplatePresentation().setText(getActionName());
  }

  @Override
  public void update(AnActionEvent e) {
    VirtualFile file = ShowFilePathAction.findLocalFile(CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext()));
    Presentation presentation = e.getPresentation();
    presentation.setText(getActionName());
    presentation.setEnabled(file != null);
  }

  @NotNull
  public static String getActionName() {
    return SystemInfo.isMac ? "Reveal in Finder" : "Show in " + ShowFilePathAction.getFileManagerName();
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    VirtualFile file = ShowFilePathAction.findLocalFile(CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext()));
    if (file != null) {
      ShowFilePathAction.openFile(new File(file.getPresentableUrl()));
    }
  }
}
