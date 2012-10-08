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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.ZipUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
    VirtualFile descriptionFile = VfsUtil.findRelativeFile(project.getBaseDir(), "description.html");
    SaveProjectAsTemplateDialog dialog = new SaveProjectAsTemplateDialog(project, descriptionFile);
    if (dialog.showAndGet()) {
      File file = dialog.getTemplateFile();
      ZipOutputStream stream = null;
      try {
        file.getParentFile().mkdirs();
        file.createNewFile();
        stream = new ZipOutputStream(new FileOutputStream(file));
        VirtualFile dir = project.getBaseDir();
        ZipUtil.addDirToZipRecursively(stream, null, new File(dir.getPath()), dir.getName(), null, null);
      }
      catch (IOException ex) {
        Messages.showErrorDialog(project, ex.getMessage(), "Error");
      }
      finally {
        StreamUtil.closeStream(stream);
      }
    }
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = getEventProject(e);
    e.getPresentation().setVisible(ApplicationManager.getApplication().isInternal());
    e.getPresentation().setVisible(project != null && !project.isDefault());
  }
}
