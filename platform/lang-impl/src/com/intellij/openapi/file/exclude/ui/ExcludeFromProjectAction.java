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
package com.intellij.openapi.file.exclude.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.file.exclude.ProjectFileExclusionManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author Rustam Vishnyakov
 */
public class ExcludeFromProjectAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) return;
    final VirtualFile file = PlatformDataKeys.VIRTUAL_FILE.getData(dataContext);
    if (file == null) return;
    if (file.isDirectory()) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          addExcludedFolder(project, file);
        }
      });
    }
    else {
      ProjectFileExclusionManager fileExManager = ProjectFileExclusionManager.getInstance(project);
      if (fileExManager == null) return;
      fileExManager.addExclusion(file);
    }
  }

  private static void addExcludedFolder(Project project, VirtualFile folder) {
    Module module = ModuleUtil.findModuleForFile(folder, project);
    if (module == null) return;
    ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    for (ContentEntry entry : model.getContentEntries()) {
      VirtualFile entryFile = entry.getFile();
      if (entryFile != null) {
        if (VfsUtil.isAncestor(entryFile, folder, true)) {
          entry.addExcludeFolder(folder);
          model.commit();
          return;
        }
      }
    }
    model.dispose();
  }

  @Override
  public void update(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final VirtualFile file = PlatformDataKeys.VIRTUAL_FILE.getData(dataContext);
    final Presentation presentation = e.getPresentation();
    if (file == null) {
      presentation.setVisible(false);
      return;
    }
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project != null) {
      if (file.equals(project.getBaseDir()) || !file.isWritable()) {
        presentation.setEnabled(false);
      }
    }
  }
}
