// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.CommonBundle;
import com.intellij.core.CoreBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.marketplace.MarketplacePluginDownloadService;
import com.intellij.ide.plugins.marketplace.PluginSignatureChecker;
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
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;
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
          boolean needRestart = !DynamicPlugins.allowLoadUnloadWithoutRestart(pluginDescriptor);
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

  private static void uninstallAfterRestart(@NotNull Path pluginPath) throws IOException {
    addActionCommand(new DeleteCommand(pluginPath));
  }

  public static boolean uninstallDynamicPlugin(@Nullable JComponent parentComponent,
                                               @NotNull IdeaPluginDescriptorImpl pluginDescriptor,
                                               boolean isUpdate) {
    DynamicPlugins.UnloadPluginOptions options = new DynamicPlugins.UnloadPluginOptions()
      .withUpdate(isUpdate)
      .withWaitForClassloaderUnload(true);

    boolean uninstalledWithoutRestart = parentComponent != null ?
                                        DynamicPlugins.unloadPluginWithProgress(null, parentComponent, pluginDescriptor, options) :
                                        DynamicPlugins.unloadPlugin(pluginDescriptor, options);

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

  static boolean installFromDisk(@NotNull InstalledPluginsTableModel model,
                                 @NotNull Path file,
                                 @NotNull Consumer<? super PluginInstallCallbackData> callback,
                                 @Nullable JComponent parent) {
    try {
      IdeaPluginDescriptorImpl pluginDescriptor = PluginDescriptorLoader.loadDescriptorFromArtifact(file, null);
      if (pluginDescriptor == null) {
        MessagesEx.showErrorDialog(parent, IdeBundle
          .message("dialog.message.fail.to.load.plugin.descriptor.from.file", file.getFileName().toString()), CommonBundle.getErrorTitle());
        return false;
      }
      if (Registry.is("marketplace.certificate.signature.check")) {
        if (!PluginSignatureChecker.isSignedByAnyCertificates(pluginDescriptor.name, file.toFile())) {
          return false;
        }
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

      PluginManagerMain.PluginEnabler pluginEnabler = model instanceof PluginManagerMain.PluginEnabler
                                                      ? (PluginManagerMain.PluginEnabler)model
                                                      : new PluginManagerMain.PluginEnabler.HEADLESS();

      Task.WithResult<Pair<? extends PluginInstallOperation, ? extends IdeaPluginDescriptor>, RuntimeException> task =
        new Task.WithResult<>(null,
                              parent,
                              IdeBundle.message("progress.title.checking.plugin.dependencies"),
                              true) {
          @Override
          protected @NotNull Pair<? extends PluginInstallOperation, ? extends IdeaPluginDescriptor> compute(@NotNull ProgressIndicator indicator) {
            PluginInstallOperation operation = new PluginInstallOperation(List.of(),
                                                                          CustomPluginRepositoryService.getInstance()
                                                                            .getCustomRepositoryPlugins(),
                                                                          pluginEnabler,
                                                                          ProgressManager.getInstance().getProgressIndicator());
            operation.setAllowInstallWithoutRestart(true);

            return operation.checkMissingDependencies(pluginDescriptor, null) ?
                   Pair.create(operation, operation.checkDependenciesAndReplacements(pluginDescriptor)) :
                   Pair.empty();
          }
        };

      Pair<? extends PluginInstallOperation, ? extends IdeaPluginDescriptor> pair = ProgressManager.getInstance().run(task);
      PluginInstallOperation operation = pair.getFirst();
      if (operation == null) {
        return false;
      }

      Path oldFile = null;
      if (installedPlugin != null && !installedPlugin.isBundled()) {
        oldFile = installedPlugin.getPluginPath();
      }

      boolean installWithoutRestart = oldFile == null &&
                                      DynamicPlugins.allowLoadUnloadWithoutRestart(pluginDescriptor) &&
                                      !operation.isRestartRequired();
      if (!installWithoutRestart) {
        installAfterRestart(file, false, oldFile, pluginDescriptor);
      }
      ourState.onPluginInstall(pluginDescriptor, installedPlugin != null, !installWithoutRestart);

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
      checkInstalledPluginDependencies(model,
                                       pluginDescriptor,
                                       parent,
                                       ContainerUtil.map2Set(installedPlugins, PluginDescriptor::getPluginId));
      PluginManagerMain.suggestToEnableInstalledDependantPlugins(pluginEnabler, installedPlugins);

      callback.accept(new PluginInstallCallbackData(file, pluginDescriptor, !installWithoutRestart));
      for (PluginInstallCallbackData callbackData : installedDependencies) {
        if (!callbackData.getPluginDescriptor().getPluginId().equals(pluginDescriptor.getPluginId())) {
          callback.accept(callbackData);
        }
      }

      if (file.toString().endsWith(".zip") && Registry.is("ide.plugins.keep.archive")) {
        File tempFile = MarketplacePluginDownloadService.getPluginTempFile();
        FileUtil.copy(file.toFile(), tempFile);
        MarketplacePluginDownloadService.renameFileToZipRoot(tempFile);
      }
      return true;
    }
    catch (IOException ex) {
      MessagesEx.showErrorDialog(parent, ex.getMessage(), CommonBundle.getErrorTitle());
    }
    return false;
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

    return DynamicPlugins.loadPlugin(targetDescriptor);
  }

  private static void checkInstalledPluginDependencies(@NotNull InstalledPluginsTableModel model,
                                                       @NotNull IdeaPluginDescriptorImpl pluginDescriptor,
                                                       @Nullable Component parent,
                                                       Set<PluginId> installedDependencies) {
    final Set<PluginId> notInstalled = new HashSet<>();
    for (IdeaPluginDependency dep : pluginDescriptor.getDependencies()) {
      if (dep.isOptional()) continue;
      PluginId id = dep.getPluginId();
      if (installedDependencies.contains(id)) continue;
      final boolean disabled = model.isDisabled(id);
      final boolean enabled = model.isEnabled(id);
      if (!enabled && !disabled && !PluginManagerCore.isModuleDependency(id)) {
        notInstalled.add(id);
      }
    }
    if (!notInstalled.isEmpty()) {
      String deps = StringUtil.join(notInstalled, PluginId::toString, ", ");
      String message =
        IdeBundle.message("dialog.message.plugin.depends.on.unknown.plugin", pluginDescriptor.getName(), notInstalled.size(), deps);
      MessagesEx.showWarningDialog(parent, message, IdeBundle.message("dialog.title.install.plugin"));
    }
  }

  static void chooseAndInstall(@NotNull InstalledPluginsTableModel model,
                               @Nullable JComponent parent,
                               @NotNull Consumer<? super PluginInstallCallbackData> callback) {
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, false, true, true, false, false) {
      @Override
      public boolean isFileSelectable(VirtualFile file) {
        final String extension = file.getExtension();
        return Comparing.strEqual(extension, "jar") || Comparing.strEqual(extension, "zip");
      }
    };
    descriptor.setTitle(IdeBundle.message("chooser.title.plugin.file"));
    descriptor.setDescription(IdeBundle.message("chooser.description.jar.and.zip.archives.are.accepted"));
    String oldPath = PropertiesComponent.getInstance().getValue(PLUGINS_PRESELECTION_PATH);
    VirtualFile toSelect =
      oldPath == null ? null : VfsUtil.findFileByIoFile(new File(FileUtilRt.toSystemDependentName(oldPath)), false);
    FileChooser.chooseFile(descriptor, null, parent, toSelect, virtualFile -> {
      Path file = VfsUtilCore.virtualToIoFile(virtualFile).toPath();
      PropertiesComponent.getInstance()
        .setValue(PLUGINS_PRESELECTION_PATH, FileUtilRt.toSystemIndependentName(file.getParent().toString()));
      installFromDisk(model, file, callback, parent);
    });
  }

  private static @NotNull Path getPluginsPath() {
    return Paths.get(PathManager.getPluginsPath());
  }
}
