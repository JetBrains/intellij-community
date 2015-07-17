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
import com.intellij.openapi.components.impl.stores.StoreUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.PairProcessor;
import com.intellij.util.PlatformUtils;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.ZipUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ExportSettingsAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(ExportSettingsAction.class);

  @Override
  public void actionPerformed(@Nullable AnActionEvent e) {
    ApplicationManager.getApplication().saveSettings();

    ChooseComponentsToExportDialog dialog = new ChooseComponentsToExportDialog(getExportableComponentsMap(true, true), true,
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
    for (ExportableComponent markedComponent : markedComponents) {
      ContainerUtil.addAll(exportFiles, markedComponent.getExportFiles());
    }

    final File saveFile = dialog.getExportFile();
    try {
      if (saveFile.exists() && Messages.showOkCancelDialog(
        IdeBundle.message("prompt.overwrite.settings.file", FileUtil.toSystemDependentName(saveFile.getPath())),
        IdeBundle.message("title.file.already.exists"), Messages.getWarningIcon()) != Messages.OK) {
        return;
      }

      MyZipOutputStream zipOut = new MyZipOutputStream(new BufferedOutputStream(new FileOutputStream(saveFile)));
      try {
        Set<String> writtenItemRelativePaths = new THashSet<String>();
        String configRoot = PathManager.getConfigPath();
        for (File file : exportFiles) {
          if (file.exists()) {
            String rPath = FileUtilRt.getRelativePath(configRoot, file.getAbsolutePath(), File.separatorChar);
            assert rPath != null;
            ZipUtil.addFileOrDirRecursively(zipOut, null, file, FileUtilRt.toSystemIndependentName(rPath), null, writtenItemRelativePaths);
          }
        }

        exportInstalledPlugins(zipOut);

        ZipEntry zipEntry = new ZipEntry(ImportSettingsFilenameFilter.SETTINGS_JAR_MARKER);
        zipOut.putNextEntry(zipEntry);
        zipOut.closeEntry();
      }
      finally {
        zipOut.close();
      }
      ShowFilePathAction.showDialog(getEventProject(e), IdeBundle.message("message.settings.exported.successfully"),
                                    IdeBundle.message("title.export.successful"), saveFile, null);
    }
    catch (IOException e1) {
      Messages.showErrorDialog(IdeBundle.message("error.writing.settings", e1.toString()), IdeBundle.message("title.error.writing.file"));
    }
  }

  private static void exportInstalledPlugins(@NotNull MyZipOutputStream zipOut) throws IOException {
    List<String> plugins = new ArrayList<String>();
    for (IdeaPluginDescriptor descriptor : PluginManagerCore.getPlugins()) {
      if (!descriptor.isBundled() && descriptor.isEnabled()) {
        plugins.add(descriptor.getPluginId().getIdString());
      }
    }
    if (plugins.isEmpty()) {
      return;
    }

    ZipEntry e = new ZipEntry(PluginManager.INSTALLED_TXT);
    zipOut.putNextEntry(e);
    zipOut.ignoreClose = true;
    try {
      PluginManagerCore.writePluginsList(plugins, new OutputStreamWriter(zipOut, CharsetToolkit.UTF8_CHARSET));
    }
    finally {
      zipOut.ignoreClose = false;
      zipOut.closeEntry();
    }
  }

  private static class MyZipOutputStream extends ZipOutputStream {
    private boolean ignoreClose = true;

    public MyZipOutputStream(@NotNull OutputStream out) {
      super(out);
    }

    @Override
    public void close() throws IOException {
      if (!ignoreClose) {
        super.close();
      }
    }
  }

  @NotNull
  public static MultiMap<File, ExportableComponent> getExportableComponentsMap(final boolean onlyExisting, final boolean computePresentableNames) {
    @SuppressWarnings("deprecation")
    List<ExportableApplicationComponent> components1 = ComponentsPackage.getComponents(ApplicationManager.getApplication(), ExportableApplicationComponent.class);
    List<ExportableComponent> components2 = ServiceBean.loadServicesFromBeans(ExportableComponent.EXTENSION_POINT, ExportableComponent.class);
    final MultiMap<File, ExportableComponent> result = MultiMap.createLinkedSet();
    for (ExportableComponent component : ContainerUtil.concat(components1, components2)) {
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
    final StateStorageManager storageManager = ComponentsPackage.getStateStore(application).getStateStorageManager();
    ServiceManagerImpl.processAllImplementationClasses(application, new PairProcessor<Class<?>, PluginDescriptor>() {
      @Override
      public boolean process(@NotNull Class<?> aClass, @Nullable PluginDescriptor pluginDescriptor) {
        State stateAnnotation = StoreUtil.getStateSpec(aClass);
        if (stateAnnotation != null && !StringUtil.isEmpty(stateAnnotation.name())) {
          if (ExportableComponent.class.isAssignableFrom(aClass)) {
            return true;
          }

          int storageIndex;
          Storage[] storages = stateAnnotation.storages();
          if (storages.length == 1 || (storages.length > 1 && stateAnnotation.storageChooser() == StateStorageChooser.class)) {
            storageIndex = 0;
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

            File additionalExportFile = null;
            if (!StringUtil.isEmpty(stateAnnotation.additionalExportFile())) {
              String expandedPath = storageManager.expandMacros(stateAnnotation.additionalExportFile());
              additionalExportFile = new File(expandedPath);
              if (!additionalExportFile.exists()) {
                additionalExportFile = new File(storageManager.expandMacros(StoragePathMacros.ROOT_CONFIG) + '/' + expandedPath);
              }

              if (onlyExisting && !additionalExportFile.exists()) {
                additionalExportFile = null;
              }
            }

            boolean fileExists = !onlyExisting || file.exists();
            if (fileExists || additionalExportFile != null) {
              File[] files;
              if (additionalExportFile == null) {
                files = new File[]{file};
              }
              else {
                files = fileExists ? new File[]{file, additionalExportFile} : new File[]{additionalExportFile};
              }
              ExportableComponentItem item = new ExportableComponentItem(files,
                                                                         computePresentableNames
                                                                         ? getComponentPresentableName(stateAnnotation, aClass, pluginDescriptor)
                                                                         : "",
                                                                         storage.roamingType());
              result.putValue(file, item);
              if (additionalExportFile != null) {
                result.putValue(additionalExportFile, item);
              }
            }
          }
        }
        return true;
      }
    });
    return result;
  }

  @NotNull
  private static String getComponentPresentableName(@NotNull State state, @NotNull Class<?> aClass, @Nullable PluginDescriptor pluginDescriptor) {
    if (state.presentableName() != State.NameGetter.class) {
      try {
        return ReflectionUtil.newInstance(state.presentableName()).get();
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    String defaultName = state.name();
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
      String message = messageOrDefault(classLoader, resourceBundleName, defaultName);
      if (message != defaultName) {
        return message;
      }

      if (PlatformUtils.isRubyMine()) {
        // ruby plugin in RubyMine has id "com.intellij", so, we cannot set "resource-bundle" in plugin.xml
        return messageOrDefault(classLoader, "org.jetbrains.plugins.ruby.RBundle", defaultName);
      }
    }
    return defaultName;
  }

  @NotNull
  private static String messageOrDefault(@NotNull ClassLoader classLoader, @NotNull String bundleName, @NotNull String defaultName) {
    ResourceBundle bundle = AbstractBundle.getResourceBundle(bundleName, classLoader);
    return bundle == null ? defaultName : CommonBundle.messageOrDefault(bundle, "exportable." + defaultName + ".presentable.name", defaultName);
  }

  public static final class ExportableComponentItem implements ExportableComponent {
    private final File[] files;
    private final String name;
    private final RoamingType roamingType;

    public ExportableComponentItem(@NotNull File[] files, @NotNull String name, @NotNull RoamingType roamingType) {
      this.files = files;
      this.name = name;
      this.roamingType = roamingType;
    }

    @NotNull
    @Override
    public File[] getExportFiles() {
      return files;
    }

    @NotNull
    @Override
    public String getPresentableName() {
      return name;
    }

    @NotNull
    public RoamingType getRoamingType() {
      return roamingType;
    }
  }
}

