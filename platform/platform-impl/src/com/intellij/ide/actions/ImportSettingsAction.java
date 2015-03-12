/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.components.ExportableComponent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.Consumer;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public class ImportSettingsAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Component component = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext);
    ChooseComponentsToExportDialog.chooseSettingsFile(PathManager.getConfigPath(), component, IdeBundle.message("title.import.file.location"), IdeBundle.message("prompt.choose.import.file.path")).doWhenDone(new Consumer<String>() {
      @Override
      public void consume(String path) {
        File saveFile = new File(path);
        try {
          doImport(saveFile);
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
    });
  }

  private static void doImport(@NotNull File saveFile) throws IOException {
    if (!saveFile.exists()) {
      Messages.showErrorDialog(IdeBundle.message("error.cannot.find.file", presentableFileName(saveFile)),
                               IdeBundle.message("title.file.not.found"));
      return;
    }

    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    final ZipEntry magicEntry = new ZipFile(saveFile).getEntry(ImportSettingsFilenameFilter.SETTINGS_JAR_MARKER);
    if (magicEntry == null) {
      Messages.showErrorDialog(
        IdeBundle.message("error.file.contains.no.settings.to.import", presentableFileName(saveFile), promptLocationMessage()),
        IdeBundle.message("title.invalid.file"));
      return;
    }

    MultiMap<File, ExportableComponent> fileToComponents = ExportSettingsAction.getExportableComponentsMap(false, true);
    List<ExportableComponent> components = getComponentsStored(saveFile, fileToComponents.values());
    fileToComponents.values().retainAll(components);
    final ChooseComponentsToExportDialog dialog = new ChooseComponentsToExportDialog(fileToComponents, false,
                                                                                     IdeBundle.message("title.select.components.to.import"),
                                                                                     IdeBundle.message("prompt.check.components.to.import"));
    if (!dialog.showAndGet()) {
      return;
    }

    final Set<ExportableComponent> chosenComponents = dialog.getExportableComponents();
    Set<String> relativeNamesToExtract = new THashSet<String>();
    for (ExportableComponent chosenComponent : chosenComponents) {
      for (File exportFile : chosenComponent.getExportFiles()) {
        String rPath = FileUtilRt.getRelativePath(new File(PathManager.getConfigPath()), exportFile);
        assert rPath != null;
        relativeNamesToExtract.add(FileUtil.toSystemIndependentName(rPath));
      }
    }

    relativeNamesToExtract.add(PluginManager.INSTALLED_TXT);

    final File tempFile = new File(PathManager.getPluginTempPath() + "/" + saveFile.getName());
    FileUtil.copy(saveFile, tempFile);
    File outDir = new File(PathManager.getConfigPath());
    final ImportSettingsFilenameFilter filenameFilter = new ImportSettingsFilenameFilter(relativeNamesToExtract);
    StartupActionScriptManager.addActionCommand(new StartupActionScriptManager.UnzipCommand(tempFile, outDir, filenameFilter));
    // remove temp file
    StartupActionScriptManager.addActionCommand(new StartupActionScriptManager.DeleteCommand(tempFile));

    UpdateSettings.getInstance().forceCheckForUpdateAfterRestart();

    String key = ApplicationManager.getApplication().isRestartCapable()
                 ? "message.settings.imported.successfully.restart"
                 : "message.settings.imported.successfully";
    if (Messages.showOkCancelDialog(IdeBundle.message(key,
                                                      ApplicationNamesInfo.getInstance().getProductName(),
                                                      ApplicationNamesInfo.getInstance().getFullProductName()),
                                    IdeBundle.message("title.restart.needed"), Messages.getQuestionIcon()) == Messages.OK) {
      ((ApplicationEx)ApplicationManager.getApplication()).restart(true);
    }
  }

  private static String presentableFileName(@NotNull File file) {
    return "'" + FileUtil.toSystemDependentName(file.getPath()) + "'";
  }

  private static String promptLocationMessage() {
    return IdeBundle.message("message.please.ensure.correct.settings");
  }

  @NotNull
  private static List<ExportableComponent> getComponentsStored(@NotNull File settings,
                                                               @NotNull Collection<? extends ExportableComponent> registeredComponents) throws IOException {
    THashSet<String> zipEntries = new THashSet<String>();
    ZipFile zip = new ZipFile(settings);
    try {
      Enumeration enumeration = zip.entries();
      while (enumeration.hasMoreElements()) {
        ZipEntry zipEntry = (ZipEntry)enumeration.nextElement();
        zipEntries.add(zipEntry.getName());
      }
    }
    finally {
      zip.close();
    }

    File configPath = new File(PathManager.getConfigPath());
    List<ExportableComponent> components = new ArrayList<ExportableComponent>();

    for (ExportableComponent component : registeredComponents) {
      for (File exportFile : component.getExportFiles()) {
        String relativePath = FileUtilRt.getRelativePath(configPath, exportFile);
        assert relativePath != null;
        relativePath = FileUtilRt.toSystemIndependentName(relativePath);
        if (exportFile.getName().indexOf('.') == -1 && !exportFile.isFile()) {
          relativePath += '/';
        }
        if (zipEntries.contains(relativePath)) {
          components.add(component);
          break;
        }
      }
    }
    return components;
  }
}
