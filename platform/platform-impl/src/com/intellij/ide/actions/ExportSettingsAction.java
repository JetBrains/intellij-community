/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/**
 * @author cdr
 */
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.components.ExportableComponent;
import com.intellij.openapi.components.ServiceBean;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.ZipUtil;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.jar.JarOutputStream;

public class ExportSettingsAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.ExportSettingsAction");

  public void actionPerformed(AnActionEvent e) {
    List<ExportableComponent> exportableComponents = new ArrayList<ExportableComponent>();
    Map<File,Set<ExportableComponent>> fileToComponents = getRegisteredComponentsAndFiles(exportableComponents);

    final ChooseComponentsToExportDialog dialog = new ChooseComponentsToExportDialog(exportableComponents, fileToComponents, true,
                                                                                     IdeBundle.message("title.select.components.to.export"),
                                                                                     IdeBundle.message(
                                                                                       "prompt.please.check.all.components.to.export"));
    dialog.show();
    if (!dialog.isOK()) return;
    Set<ExportableComponent> markedComponents = dialog.getExportableComponents();
    if (markedComponents.size() == 0) return;
    Set<File> exportFiles = new HashSet<File>();
    for (final ExportableComponent markedComponent : markedComponents) {
      final File[] files = markedComponent.getExportFiles();
      ContainerUtil.addAll(exportFiles, files);
    }

    ApplicationManager.getApplication().saveSettings();

    try {
      final File saveFile = dialog.getExportFile();
      if (saveFile.exists()) {
        final int ret = Messages.showOkCancelDialog(
          IdeBundle.message("prompt.overwrite.settings.file", FileUtil.toSystemDependentName(saveFile.getPath())),
          IdeBundle.message("title.file.already.exists"), Messages.getWarningIcon());
        if (ret != 0) return;
      }
      final JarOutputStream output = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(saveFile)));
      try {
        final File configPath = new File(PathManager.getConfigPath());
        final HashSet<String> writtenItemRelativePaths = new HashSet<String>();
        for (File file : exportFiles) {
          final String rPath = FileUtil.getRelativePath(configPath, file);
          assert rPath != null;
          final String relativePath = FileUtil.toSystemIndependentName(rPath);
          if (file.exists()) {
            ZipUtil.addFileOrDirRecursively(output, saveFile, file, relativePath, null, writtenItemRelativePaths);
          }
        }
        final File magicFile = new File(FileUtil.getTempDirectory(), ImportSettingsFilenameFilter.SETTINGS_JAR_MARKER);
        FileUtil.createIfDoesntExist(magicFile);
        magicFile.deleteOnExit();
        ZipUtil.addFileToZip(output, magicFile, ImportSettingsFilenameFilter.SETTINGS_JAR_MARKER, writtenItemRelativePaths, null);
      }
      finally {
        output.close();
      }
      Messages.showMessageDialog(IdeBundle.message("message.settings.exported.successfully"),
                                 IdeBundle.message("title.export.successful"), Messages.getInformationIcon());
    }
    catch (IOException e1) {
      Messages.showErrorDialog(IdeBundle.message("error.writing.settings", e1.toString()),IdeBundle.message("title.error.writing.file"));
    }
  }

  public static Map<File, Set<ExportableComponent>> getRegisteredComponentsAndFiles(List<ExportableComponent> exportableComponents) {
    Map<File,Set<ExportableComponent>> fileToComponents = new HashMap<File, Set<ExportableComponent>>();

    final List<ExportableComponent> components = new ArrayList<ExportableComponent>(Arrays.asList(ApplicationManager.getApplication().getComponents(ExportableApplicationComponent.class)));

    components.addAll(ServiceBean.loadServicesFromBeans(ExportableComponent.EXTENSION_POINT, ExportableComponent.class));

    for (ExportableComponent component : components) {
      exportableComponents.add(component);
      final File[] exportFiles = component.getExportFiles();
      for (File exportFile : exportFiles) {
        Set<ExportableComponent> componentsTied = fileToComponents.get(exportFile);
        if (componentsTied == null) {
          componentsTied = new HashSet<ExportableComponent>();
          fileToComponents.put(exportFile, componentsTied);
        }
        componentsTied.add(component);
      }
    }
    return fileToComponents;
  }

}
