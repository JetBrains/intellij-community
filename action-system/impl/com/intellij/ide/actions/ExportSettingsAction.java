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
import com.intellij.openapi.components.ExportableBean;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.ZipUtil;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.jar.JarOutputStream;

public class ExportSettingsAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.ExportSettingsAction");

  public void actionPerformed(AnActionEvent e) {
    List<ExportableApplicationComponent> exportableComponents = new ArrayList<ExportableApplicationComponent>();
    Map<File,Set<ExportableApplicationComponent>> fileToComponents = getRegisteredComponentsAndFiles(exportableComponents);

    final ChooseComponentsToExportDialog dialog = new ChooseComponentsToExportDialog(exportableComponents, fileToComponents, true,
                                                                                     IdeBundle.message("title.select.components.to.export"),
                                                                                     IdeBundle.message(
                                                                                       "prompt.please.check.all.components.to.export"));
    dialog.show();
    if (!dialog.isOK()) return;
    Set<ExportableApplicationComponent> markedComponents = dialog.getExportableComponents();
    if (markedComponents.size() == 0) return;
    Set<File> exportFiles = new HashSet<File>();
    for (final ExportableApplicationComponent markedComponent : markedComponents) {
      final File[] files = markedComponent.getExportFiles();
      exportFiles.addAll(Arrays.asList(files));
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
        magicFile.createNewFile();
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

  public static Map<File, Set<ExportableApplicationComponent>> getRegisteredComponentsAndFiles(List<ExportableApplicationComponent> exportableComponents) {
    Map<File,Set<ExportableApplicationComponent>> fileToComponents = new HashMap<File, Set<ExportableApplicationComponent>>();

    final List<ExportableApplicationComponent> components = new ArrayList<ExportableApplicationComponent>(Arrays.asList(ApplicationManager.getApplication().getComponents(ExportableApplicationComponent.class)));

    final ExportableBean[] exportableBeans = Extensions.getExtensions(ExportableApplicationComponent.EXTENSION_POINT);
    for (ExportableBean exportableBean : exportableBeans) {
      final String serviceClass = exportableBean.serviceInterface;
      if (serviceClass == null) {
        LOG.error("Service interface not specified in " + ExportableApplicationComponent.EXTENSION_POINT);
        continue;
      }
      try {
        final Class<?> aClass = Class.forName(serviceClass);
        final Object service = ServiceManager.getService(aClass);
        if (service == null) {
          LOG.error("Can't find service: " + serviceClass);
          continue;
        }
        if (!(service instanceof ExportableApplicationComponent)) {
          LOG.error("Service " + serviceClass + " is registered in exportable EP, but doesn't implement ExportableApplicationComponent");
          continue;
        }

        components.add((ExportableApplicationComponent)service);
      }
      catch (ClassNotFoundException e) {
        LOG.error(e);
      }
    }


    for (ExportableApplicationComponent component : components) {
      exportableComponents.add(component);
      final File[] exportFiles = component.getExportFiles();
      for (File exportFile : exportFiles) {
        Set<ExportableApplicationComponent> componentsTied = fileToComponents.get(exportFile);
        if (componentsTied == null) {
          componentsTied = new HashSet<ExportableApplicationComponent>();
          fileToComponents.put(exportFile, componentsTied);
        }
        componentsTied.add(component);
      }
    }
    return fileToComponents;
  }
}
