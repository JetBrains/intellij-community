/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.platform.templates;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.ZipUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Dmitry Avdeev
 *         Date: 10/5/12
 */
public class SaveProjectAsTemplateAction extends AnAction {

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = getEventProject(e);
    assert project != null;
    StorageScheme scheme = ((ProjectEx)project).getStateStore().getStorageScheme();
    if (scheme != StorageScheme.DIRECTORY_BASED) {
      Messages.showErrorDialog(project, "Project templates do not support old .ipr (file-based) format.\n" +
                                        "Please convert your project via File->Save as Directory-Based format.", CommonBundle.getErrorTitle());
      return;
    }

    VirtualFile descriptionFile = VfsUtil.findRelativeFile(project.getBaseDir(), ArchivedProjectTemplate.DESCRIPTION_PATH.split("/"));
    SaveProjectAsTemplateDialog dialog = new SaveProjectAsTemplateDialog(project, descriptionFile);
    if (dialog.showAndGet()) {
      File file = dialog.getTemplateFile();
      ZipOutputStream stream = null;
      try {
        file.getParentFile().mkdirs();
        stream = new ZipOutputStream(new FileOutputStream(file));

        VirtualFile dir = getDirectoryToSave(project, dialog.getModuleToSave());
        String description = dialog.getDescription();
        if (descriptionFile == null) {
          stream.putNextEntry(new ZipEntry(dir.getName() + "/" + ArchivedProjectTemplate.DESCRIPTION_PATH));
          stream.write(description.getBytes());
          stream.closeEntry();
        }
        else {
          VfsUtil.saveText(descriptionFile, description);
        }
        ZipUtil.addDirToZipRecursively(stream, null, new File(dir.getPath()), dir.getName(), new FileFilter() {
          @Override
          public boolean accept(File pathname) {
            if (!".idea".equals(pathname.getParent())) return true;
            // todo filter out some garbage from .idea
            return true;
          }
        }, null);
        Messages.showInfoMessage(FileUtil.getNameWithoutExtension(file) + " was successfully created.\n" +
                                 "It's available now in Project Wizard", "Template Created");
      }
      catch (IOException ex) {
        Messages.showErrorDialog(project, ex.getMessage(), "Error");
      }
      finally {
        StreamUtil.closeStream(stream);
      }
    }
  }

  private static VirtualFile getDirectoryToSave(Project project, @Nullable String moduleName) {
    if (moduleName == null) {
      return project.getBaseDir();
    }
    else {
      Module module = ModuleManager.getInstance(project).findModuleByName(moduleName);
      assert module != null : "Can't find module " + moduleName;
      VirtualFile moduleFile = module.getModuleFile();
      assert moduleFile != null;
      return moduleFile.getParent();
    }
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = getEventProject(e);
    e.getPresentation().setEnabled(project != null && !project.isDefault());
  }
}
