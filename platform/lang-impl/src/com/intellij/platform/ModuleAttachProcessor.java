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
package com.intellij.platform;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectAttachProcessor;

import java.io.File;

/**
 * @author yole
 */
public class ModuleAttachProcessor extends ProjectAttachProcessor {
  private static final Logger LOG = Logger.getInstance(ModuleAttachProcessor.class);
  
  @Override
  public boolean attachToProject(Project project, File projectDir) {
    if (!projectDir.exists()) {
      Project newProject = ((ProjectManagerEx) ProjectManager.getInstance()).newProject(projectDir.getParentFile().getName(), projectDir.getParent(), true, false);
      if (newProject == null) {
        return false;
      }
      final VirtualFile baseDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(projectDir.getParent());
      PlatformProjectOpenProcessor.runDirectoryProjectConfigurators(baseDir, newProject);
      newProject.save();
      AccessToken token = ApplicationManager.getApplication().acquireWriteActionLock(null);
      try {
        Disposer.dispose(newProject);
      }
      finally {
        token.finish();
      }
    }
    for(String file: projectDir.list()) {
      if (FileUtil.getExtension(file).equals("iml")) {
        VirtualFile imlFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(projectDir, file));
        if (imlFile != null) {
          attachModule(project, imlFile);
          return true;
        }
      }
    }

    return false;
  }

  private static void attachModule(Project project, VirtualFile file) {
    try {
      final ModifiableModuleModel model = ModuleManager.getInstance(project).getModifiableModel();
      model.loadModule(file.getPath());

      AccessToken token = WriteAction.start();
      try {
        model.commit();
      }
      finally {
        token.finish();
      }
    }
    catch (Exception ex) {
      LOG.info(ex);
      Messages.showErrorDialog(project, "Cannot attach project: " + ex.getMessage(), CommonBundle.getErrorTitle());
    }
  }
}
