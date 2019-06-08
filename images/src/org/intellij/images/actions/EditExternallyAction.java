// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.intellij.images.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
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
import org.intellij.images.options.impl.ImagesConfigurable;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Map;

/**
 * Open image file externally.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public final class EditExternallyAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    Options options = OptionsManager.getInstance().getOptions();
    String executablePath = options.getExternalEditorOptions().getExecutablePath();
    if (StringUtil.isEmpty(executablePath)) {
      Messages.showErrorDialog(project,
                               ImagesBundle.message("error.empty.external.editor.path"),
                               ImagesBundle.message("error.title.empty.external.editor.path"));
      ImagesConfigurable.show(project);
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
          ImagesConfigurable.show(project);
        }
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {

    doUpdate(e);
  }

  static void doUpdate(@NotNull AnActionEvent e) {
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
