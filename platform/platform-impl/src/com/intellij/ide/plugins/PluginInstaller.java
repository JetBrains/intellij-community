// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.CommonBundle;
import com.intellij.core.CoreBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.marketplace.MarketplacePluginDownloadService;
import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.Decompressor;
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

/**
 * @author stathik
 */
public final class PluginInstaller {
  private static final Logger LOG = Logger.getInstance(PluginInstaller.class);

  public static final String UNKNOWN_HOST_MARKER = "__unknown_repository__";

  static final Object ourLock = new Object();
  private static final String PLUGINS_PRESELECTION_PATH = "plugins.preselection.path";

  private PluginInstaller() { }

  public static boolean prepareToInstall(List<PluginNode> pluginsToInstall,
                                         List<? extends IdeaPluginDescriptor> customOrAllPlugins,
                                         boolean allowInstallWithoutRestart,
                                         PluginManagerMain.PluginEnabler pluginEnabler,
                                         Runnable onSuccess,
                                         @NotNull ProgressIndicator indicator) {
    //TODO: `PluginInstallOperation` expects only `customPlugins`, but it can take `allPlugins` too
    PluginInstallOperation operation = new PluginInstallOperation(pluginsToInstall, customOrAllPlugins, pluginEnabler, indicator);
    operation.setAllowInstallWithoutRestart(allowInstallWithoutRestart);
    operation.run();
    boolean success = operation.isSuccess();
    if (success) {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (allowInstallWithoutRestart) {
          for (PendingDynamicPluginInstall install : operation.getPendingDynamicPluginInstalls()) {
            installAndLoadDynamicPlugin(install.getFile(), null, install.getPluginDescriptor());
          }
        }
        if (onSuccess != null) {
          onSuccess.run();
        }
      });
    }
    return success;
  }

  /**
   * @return true if restart is needed
   */
  public static boolean prepareToUninstall(@NotNull IdeaPluginDescriptor pluginDescriptor) throws IOException {
    synchronized (ourLock) {
      if (PluginManagerCore.isPluginInstalled(pluginDescriptor.getPluginId())) {
        if (pluginDescriptor.isBundled()) {
          LOG.error("Plugin is bundled: " + pluginDescriptor.getPluginId());
        }
        else {
          boolean needRestart = !DynamicPlugins.allowLoadUnloadWithoutRestart((IdeaPluginDescriptorImpl)pluginDescriptor);
          if (needRestart) {
            uninstallAfterRestart(pluginDescriptor);
          }

          PluginStateManager.fireState(pluginDescriptor, false);
          return needRestart;
        }
      }
    }
    return false;
  }

  private static void uninstallAfterRestart(IdeaPluginDescriptor pluginDescriptor) throws IOException {
    StartupActionScriptManager.addActionCommand(new StartupActionScriptManager.DeleteCommand(pluginDescriptor.getPluginPath()));
  }

  public static boolean uninstallDynamicPlugin(@Nullable JComponent parentComponent, IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
    DynamicPlugins.UnloadPluginOptions options = new DynamicPlugins.UnloadPluginOptions()
      .withUpdate(isUpdate)
      .withWaitForClassloaderUnload(true);

    boolean uninstalledWithoutRestart = parentComponent != null
      ? DynamicPlugins.unloadPluginWithProgress(null, parentComponent, (IdeaPluginDescriptorImpl)pluginDescriptor, options)
      : DynamicPlugins.unloadPlugin((IdeaPluginDescriptorImpl)pluginDescriptor, options);

    if (uninstalledWithoutRestart) {
      try {
        FileUtil.delete(pluginDescriptor.getPluginPath());
      }
      catch (IOException e) {
        LOG.info("Failed to delete jar of dynamic plugin", e);
        uninstalledWithoutRestart = false;
      }
    }

    if (!uninstalledWithoutRestart) {
      try {
        uninstallAfterRestart(pluginDescriptor);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    return uninstalledWithoutRestart;
  }

  /**
   * @deprecated Use {@link #installAfterRestart(File, boolean, Path, IdeaPluginDescriptor)}
   */
  @Deprecated
  public static void installAfterRestart(@NotNull File sourceFile,
                                         boolean deleteSourceFile,
                                         @Nullable File existingPlugin,
                                         @NotNull IdeaPluginDescriptor descriptor) throws IOException {

  }

  public static void installAfterRestart(@NotNull Path sourceFile,
                                         boolean deleteSourceFile,
                                         @Nullable Path existingPlugin,
                                         @NotNull IdeaPluginDescriptor descriptor) throws IOException {
    List<StartupActionScriptManager.ActionCommand> commands = new ArrayList<>();

    if (existingPlugin != null) {
      commands.add(new StartupActionScriptManager.DeleteCommand(existingPlugin));
    }

    Path pluginsPath = Paths.get(PathManager.getPluginsPath());
    if (sourceFile.getFileName().toString().endsWith(".jar")) {
      commands.add(new StartupActionScriptManager.CopyCommand(sourceFile, pluginsPath.resolve(sourceFile.getFileName())));
    }
    else {
      // drops stale directory
      commands.add(new StartupActionScriptManager.DeleteCommand(pluginsPath.resolve(rootEntryName(sourceFile))));
      commands.add(new StartupActionScriptManager.UnzipCommand(sourceFile, pluginsPath));
    }

    if (deleteSourceFile) {
      commands.add(new StartupActionScriptManager.DeleteCommand(sourceFile));
    }

    StartupActionScriptManager.addActionCommands(commands);

    PluginStateManager.fireState(descriptor, true);
  }

  private static @Nullable Path installWithoutRestart(@NotNull Path sourceFile, IdeaPluginDescriptorImpl descriptor, Component parent) {
    Ref<Throwable> ref = new Ref<>();
    Ref<Path> refTarget = new Ref<>();
    String pluginName = descriptor.getName();
    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      try {
        refTarget.set(unpackPlugin(sourceFile, Paths.get(PathManager.getPluginsPath())));
      }
      catch (Throwable e) {
        LOG.warn("Plugin " + descriptor + " failed to install without restart. " + e.getMessage(), e);
        ref.set(e);
      }
    }, IdeBundle.message("progress.title.installing.plugin", pluginName), false, null, parent instanceof JComponent ? (JComponent)parent : null);
    Throwable exception = ref.get();
    PluginStateManager.fireState(descriptor, true);
    return exception != null ? null : refTarget.get();
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

  public static boolean installFromDisk(@NotNull InstalledPluginsTableModel model,
                                        @NotNull Path file,
                                        @NotNull Consumer<? super PluginInstallCallbackData> callback,
                                        @Nullable Component parent) {
    try {
      IdeaPluginDescriptorImpl pluginDescriptor = PluginDescriptorLoader.loadDescriptorFromArtifact(file, null);
      if (pluginDescriptor == null) {
        MessagesEx.showErrorDialog(parent, IdeBundle.message("dialog.message.fail.to.load.plugin.descriptor.from.file", file.getFileName().toString()), CommonBundle.getErrorTitle());
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
        String message = CoreBundle.message("plugin.loading.error.long.marked.as.broken", pluginDescriptor.getName(), pluginDescriptor.getVersion());
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
      Ref<Boolean> cancel = Ref.create(false);
      Ref<IdeaPluginDescriptor> toDisable = new Ref<>();
      Ref<Boolean> dependenciesRequireRestart = Ref.create(false);
      Set<PluginInstallCallbackData> installedDependencies = new HashSet<>();
      boolean success = ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
        PluginInstallOperation dependencyInstallOperation = new PluginInstallOperation(
          Collections.emptyList(),
          CustomPluginRepositoryService.getInstance().getCustomRepositoryPlugins(),
          pluginEnabler,
          ProgressManager.getInstance().getProgressIndicator());
        dependencyInstallOperation.setAllowInstallWithoutRestart(true);
        Ref<IdeaPluginDescriptor> ref = dependencyInstallOperation.checkDependenciesAndReplacements(pluginDescriptor, null);
        if (ref == null) {
          cancel.set(true);
        }
        else {
          dependenciesRequireRestart.set(dependencyInstallOperation.isRestartRequired());
          installedDependencies.addAll(dependencyInstallOperation.getInstalledDependentPlugins());
          toDisable.set(ref.get());
        }
      }, IdeBundle.message("progress.title.checking.plugin.dependencies"), true, null, (JComponent) parent);

      if (!success || cancel.get()) return false;

      Path oldFile = null;
      if (installedPlugin != null && !installedPlugin.isBundled()) {
        oldFile = installedPlugin.getPluginPath();
      }

      boolean installWithoutRestart = oldFile == null && DynamicPlugins.allowLoadUnloadWithoutRestart(pluginDescriptor) && !dependenciesRequireRestart.get();
      if (!installWithoutRestart) {
        installAfterRestart(file, false, oldFile, pluginDescriptor);
      }
      ourState.onPluginInstall(pluginDescriptor, installedPlugin != null, !installWithoutRestart);
      if (!toDisable.isNull()) {
        // TODO[yole] unload and check for restart
        pluginEnabler.disablePlugins(Collections.singleton(toDisable.get()));
      }

      List<IdeaPluginDescriptor> installedPlugins = new ArrayList<>();
      installedPlugins.add(pluginDescriptor);
      for (PluginInstallCallbackData plugin : installedDependencies) {
        installedPlugins.add(plugin.getPluginDescriptor());
      }
      checkInstalledPluginDependencies(model, pluginDescriptor, parent, ContainerUtil.map2Set(installedPlugins, (descriptor) -> descriptor.getPluginId()));
      PluginManagerMain.suggestToEnableInstalledDependantPlugins(pluginEnabler, installedPlugins);

      callback.accept(new PluginInstallCallbackData(file, pluginDescriptor, !installWithoutRestart));
      for (PluginInstallCallbackData callbackData: installedDependencies) {
        if (!callbackData.getPluginDescriptor().getPluginId().equals(pluginDescriptor.getPluginId())) {
          callback.accept(callbackData);
        }
      }

      if (file.toString().endsWith(".zip") && UpdateSettings.getInstance().isKeepPluginsArchive()) {
        File tempFile = MarketplacePluginDownloadService.INSTANCE.getPluginTempFile();
        FileUtil.copy(file.toFile(), tempFile);
        MarketplacePluginDownloadService.INSTANCE.renameFileToZipRoot(tempFile);
      }
      return true;
    }
    catch (IOException ex) {
      MessagesEx.showErrorDialog(parent, ex.getMessage(), CommonBundle.getErrorTitle());
    }
    return false;
  }

  /**
   * @return true if plugin was successfully installed without restart, false if restart is required
   */
  public static boolean installAndLoadDynamicPlugin(@NotNull Path file,
                                                    @Nullable Component parent,
                                                    IdeaPluginDescriptorImpl pluginDescriptor) {
    Path targetFile = installWithoutRestart(file, pluginDescriptor, parent);
    if (targetFile != null) {
      IdeaPluginDescriptorImpl targetDescriptor = PluginManager.loadDescriptor(targetFile, PluginManagerCore.PLUGIN_XML);
      if (targetDescriptor != null) {
        return DynamicPlugins.loadPlugin(targetDescriptor);
      }
    }
    return false;
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
                               @Nullable Component parent,
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
      PropertiesComponent.getInstance().setValue(PLUGINS_PRESELECTION_PATH, FileUtilRt.toSystemIndependentName(file.getParent().toString()));
      installFromDisk(model, file, callback, parent);
    });
  }
}