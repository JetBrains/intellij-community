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

package org.intellij.images.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EnvironmentUtil;
import org.intellij.images.ImagesBundle;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.intellij.images.options.Options;
import org.intellij.images.options.OptionsManager;
import org.intellij.images.options.impl.OptionsConfigurabe;

import java.io.File;
import java.util.Map;

/**
 * Open image file externally.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public final class EditExternallyAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    Options options = OptionsManager.getInstance().getOptions();
    String executablePath = options.getExternalEditorOptions().getExecutablePath();
    if (StringUtil.isEmpty(executablePath)) {
      Messages.showErrorDialog(project,
                               ImagesBundle.message("error.empty.external.editor.path"),
                               ImagesBundle.message("error.title.empty.external.editor.path"));
      OptionsConfigurabe.show(project);
    }
    else {
      if (files != null) {
        Map<String, String> env = EnvironmentUtil.getEnvironmentMap();
        for (String varName : env.keySet()) {
          if (SystemInfo.isWindows) {
            executablePath = StringUtil.replace(executablePath, "%" + varName + "%", env.get(varName), true);
          }
          else {
            executablePath = StringUtil.replace(executablePath, "${" + varName + "}", env.get(varName), false);
          }
        }
        executablePath = FileUtil.toSystemDependentName(executablePath);
        File executable = new File(executablePath);
        GeneralCommandLine commandLine = new GeneralCommandLine();
        final String path = executable.exists() ? executable.getAbsolutePath() : executablePath;
        if (SystemInfo.isMac) {
          commandLine.setExePath(ExecUtil.getOpenCommandPath());
          commandLine.addParameter("-a");
          commandLine.addParameter(path);
        } else {
          commandLine.setExePath(path);
        }

        ImageFileTypeManager typeManager = ImageFileTypeManager.getInstance();
        for (VirtualFile file : files) {
          if (file.isInLocalFileSystem() && typeManager.isImage(file)) {
            commandLine.addParameter(VfsUtilCore.virtualToIoFile(file).getAbsolutePath());
          }
        }
        commandLine.setWorkDirectory(new File(executablePath).getParentFile());

        try {
          commandLine.createProcess();
        }
        catch (ExecutionException ex) {
          Messages.showErrorDialog(project, ex.getLocalizedMessage(), ImagesBundle.message("error.title.launching.external.editor"));
          OptionsConfigurabe.show(project);
        }
      }
    }
  }

  public void update(AnActionEvent e) {
    super.update(e);

    doUpdate(e);
  }

  static void doUpdate(AnActionEvent e) {
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    final boolean isEnabled = isImages(files);
    if (e.getPlace().equals(ActionPlaces.PROJECT_VIEW_POPUP)) {
      e.getPresentation().setVisible(isEnabled);
    }
    else {
      e.getPresentation().setEnabled(isEnabled);
    }
  }

  private static boolean isImages(VirtualFile[] files) {
    boolean isImagesFound = false;
    if (files != null) {
      ImageFileTypeManager typeManager = ImageFileTypeManager.getInstance();
      for (VirtualFile file : files) {
        boolean isImage = typeManager.isImage(file);
        isImagesFound |= isImage;
        if (!file.isInLocalFileSystem() || !isImage) {
          return false;
        }
      }
    }
    return isImagesFound;
  }
}
