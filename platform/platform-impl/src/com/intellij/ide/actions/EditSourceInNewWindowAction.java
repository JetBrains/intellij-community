/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Arrays;

/**
 * @author Konstantin Bulenkov
 */
public class EditSourceInNewWindowAction extends DumbAwareAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    final FileEditorManager manager = FileEditorManager.getInstance(project);
    ((FileEditorManagerImpl)manager).openFileInNewWindow(getVirtualFiles(e)[0]);
  }

  protected VirtualFile[] getVirtualFiles(AnActionEvent e) {
    final VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (files != null) return Arrays.stream(files).filter(file -> !file.isDirectory()).toArray(VirtualFile[]::new);

    final VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    return file == null || file.isDirectory() ? VirtualFile.EMPTY_ARRAY : new VirtualFile[]{file};
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(getEventProject(e) != null && getVirtualFiles(e).length == 1);
  }
}
