/**
 * @author cdr
 */
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.ZipUtil;

import java.awt.*;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public class ImportSettingsAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Component component = (Component)dataContext.getData(DataConstants.CONTEXT_COMPONENT);
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

      final ZipEntry magicEntry = zipFile.getEntry(ExportSettingsAction.SETTINGS_JAR_MARKER);
      if (magicEntry == null) {
        Messages.showErrorDialog(
          IdeBundle.message("error.file.contains.no.settings.to.import", presentableFileName(saveFile), promptLocationMessage()),
          IdeBundle.message("title.invalid.file"));
        return;
      }

      final ArrayList<ExportableApplicationComponent> registeredComponents = new ArrayList<ExportableApplicationComponent>();
      final Map<File, Set<ExportableApplicationComponent>> filesToComponents = ExportSettingsAction.getRegisteredComponentsAndFiles(registeredComponents);
      List<ExportableApplicationComponent> components = getComponentsStored(saveFile, registeredComponents);
      final ChooseComponentsToExportDialog dialog = new ChooseComponentsToExportDialog(components, filesToComponents, false,
                                                                       IdeBundle.message("title.select.components.to.import"),
                                                                       IdeBundle.message("prompt.check.components.to.import"));
      dialog.show();
      if (!dialog.isOK()) return;
      final Set<ExportableApplicationComponent> chosenComponents = dialog.getExportableComponents();
      Set<String> relativeNamesToExtract = new HashSet<String>();
      for (final ExportableApplicationComponent chosenComponent : chosenComponents) {
        final File[] exportFiles = chosenComponent.getExportFiles();
        for (File exportFile : exportFiles) {
          final File configPath = new File(PathManager.getConfigPath());
          final String rPath = FileUtil.getRelativePath(configPath, exportFile);
          assert rPath != null;
          final String relativePath = FileUtil.toSystemIndependentName(rPath);
          relativeNamesToExtract.add(relativePath);
        }
      }

      final File tempFile = new File(PathManagerEx.getPluginTempPath() + "/" + saveFile.getName());
      FileUtil.copy(saveFile, tempFile);
      File outDir = new File(PathManager.getConfigPath());
      final MyFilenameFilter filenameFilter = new MyFilenameFilter(relativeNamesToExtract);
      StartupActionScriptManager.ActionCommand unzip = new StartupActionScriptManager.UnzipCommand(tempFile, outDir, filenameFilter);
      StartupActionScriptManager.addActionCommand(unzip);
      // remove temp file
      StartupActionScriptManager.ActionCommand deleteTemp = new StartupActionScriptManager.DeleteCommand(tempFile);
      StartupActionScriptManager.addActionCommand(deleteTemp);

      final int ret = Messages.showOkCancelDialog(IdeBundle.message("message.settings.imported.successfully",
                                                                    ApplicationNamesInfo.getInstance().getProductName(),
                                                                    ApplicationNamesInfo.getInstance().getFullProductName()),
                                                  IdeBundle.message("title.restart.needed"), Messages.getQuestionIcon());
      if (ret == 0) {
        ApplicationManager.getApplication().exit();
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

  private static List<ExportableApplicationComponent> getComponentsStored(File zipFile,
                                                                   ArrayList<ExportableApplicationComponent> registeredComponents)
    throws IOException {
    final File configPath = new File(PathManager.getConfigPath());

    final ArrayList<ExportableApplicationComponent> components = new ArrayList<ExportableApplicationComponent>();
    for (ExportableApplicationComponent component : registeredComponents) {
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

  private static class MyFilenameFilter implements FilenameFilter, Serializable {
    private final Set<String> myRelativeNamesToExtract;
    public MyFilenameFilter(Set<String> relativeNamesToExtract) {
      myRelativeNamesToExtract = relativeNamesToExtract;
    }

    public boolean accept(File dir, String name) {
      if (name.equals(ExportSettingsAction.SETTINGS_JAR_MARKER)) return false;
      final File configPath = new File(PathManager.getConfigPath());
      final String rPath = FileUtil.getRelativePath(configPath, new File(dir, name));
      assert rPath != null;
      final String relativePath = FileUtil.toSystemIndependentName(rPath);
      for (final String allowedRelPath : myRelativeNamesToExtract) {
        if (relativePath.startsWith(allowedRelPath)) return true;
      }
      return false;
    }
  }
}