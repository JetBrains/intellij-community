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
import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableComponent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.ZipUtil;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public class ImportSettingsAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Component component = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext);
    final String path = ChooseComponentsToExportDialog.chooseSettingsFile(PathManager.getConfigPath(), component,
                                                                IdeBundle.message("title.import.file.location"),
                                                                IdeBundle.message("prompt.choose.import.file.path"));
    if (path == null) return;

    final File saveFile = new File(path);
    try {
      if (!saveFile.exists()) {
        Messages.showErrorDialog(IdeBundle.message("error.cannot.find.file", presentableFileName(saveFile)),
                                 IdeBundle.message("title.file.not.found"));
        return;
      }
      final ZipFile zipFile = new ZipFile(saveFile);

      final ZipEntry magicEntry = zipFile.getEntry(ImportSettingsFilenameFilter.SETTINGS_JAR_MARKER);
      if (magicEntry == null) {
        Messages.showErrorDialog(
          IdeBundle.message("error.file.contains.no.settings.to.import", presentableFileName(saveFile), promptLocationMessage()),
          IdeBundle.message("title.invalid.file"));
        return;
      }

      final ArrayList<ExportableComponent> registeredComponents = new ArrayList<ExportableComponent>();
      final Map<File, Set<ExportableComponent>> filesToComponents = ExportSettingsAction.getRegisteredComponentsAndFiles(registeredComponents);
      List<ExportableComponent> components = getComponentsStored(saveFile, registeredComponents);
      final ChooseComponentsToExportDialog dialog = new ChooseComponentsToExportDialog(components, filesToComponents, false,
                                                                       IdeBundle.message("title.select.components.to.import"),
                                                                       IdeBundle.message("prompt.check.components.to.import"));
      dialog.show();
      if (!dialog.isOK()) return;
      final Set<ExportableComponent> chosenComponents = dialog.getExportableComponents();
      Set<String> relativeNamesToExtract = new HashSet<String>();
      for (final ExportableComponent chosenComponent : chosenComponents) {
        final File[] exportFiles = chosenComponent.getExportFiles();
        for (File exportFile : exportFiles) {
          final File configPath = new File(PathManager.getConfigPath());
          final String rPath = FileUtil.getRelativePath(configPath, exportFile);
          assert rPath != null;
          final String relativePath = FileUtil.toSystemIndependentName(rPath);
          relativeNamesToExtract.add(relativePath);
        }
      }

      final File tempFile = new File(PathManager.getPluginTempPath() + "/" + saveFile.getName());
      FileUtil.copy(saveFile, tempFile);
      File outDir = new File(PathManager.getConfigPath());
      final ImportSettingsFilenameFilter filenameFilter = new ImportSettingsFilenameFilter(relativeNamesToExtract);
      StartupActionScriptManager.ActionCommand unzip = new StartupActionScriptManager.UnzipCommand(tempFile, outDir, filenameFilter);
      StartupActionScriptManager.addActionCommand(unzip);
      // remove temp file
      StartupActionScriptManager.ActionCommand deleteTemp = new StartupActionScriptManager.DeleteCommand(tempFile);
      StartupActionScriptManager.addActionCommand(deleteTemp);

      String key = ApplicationManager.getApplication().isRestartCapable()
                   ? "message.settings.imported.successfully.restart"
                   : "message.settings.imported.successfully";
      final int ret = Messages.showOkCancelDialog(IdeBundle.message(key,
                                                                    ApplicationNamesInfo.getInstance().getProductName(),
                                                                    ApplicationNamesInfo.getInstance().getFullProductName()),
                                                  IdeBundle.message("title.restart.needed"), Messages.getQuestionIcon());
      if (ret == 0) {
        if (ApplicationManager.getApplication().isRestartCapable()) {
          ApplicationManager.getApplication().restart();
        }
        else {
          ApplicationManager.getApplication().exit();
        }
      }
    }
    catch (ZipException e1) {
      Messages.showErrorDialog(
        IdeBundle.message("error.reading.settings.file", presentableFileName(saveFile), e1.getMessage(), promptLocationMessage()),
                               IdeBundle.message("title.invalid.file"));
    }
    catch (IOException e1) {
      Messages.showErrorDialog(IdeBundle.message("error.reading.settings.file.2", presentableFileName(saveFile), e1.getMessage()),
                               IdeBundle.message("title.error.reading.file"));
    }
  }

  private static String presentableFileName(final File file) {
    return "'" + FileUtil.toSystemDependentName(file.getPath()) + "'";
  }

  private static String promptLocationMessage() {
    return IdeBundle.message("message.please.ensure.correct.settings");
  }

  private static List<ExportableComponent> getComponentsStored(File zipFile,
                                                                   ArrayList<ExportableComponent> registeredComponents)
    throws IOException {
    final File configPath = new File(PathManager.getConfigPath());

    final ArrayList<ExportableComponent> components = new ArrayList<ExportableComponent>();
    for (ExportableComponent component : registeredComponents) {
      final File[] exportFiles = component.getExportFiles();
      for (File exportFile : exportFiles) {
        final String rPath = FileUtil.getRelativePath(configPath, exportFile);
        assert rPath != null;
        String relativePath = FileUtil.toSystemIndependentName(rPath);
        if (exportFile.isDirectory()) relativePath += "/";
        if (ZipUtil.isZipContainsEntry(zipFile, relativePath)) {
          components.add(component);
          break;
        }
      }
    }
    return components;
  }

}
