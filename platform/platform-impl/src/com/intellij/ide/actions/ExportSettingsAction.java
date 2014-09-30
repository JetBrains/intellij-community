/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.AbstractBundle;
import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.components.*;
import com.intellij.openapi.components.impl.ServiceManagerImpl;
import com.intellij.openapi.components.impl.stores.StateStorageManager;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.ZipUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.jar.JarOutputStream;

public class ExportSettingsAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@Nullable AnActionEvent e) {
    ApplicationManager.getApplication().saveSettings();

    MultiMap<File, ExportableComponent> fileToComponents = getExportableComponentsMap(true);
    ChooseComponentsToExportDialog dialog = new ChooseComponentsToExportDialog(fileToComponents, true,
                                                                               IdeBundle.message("title.select.components.to.export"),
                                                                               IdeBundle.message(
                                                                                 "prompt.please.check.all.components.to.export"));
    if (!dialog.showAndGet()) {
      return;
    }

    Set<ExportableComponent> markedComponents = dialog.getExportableComponents();
    if (markedComponents.isEmpty()) {
      return;
    }

    Set<File> exportFiles = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
    for (final ExportableComponent markedComponent : markedComponents) {
      ContainerUtil.addAll(exportFiles, markedComponent.getExportFiles());
    }

    final File saveFile = dialog.getExportFile();
    try {
      if (saveFile.exists()) {
        final int ret = Messages.showOkCancelDialog(
          IdeBundle.message("prompt.overwrite.settings.file", FileUtil.toSystemDependentName(saveFile.getPath())),
          IdeBundle.message("title.file.already.exists"), Messages.getWarningIcon());
        if (ret != Messages.OK) return;
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

        exportInstalledPlugins(saveFile, output, writtenItemRelativePaths);

        final File magicFile = new File(FileUtil.getTempDirectory(), ImportSettingsFilenameFilter.SETTINGS_JAR_MARKER);
        FileUtil.createIfDoesntExist(magicFile);
        magicFile.deleteOnExit();
        ZipUtil.addFileToZip(output, magicFile, ImportSettingsFilenameFilter.SETTINGS_JAR_MARKER, writtenItemRelativePaths, null);
      }
      finally {
        output.close();
      }
      ShowFilePathAction.showDialog(getEventProject(e), IdeBundle.message("message.settings.exported.successfully"),
                                    IdeBundle.message("title.export.successful"), saveFile, null);
    }
    catch (IOException e1) {
      Messages.showErrorDialog(IdeBundle.message("error.writing.settings", e1.toString()), IdeBundle.message("title.error.writing.file"));
    }
  }

  private static void exportInstalledPlugins(File saveFile, JarOutputStream output, HashSet<String> writtenItemRelativePaths) throws IOException {
    final List<String> oldPlugins = new ArrayList<String>();
    for (IdeaPluginDescriptor descriptor : PluginManagerCore.getPlugins()) {
      if (!descriptor.isBundled() && descriptor.isEnabled()) {
        oldPlugins.add(descriptor.getPluginId().getIdString());
      }
    }
    if (!oldPlugins.isEmpty()) {
      final File tempFile = File.createTempFile("installed", "plugins");
      tempFile.deleteOnExit();
      PluginManagerCore.savePluginsList(oldPlugins, false, tempFile);
      ZipUtil.addDirToZipRecursively(output, saveFile, tempFile, "/" + PluginManager.INSTALLED_TXT, null, writtenItemRelativePaths);
    }
  }

  @NotNull
  public static MultiMap<File, ExportableComponent> getExportableComponentsMap(final boolean onlyExisting) {
    ExportableApplicationComponent[] components1 = ApplicationManager.getApplication().getComponents(ExportableApplicationComponent.class);
    List<ExportableComponent> components2 = ServiceBean.loadServicesFromBeans(ExportableComponent.EXTENSION_POINT, ExportableComponent.class);
    final MultiMap<File, ExportableComponent> result = MultiMap.createSet();
    for (ExportableComponent component : ContainerUtil.concat(Arrays.asList(components1), components2)) {
      for (File exportFile : component.getExportFiles()) {
        result.putValue(exportFile, component);
      }
    }

    if (onlyExisting) {
      for (Iterator<File> it = result.keySet().iterator(); it.hasNext(); ) {
        if (!it.next().exists()) {
          it.remove();
        }
      }
    }

    ApplicationImpl application = (ApplicationImpl)ApplicationManager.getApplication();
    final StateStorageManager storageManager = application.getStateStore().getStateStorageManager();
    ServiceManagerImpl.processAllImplementationClasses(application, new PairProcessor<Class<?>, PluginDescriptor>() {
      @Override
      public boolean process(@NotNull Class<?> aClass, @Nullable PluginDescriptor pluginDescriptor) {
        State stateAnnotation = aClass.getAnnotation(State.class);
        if (stateAnnotation != null && !StringUtil.isEmpty(stateAnnotation.name())) {
          if (ExportableComponent.class.isAssignableFrom(aClass)) {
            return true;
          }

          int storageIndex;
          Storage[] storages = stateAnnotation.storages();
          if (storages.length == 1) {
            storageIndex = 0;
          }
          else if (storages.length > 1 && stateAnnotation.storageChooser() == LastStorageChooserForWrite.class) {
            storageIndex = storages.length - 1;
          }
          else {
            return true;
          }

          Storage storage = storages[storageIndex];
          if (storage.roamingType() != RoamingType.DISABLED &&
              storage.storageClass().equals(StateStorage.class) &&
              storage.scheme() == StorageScheme.DEFAULT &&
              !StringUtil.isEmpty(storage.file()) &&
              storage.file().startsWith(StoragePathMacros.APP_CONFIG)) {
            File file = new File(storageManager.expandMacros(storage.file()));
            if (!onlyExisting || file.exists()) {
              result.putValue(file, new MyExportableComponent(file, getExportableComponentPresentableName(stateAnnotation.name(), aClass, pluginDescriptor)));
            }
          }
        }
        return true;
      }
    });
    return result;
  }

  @NotNull
  private static String getExportableComponentPresentableName(@NotNull String defaultName, @NotNull Class<?> aClass, @Nullable PluginDescriptor pluginDescriptor) {
    String resourceBundleName;
    if (pluginDescriptor != null && pluginDescriptor instanceof IdeaPluginDescriptor && !"com.intellij".equals(pluginDescriptor.getPluginId().getIdString())) {
      resourceBundleName = ((IdeaPluginDescriptor)pluginDescriptor).getResourceBundleBaseName();
    }
    else {
      resourceBundleName = OptionsBundle.PATH_TO_BUNDLE;
    }

    if (resourceBundleName == null) {
      return defaultName;
    }

    ClassLoader classLoader = pluginDescriptor == null ? null : pluginDescriptor.getPluginClassLoader();
    classLoader = classLoader == null ? aClass.getClassLoader() : classLoader;
    if (classLoader != null) {
      ResourceBundle bundle = AbstractBundle.getResourceBundle(resourceBundleName, classLoader);
      if (bundle != null) {
        return CommonBundle.messageOrDefault(bundle, "exportable." + defaultName + ".presentable.name", defaultName);
      }
    }
    return defaultName;
  }

  private static final class MyExportableComponent implements ExportableComponent {
    private final File file;
    private final String name;

    public MyExportableComponent(@NotNull File file, @NotNull String name) {
      this.file = file;
      this.name = name;
    }

    @NotNull
    @Override
    public File[] getExportFiles() {
      return new File[]{file};
    }

    @NotNull
    @Override
    public String getPresentableName() {
      return name;
    }
  }
}

