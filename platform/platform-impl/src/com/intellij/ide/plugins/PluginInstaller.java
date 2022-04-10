// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.CommonBundle;
import com.intellij.core.CoreBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.marketplace.MarketplacePluginDownloadService;
import com.intellij.ide.plugins.marketplace.PluginSignatureChecker;
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector;
import com.intellij.ide.plugins.marketplace.statistics.enums.InstallationSourceEnum;
import com.intellij.ide.plugins.org.PluginManagerFilters;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.Decompressor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.intellij.ide.startup.StartupActionScriptManager.*;

/**
 * @author stathik
 */
public final class PluginInstaller {
  private static final Logger LOG = Logger.getInstance(PluginInstaller.class);

  public static final String UNKNOWN_HOST_MARKER = "__unknown_repository__";

  static final Object ourLock = new Object();
  private static final String PLUGINS_PRESELECTION_PATH = "plugins.preselection.path";

  private PluginInstaller() { }

  /**
   * @return true if restart is needed
   */
  @ApiStatus.Internal
  public static boolean prepareToUninstall(@NotNull IdeaPluginDescriptorImpl pluginDescriptor) throws IOException {
    synchronized (ourLock) {
      if (PluginManagerCore.isPluginInstalled(pluginDescriptor.getPluginId())) {
        if (pluginDescriptor.isBundled()) {
          LOG.error("Plugin is bundled: " + pluginDescriptor.getPluginId());
        }
        else {
          boolean needRestart = pluginDescriptor.isEnabled() && !DynamicPlugins.allowLoadUnloadWithoutRestart(pluginDescriptor);
          if (needRestart) {
            uninstallAfterRestart(pluginDescriptor.getPluginPath());
          }

          PluginStateManager.fireState(pluginDescriptor, false);
          return needRestart;
        }
      }
    }
    return false;
  }

  @ApiStatus.Internal
  public static void uninstallAfterRestart(@NotNull Path pluginPath) throws IOException {
    addActionCommand(new DeleteCommand(pluginPath));
  }

