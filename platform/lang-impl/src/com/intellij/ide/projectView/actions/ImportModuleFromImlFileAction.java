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
package com.intellij.ide.projectView.actions;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ImportModuleFromImlFileAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.projectView.actions.ImportModuleFromImlFileAction");

  @Override
  public void actionPerformed(AnActionEvent e) {
    final VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    final Project project = getEventProject(e);
    if (files == null || project == null) return;

    try {
      final ModifiableModuleModel model = ModuleManager.getInstance(project).getModifiableModel();
      for (VirtualFile file : files) {
        model.loadModule(file.getPath());
      }

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
      Messages.showErrorDialog(project, "Cannot import module: " + ex.getMessage(), CommonBundle.getErrorTitle());
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final List<VirtualFile> modules = getModuleNames(e);
    final Presentation presentation = e.getPresentation();
    final boolean visible = !modules.isEmpty();
    presentation.setVisible(visible);
    presentation.setEnabled(visible);
    String text;
    if (modules.size() > 1) {
      text = "Import " + modules.size() + " Modules";
    }
    else if (modules.size() == 1) {
      text = "Import '" + modules.get(0).getNameWithoutExtension() + "' Module";
    }
    else {
      text = getTemplatePresentation().getText();
    }
    presentation.setText(text);
  }

  private static List<VirtualFile> getModuleNames(AnActionEvent e) {
    final VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    final Project project = getEventProject(e);
    if (project == null || files == null || files.length == 0) {
      return Collections.emptyList();
    }

    List<VirtualFile> modulesFiles = new ArrayList<>();
    for (VirtualFile file : files) {
      if (!file.getFileType().equals(StdFileTypes.IDEA_MODULE)) {
        return Collections.emptyList();
      }

      modulesFiles.add(file);
    }

    final ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      modulesFiles.remove(module.getModuleFile());
    }
    return modulesFiles;
  }
}
