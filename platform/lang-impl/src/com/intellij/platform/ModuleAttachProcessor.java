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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectAttachProcessor;
import com.intellij.projectImport.ProjectOpenedCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author yole
 */
public class ModuleAttachProcessor extends ProjectAttachProcessor {
  private static final Logger LOG = Logger.getInstance(ModuleAttachProcessor.class);
  
  @Override
  public boolean attachToProject(Project project, File projectDir, @Nullable ProjectOpenedCallback callback) {
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
          attachModule(project, imlFile, callback);
          return true;
        }
      }
    }
    int rc = Messages.showYesNoDialog(project, "The project at " + FileUtil.toSystemDependentName(projectDir.getPath()) +
                                               " uses a non-standard layout and cannot be attached to this project. Would you like to open it in a new window?",
                                      "Open Project", Messages.getQuestionIcon());

    return rc != Messages.YES;
  }

  private static void attachModule(Project project, VirtualFile file, @Nullable ProjectOpenedCallback callback) {
    try {
      final ModifiableModuleModel model = ModuleManager.getInstance(project).getModifiableModel();
      final Module module = model.loadModule(file.getPath());

      AccessToken token = WriteAction.start();
      try {
        model.commit();
      }
      finally {
        token.finish();
      }
      final Module newModule = ModuleManager.getInstance(project).findModuleByName(module.getName());
      addPrimaryModuleDependency(project, newModule);

      if (callback != null) {
        callback.projectOpened(project, newModule);
      }
    }
    catch (Exception ex) {
      LOG.info(ex);
      Messages.showErrorDialog(project, "Cannot attach project: " + ex.getMessage(), CommonBundle.getErrorTitle());
    }
  }

  private static void addPrimaryModuleDependency(Project project, @NotNull Module newModule) {
    for (Module oldModule : ModuleManager.getInstance(project).getModules()) {
      if (oldModule != newModule) {
        final VirtualFile[] roots = ModuleRootManager.getInstance(oldModule).getContentRoots();
        for (VirtualFile root : roots) {
          if (root == project.getBaseDir()) {
            final ModifiableRootModel modifiableRootModel = ModuleRootManager.getInstance(oldModule).getModifiableModel();
            modifiableRootModel.addModuleOrderEntry(newModule);
            AccessToken token = WriteAction.start();
            try {
              modifiableRootModel.commit();
            }
            finally {
              token.finish();
            }
            break;
          }
        }
      }
    }
  }
}