  public static boolean uninstallDynamicPlugin(@Nullable JComponent parentComponent,
                                               @NotNull IdeaPluginDescriptorImpl pluginDescriptor,
                                               boolean isUpdate) {
    boolean uninstalledWithoutRestart = true;
    if (pluginDescriptor.isEnabled()) {
      DynamicPlugins.UnloadPluginOptions options = new DynamicPlugins.UnloadPluginOptions()
        .withDisable(false)
        .withUpdate(isUpdate)
        .withWaitForClassloaderUnload(true);

      uninstalledWithoutRestart = parentComponent != null ?
                                  DynamicPlugins.INSTANCE.unloadPluginWithProgress(null, parentComponent, pluginDescriptor, options) :
                                  DynamicPlugins.INSTANCE.unloadPlugin(pluginDescriptor, options);
    }

    Path pluginPath = pluginDescriptor.getPluginPath();
    if (uninstalledWithoutRestart) {
      try {
        FileUtil.delete(pluginPath);
      }
      catch (IOException e) {
        LOG.info("Failed to delete jar of dynamic plugin", e);
        uninstalledWithoutRestart = false;
      }
    }

    if (!uninstalledWithoutRestart) {
      try {
        uninstallAfterRestart(pluginPath);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    return uninstalledWithoutRestart;
  }

  public static void installAfterRestart(@NotNull Path sourceFile,
                                         boolean deleteSourceFile,
                                         @Nullable Path existingPlugin,
                                         @NotNull IdeaPluginDescriptor descriptor) throws IOException {
    List<ActionCommand> commands = new ArrayList<>();

    if (existingPlugin != null) {
      commands.add(new DeleteCommand(existingPlugin));
    }

    Path pluginsPath = getPluginsPath();
    if (sourceFile.getFileName().toString().endsWith(".jar")) {
      commands.add(new CopyCommand(sourceFile, pluginsPath.resolve(sourceFile.getFileName())));
    }
    else {
      // drops stale directory
      commands.add(new DeleteCommand(pluginsPath.resolve(rootEntryName(sourceFile))));
      commands.add(new UnzipCommand(sourceFile, pluginsPath));
    }

    if (deleteSourceFile) {
      commands.add(new DeleteCommand(sourceFile));
    }

    addActionCommands(commands);

    PluginStateManager.fireState(descriptor, true);
  }

  private static @Nullable Path installWithoutRestart(@NotNull Path sourceFile,
                                                      @NotNull IdeaPluginDescriptorImpl descriptor,
                                                      @Nullable JComponent parent) {
    Path result;
    try {
      Task.WithResult<Path, IOException> task = new Task.WithResult<>(null,
                                                                      parent,
                                                                      IdeBundle
                                                                        .message("progress.title.installing.plugin", descriptor.getName()),
                                                                      false) {
        @Override
        protected Path compute(@NotNull ProgressIndicator indicator) throws IOException {
          return unpackPlugin(sourceFile, getPluginsPath());
        }
      };
      result = ProgressManager.getInstance().run(task);
    }
    catch (Throwable throwable) {
      LOG.warn("Plugin " + descriptor + " failed to install without restart. " + throwable.getMessage(), throwable);
      result = null;
    }
    PluginStateManager.fireState(descriptor, true);
    return result;
  }

  public static @NotNull Path unpackPlugin(@NotNull Path sourceFile, @NotNull Path targetPath) throws IOException {
    Path target;
    if (sourceFile.getFileName().toString().endsWith(".jar")) {
      target = targetPath.resolve(sourceFile.getFileName());
      FileUtilRt.copy(sourceFile.toFile(), target.toFile());
    }
    else {
      target = targetPath.resolve(rootEntryName(sourceFile));
      FileUtilRt.delete(target.toFile());
      new Decompressor.Zip(sourceFile).extract(targetPath);
    }
    return target;
  }

  public static String rootEntryName(@NotNull Path zip) throws IOException {
    try (ZipFile zipFile = new ZipFile(zip.toFile())) {
      Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry zipEntry = entries.nextElement();
        // we do not necessarily get a separate entry for the subdirectory when the file
        // in the ZIP archive is placed in a subdirectory, so we need to check if the slash
        // is found anywhere in the path
        String name = zipEntry.getName();
        int i = name.indexOf('/');
        if (i > 0) return name.substring(0, i);
      }
    }

    throw new IOException("Corrupted archive (no file entries): " + zip);
  }

  public static void addStateListener(@NotNull PluginStateListener listener) {
    PluginStateManager.addStateListener(listener);
  }

  static boolean installFromDisk(@NotNull File file,
                                 @Nullable Project project,
                                 @Nullable JComponent parent) {
    return installFromDisk(new InstalledPluginsTableModel(project),
                           PluginEnabler.HEADLESS,
                           file,
                           parent,
                           PluginInstaller::installPluginFromCallbackData);
  }

  static boolean installFromDisk(@NotNull InstalledPluginsTableModel model,
                                 @NotNull PluginEnabler pluginEnabler,
                                 @NotNull File file,
                                 @Nullable JComponent parent,
                                 @NotNull Consumer<? super PluginInstallCallbackData> callback) {
    try {
      Path path = file.toPath();
      IdeaPluginDescriptorImpl pluginDescriptor = PluginDescriptorLoader.loadDescriptorFromArtifact(path, null);
      if (pluginDescriptor == null) {
        MessagesEx.showErrorDialog(parent,
                                   IdeBundle.message("dialog.message.fail.to.load.plugin.descriptor.from.file", file.getName()),
                                   CommonBundle.getErrorTitle());
        return false;
      }

      if (!PluginManagerMain.checkThirdPartyPluginsAllowed(List.of(pluginDescriptor))) {
        return false;
      }

      if (!PluginManagerFilters.getInstance().allowInstallingPlugin(pluginDescriptor)) {
        String message = IdeBundle.message("dialog.message.plugin.is.not.allowed", pluginDescriptor.getName());
        MessagesEx.showWarningDialog(parent, message, IdeBundle.message("dialog.title.install.plugin"));
        return false;
      }

      InstalledPluginsState ourState = InstalledPluginsState.getInstance();
      if (ourState.wasInstalled(pluginDescriptor.getPluginId())) {
        String message = IdeBundle.message("dialog.message.plugin.was.already.installed", pluginDescriptor.getName());
        MessagesEx.showWarningDialog(parent, message, IdeBundle.message("dialog.title.install.plugin"));
        return false;
      }

      PluginLoadingError error = PluginManagerCore.checkBuildNumberCompatibility(pluginDescriptor, PluginManagerCore.getBuildNumber());
      if (error != null) {
        MessagesEx.showErrorDialog(parent, error.getDetailedMessage(), CommonBundle.getErrorTitle());
        return false;
      }
      if (PluginManagerCore.isBrokenPlugin(pluginDescriptor)) {
        String message =
          CoreBundle.message("plugin.loading.error.long.marked.as.broken", pluginDescriptor.getName(), pluginDescriptor.getVersion());
        MessagesEx.showErrorDialog(parent, message, CommonBundle.getErrorTitle());
        return false;
      }

      IdeaPluginDescriptor installedPlugin = PluginManagerCore.getPlugin(pluginDescriptor.getPluginId());
      if (installedPlugin != null && ApplicationInfoEx.getInstanceEx().isEssentialPlugin(installedPlugin.getPluginId())) {
        String message = IdeBundle
          .message("dialog.message.plugin.core.part", pluginDescriptor.getName(), ApplicationNamesInfo.getInstance().getFullProductName());
        MessagesEx.showErrorDialog(parent, message, CommonBundle.getErrorTitle());
        return false;
      }

      PluginManagerUsageCollector.pluginInstallationStarted(pluginDescriptor,
                                                            InstallationSourceEnum.FROM_DISK,
                                                            installedPlugin != null ? installedPlugin.getVersion() : null);

      if (!PluginSignatureChecker.verifyIfRequired(pluginDescriptor, file, false, true)) {
        return false;
      }

      Task.WithResult<Pair<PluginInstallOperation, ? extends IdeaPluginDescriptor>, RuntimeException> task =
        new Task.WithResult<>(null,
                              parent,
                              IdeBundle.message("progress.title.checking.plugin.dependencies"),
                              true) {
          @Override
          protected @NotNull Pair<PluginInstallOperation, ? extends IdeaPluginDescriptor> compute(@NotNull ProgressIndicator indicator) {
            PluginInstallOperation operation = new PluginInstallOperation(List.of(),
                                                                          CustomPluginRepositoryService.getInstance()
                                                                            .getCustomRepositoryPlugins(),
                                                                          pluginEnabler,
                                                                          indicator);
            operation.setAllowInstallWithoutRestart(true);

            return operation.checkMissingDependencies(pluginDescriptor, null) ?
                   Pair.create(operation, operation.checkDependenciesAndReplacements(pluginDescriptor)) :
                   Pair.empty();
          }
        };

      Pair<PluginInstallOperation, ? extends IdeaPluginDescriptor> pair = ProgressManager.getInstance().run(task);
      PluginInstallOperation operation = pair.getFirst();
      if (operation == null) {
        return false;
      }

      Path oldFile = installedPlugin != null && !installedPlugin.isBundled() ?
                     installedPlugin.getPluginPath() :
                     null;

      boolean isRestartRequired = oldFile != null ||
                                  !DynamicPlugins.allowLoadUnloadWithoutRestart(pluginDescriptor) ||
                                  operation.isRestartRequired();
      if (isRestartRequired) {
        installAfterRestart(path, false, oldFile, pluginDescriptor);
      }
      ourState.onPluginInstall(pluginDescriptor, installedPlugin != null, isRestartRequired);

      IdeaPluginDescriptor toDisable = pair.getSecond();
      if (toDisable != null) {
        // TODO[yole] unload and check for restart
        pluginEnabler.disablePlugins(Set.of(toDisable));
      }

      Set<PluginInstallCallbackData> installedDependencies = operation.getInstalledDependentPlugins();
      List<IdeaPluginDescriptor> installedPlugins = new ArrayList<>();
      installedPlugins.add(pluginDescriptor);
      for (PluginInstallCallbackData plugin : installedDependencies) {
        installedPlugins.add(plugin.getPluginDescriptor());
      }

      Set<String> notInstalled = findNotInstalledPluginDependencies(pluginDescriptor.getDependencies(),
                                                                    model,
                                                                    ContainerUtil.map2Set(installedPlugins, PluginDescriptor::getPluginId));
      if (!notInstalled.isEmpty()) {
        String message = IdeBundle.message("dialog.message.plugin.depends.on.unknown.plugin",
                                           pluginDescriptor.getName(),
                                           notInstalled.size(),
                                           StringUtil.join(notInstalled, ", "));
        MessagesEx.showWarningDialog(parent, message, IdeBundle.message("dialog.title.install.plugin"));
      }

      PluginManagerMain.suggestToEnableInstalledDependantPlugins(pluginEnabler, installedPlugins);

      callback.accept(new PluginInstallCallbackData(path, pluginDescriptor, isRestartRequired));
      for (PluginInstallCallbackData callbackData : installedDependencies) {
        if (!callbackData.getPluginDescriptor().getPluginId().equals(pluginDescriptor.getPluginId())) {
          callback.accept(callbackData);
        }
      }

      if (path.toString().endsWith(".zip") && Registry.is("ide.plugins.keep.archive")) {
        File tempFile = MarketplacePluginDownloadService.getPluginTempFile();
        FileUtil.copy(file, tempFile);
        MarketplacePluginDownloadService.renameFileToZipRoot(tempFile);
      }
      return true;
    }
    catch (IOException ex) {
      MessagesEx.showErrorDialog(parent, ex.getMessage(), CommonBundle.getErrorTitle());
      return false;
    }
  }

  public static boolean installAndLoadDynamicPlugin(@NotNull Path file,
                                                    @NotNull IdeaPluginDescriptorImpl descriptor) {
    return installAndLoadDynamicPlugin(file, null, descriptor);
  }

  /**
   * @return true if plugin was successfully installed without restart, false if restart is required
   */
  public static boolean installAndLoadDynamicPlugin(@NotNull Path file,
                                                    @Nullable JComponent parent,
                                                    @NotNull IdeaPluginDescriptorImpl descriptor) {
    Path targetFile = installWithoutRestart(file, descriptor, parent);
    if (targetFile == null) {
      return false;
    }

    IdeaPluginDescriptorImpl targetDescriptor = PluginDescriptorLoader.loadDescriptor(targetFile,
                                                                                      DisabledPluginsState.disabledPlugins(),
                                                                                      false,
                                                                                      PluginXmlPathResolver.DEFAULT_PATH_RESOLVER);
    if (targetDescriptor == null) {
      return false;
    }

    return PluginEnabler.HEADLESS.isDisabled(targetDescriptor.getPluginId()) ||
           DynamicPlugins.INSTANCE.loadPlugin(targetDescriptor);
  }

  private static @NotNull Set<String> findNotInstalledPluginDependencies(@NotNull List<? extends IdeaPluginDependency> dependencies,
                                                                         @NotNull InstalledPluginsTableModel model,
                                                                         @NotNull Set<PluginId> installedDependencies) {
    Set<String> notInstalled = new HashSet<>();
    for (IdeaPluginDependency dependency : dependencies) {
      if (dependency.isOptional()) continue;

      PluginId pluginId = dependency.getPluginId();
      if (installedDependencies.contains(pluginId) ||
          model.isLoaded(pluginId) ||
          PluginManagerCore.isModuleDependency(pluginId)) {
        continue;
      }

      notInstalled.add(pluginId.getIdString());
    }

    return notInstalled;
  }

  static void chooseAndInstall(@Nullable Project project,
                               @Nullable JComponent parent,
                               @NotNull BiConsumer<? super File, ? super JComponent> callback) {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(false, false, true, true, false, false) {

      {
        setTitle(IdeBundle.message("chooser.title.plugin.file"));
        setDescription(IdeBundle.message("chooser.description.jar.and.zip.archives.are.accepted"));
      }

      @Override
      public boolean isFileSelectable(@Nullable VirtualFile file) {
        if (file == null) {
          return false;
        }

        final String extension = file.getExtension();
        return Comparing.strEqual(extension, "jar") || Comparing.strEqual(extension, "zip");
      }
    };

    String oldPath = PropertiesComponent.getInstance().getValue(PLUGINS_PRESELECTION_PATH);
    VirtualFile toSelect = oldPath != null ?
                           VfsUtil.findFileByIoFile(new File(FileUtilRt.toSystemDependentName(oldPath)), false) :
                           null;

    FileChooser.chooseFile(descriptor, project, parent, toSelect, virtualFile -> {
      File file = VfsUtilCore.virtualToIoFile(virtualFile);
      PropertiesComponent.getInstance().setValue(PLUGINS_PRESELECTION_PATH,
                                                 FileUtilRt.toSystemIndependentName(file.getParent()));
      callback.accept(file, parent);
    });
  }

  private static @NotNull Path getPluginsPath() {
    return Paths.get(PathManager.getPluginsPath());
  }

  private static void installPluginFromCallbackData(@NotNull PluginInstallCallbackData callbackData) {
    IdeaPluginDescriptorImpl descriptor = callbackData.getPluginDescriptor();
    if (!callbackData.getRestartNeeded() &&
        installAndLoadDynamicPlugin(callbackData.getFile(), descriptor)) {
      return;
    }

    PluginManagerConfigurable.shutdownOrRestartAppAfterInstall(PluginManagerConfigurable.getUpdatesDialogTitle(),
                                                               action -> IdeBundle.message("plugin.installed.ide.restart.required.message",
                                                                                           descriptor.getName(),
                                                                                           action,
                                                                                           ApplicationNamesInfo.getInstance()
                                                                                             .getFullProductName()));
  }
}
