// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.CommonBundle;
import com.intellij.core.CoreBundle;
import com.intellij.diagnostic.LoadingState;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.marketplace.MarketplacePluginDownloadService;
import com.intellij.ide.plugins.marketplace.PluginSignatureChecker;
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector;
import com.intellij.ide.plugins.marketplace.statistics.enums.InstallationSourceEnum;
import com.intellij.ide.plugins.newui.PluginManagerSession;
import com.intellij.ide.plugins.newui.PluginManagerSessionService;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.Decompressor;
import com.intellij.util.ui.IoErrorText;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.intellij.ide.plugins.BrokenPluginFileKt.isBrokenPlugin;
import static com.intellij.ide.startup.StartupActionScriptManager.*;

public final class PluginInstaller {
  private static final Logger LOG = Logger.getInstance(PluginInstaller.class);
  private static final boolean DROP_DISABLED_FLAG_OF_REINSTALLED_PLUGINS =
    SystemProperties.getBooleanProperty("plugins.drop-disabled-flag-of-uninstalled-plugins", true);

  public static final String UNKNOWN_HOST_MARKER = "__unknown_repository__";

  static final Object ourLock = new Object();

  private PluginInstaller() { }

  /**
   * @return true if restart is needed
   */
  @ApiStatus.Internal
  public static boolean prepareToUninstall(@NotNull IdeaPluginDescriptorImpl pluginDescriptor) throws IOException {
    synchronized (ourLock) {
      if (PluginManagerCore.isPluginInstalled(pluginDescriptor.getPluginId())) {
        if (pluginDescriptor.isBundled()) {
          throw new IllegalArgumentException("Plugin is bundled: " + pluginDescriptor.getPluginId());
        }
        else {
          var needRestart = pluginDescriptor.isEnabled() && !DynamicPlugins.allowLoadUnloadWithoutRestart(pluginDescriptor);
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

  @ApiStatus.Internal
  public static void uninstallAfterRestart(@NotNull IdeaPluginDescriptor pluginDescriptor) throws IOException {
    if (pluginDescriptor.isBundled()) {
      throw new IllegalArgumentException("Plugin is bundled: " + pluginDescriptor.getPluginId());
    }
    // Make sure this method does not interfere with installAfterRestart by adding the DeleteCommand to the beginning of the script.
    // This way plugin installation always takes place after plugin uninstallation.
    addActionCommandsToBeginning(List.of(new DeleteCommand(pluginDescriptor.getPluginPath())));
  }

  @ApiStatus.Internal
  public static boolean unloadDynamicPlugin(
    @Nullable JComponent parentComponent,
    @NotNull IdeaPluginDescriptorImpl pluginDescriptor,
    boolean isUpdate
  ) {
    var options = new DynamicPlugins.UnloadPluginOptions().withDisable(false).withWaitForClassloaderUnload(true).withUpdate(isUpdate);
    return parentComponent != null ?
           DynamicPlugins.INSTANCE.unloadPluginWithProgress(null, parentComponent, pluginDescriptor, options) :
           DynamicPlugins.INSTANCE.unloadPlugin(pluginDescriptor, options);
  }

  @ApiStatus.Internal
  public static boolean uninstallDynamicPlugin(
    @Nullable JComponent parentComponent,
    @NotNull IdeaPluginDescriptorImpl pluginDescriptor,
    boolean isUpdate
  ) {
    if (pluginDescriptor.isBundled()) {
      throw new IllegalArgumentException("Plugin is bundled: " + pluginDescriptor.getPluginId());
    }

    var uninstalledWithoutRestart = !pluginDescriptor.isEnabled() || unloadDynamicPlugin(parentComponent, pluginDescriptor, isUpdate);
    if (uninstalledWithoutRestart) {
      try {
        NioFiles.deleteRecursively(pluginDescriptor.getPluginPath());
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

  @ApiStatus.Internal
  public static void installAfterRestartAndKeepIfNecessary(
    @NotNull IdeaPluginDescriptor newDescriptor,
    @NotNull Path newPluginPath,
    @Nullable Path oldPluginPath
  ) throws IOException {
    installAfterRestart(newDescriptor, newPluginPath, oldPluginPath, !keepArchive());
  }

  @ApiStatus.Internal
  public static void installAfterRestart(
    @NotNull IdeaPluginDescriptor descriptor,
    @NotNull Path sourceFile,
    @Nullable Path existingPlugin,
    boolean deleteSourceFile
  ) throws IOException {
    var commands = new ArrayList<ActionCommand>();

    if (existingPlugin != null) {
      commands.add(new DeleteCommand(existingPlugin));
    }

    var pluginsPath = getPluginsPath();
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

  private static @Nullable Path installWithoutRestart(Path sourceFile, IdeaPluginDescriptorImpl descriptor, @Nullable JComponent parent) {
    Path result;
    try {
      @SuppressWarnings("UsagesOfObsoleteApi")
      Task.WithResult<Path, IOException> task =
        new Task.WithResult<>(null, parent, IdeBundle.message("progress.title.installing.plugin", descriptor.getName()), false) {
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
      NioFiles.deleteRecursively(target);
      new Decompressor.Zip(sourceFile).extract(targetPath);
    }
    return target;
  }

  public static String rootEntryName(@NotNull Path zip) throws IOException {
    try (ZipFile zipFile = new ZipFile(zip.toFile())) {
      var entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry zipEntry = entries.nextElement();
        // we do not necessarily get a separate entry for the subdirectory when the file
        // in the ZIP archive is placed in a subdirectory, so we need to check if the slash is found anywhere in the path
        String name = zipEntry.getName();
        int i = name.indexOf('/');
        if (i > 0) {
          return name.substring(0, i);
        }
      }
    }

    throw new IOException("Corrupted archive (no file entries): " + zip);
  }

  public static void addStateListener(@NotNull PluginStateListener listener) {
    PluginStateManager.addStateListener(listener);
  }

  @RequiresEdt
  static void installFromDisk(
    @NotNull InstalledPluginsTableModel model,
    @NotNull PluginEnabler pluginEnabler,
    @NotNull Path file,
    @Nullable Project project,
    @Nullable JComponent parent,
    @NotNull Consumer<? super PluginInstallCallbackData> callback
  ) {
    try {
      var pluginDescriptor = ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
        return PluginDescriptorLoader.loadDescriptorFromArtifact(file, null);
      }, IdeBundle.message("action.InstallFromDiskAction.progress.text"), true, project);

      if (pluginDescriptor == null) {
        MessagesEx.showErrorDialog(parent, IdeBundle.message("dialog.message.fail.to.load.plugin.descriptor.from.file", file.getFileName()),
                                   CommonBundle.getErrorTitle());
        return;
      }

      if (!PluginManagerMain.checkThirdPartyPluginsAllowed(List.of(pluginDescriptor))) {
        return;
      }

      if (!PluginManagementPolicy.getInstance().canInstallPlugin(pluginDescriptor)) {
        var message = IdeBundle.message("dialog.message.plugin.is.not.allowed", pluginDescriptor.getName());
        MessagesEx.showWarningDialog(parent, message, IdeBundle.message("dialog.title.install.plugin"));
        return;
      }

      InstalledPluginsState ourState = InstalledPluginsState.getInstance();
      if (ourState.wasInstalled(pluginDescriptor.getPluginId())) {
        var message = IdeBundle.message("dialog.message.plugin.was.already.installed", pluginDescriptor.getName());
        MessagesEx.showWarningDialog(parent, message, IdeBundle.message("dialog.title.install.plugin"));
        return;
      }

      var error = PluginManagerCore.checkBuildNumberCompatibility(pluginDescriptor, PluginManagerCore.getBuildNumber());
      if (error != null) {
        MessagesEx.showErrorDialog(parent, error.getDetailedMessage(), CommonBundle.getErrorTitle());
        return;
      }
      if (isBrokenPlugin(pluginDescriptor)) {
        var message =
          CoreBundle.message("plugin.loading.error.long.marked.as.broken", pluginDescriptor.getName(), pluginDescriptor.getVersion());
        MessagesEx.showErrorDialog(parent, message, CommonBundle.getErrorTitle());
        return;
      }

      var installedPlugin = PluginManagerCore.getPlugin(pluginDescriptor.getPluginId());
      if (installedPlugin != null && ApplicationInfoEx.getInstanceEx().isEssentialPlugin(installedPlugin.getPluginId())) {
        var message = IdeBundle
          .message("dialog.message.plugin.core.part", pluginDescriptor.getName(), ApplicationNamesInfo.getInstance().getFullProductName());
        MessagesEx.showErrorDialog(parent, message, CommonBundle.getErrorTitle());
        return;
      }

      var previousVersion = installedPlugin != null ? installedPlugin.getVersion() : null;
      PluginManagerUsageCollector.pluginInstallationStarted(pluginDescriptor, InstallationSourceEnum.FROM_DISK, previousVersion);

      if (!PluginSignatureChecker.verifyIfRequired(pluginDescriptor, file, false, true)) {
        return;
      }

      @SuppressWarnings("UsagesOfObsoleteApi")
      Task.WithResult<Pair<PluginInstallOperation, ? extends IdeaPluginDescriptor>, RuntimeException> task =
        new Task.WithResult<>(null, parent, IdeBundle.message("progress.title.checking.plugin.dependencies"), true) {
          @Override
          protected @NotNull Pair<PluginInstallOperation, ? extends IdeaPluginDescriptor> compute(@NotNull ProgressIndicator indicator) {
            var repositoryPlugins = CustomPluginRepositoryService.getInstance().getCustomRepositoryPlugins();
            var operation = new PluginInstallOperation(List.of(), repositoryPlugins, indicator, pluginEnabler);
            operation.setAllowInstallWithoutRestart(true);
            return operation.checkMissingDependencies(pluginDescriptor, null) ?
                   new Pair<>(operation, operation.checkDependenciesAndReplacements(pluginDescriptor)) : Pair.empty();
          }
        };

      @SuppressWarnings("UsagesOfObsoleteApi")
      var pair = ProgressManager.getInstance().run(task);
      var operation = pair.getFirst();
      if (operation == null) {
        return;
      }

      var oldFile = installedPlugin != null && !installedPlugin.isBundled() ? installedPlugin.getPluginPath() : null;
      var isRestartRequired = oldFile != null ||
                              !DynamicPlugins.allowLoadUnloadWithoutRestart(pluginDescriptor) ||
                              operation.isRestartRequired();
      if (isRestartRequired) {
        installAfterRestart(pluginDescriptor, file, oldFile, false);
      }
      ourState.onPluginInstall(pluginDescriptor, installedPlugin != null, isRestartRequired);

      var toDisable = pair.getSecond();
      if (toDisable != null) {
        // TODO[yole] unload and check for restart
        pluginEnabler.disable(Set.of(toDisable));
      }

      var installedDependencies = operation.getInstalledDependentPlugins();
      var installedPlugins = new ArrayList<IdeaPluginDescriptor>();
      installedPlugins.add(pluginDescriptor);
      for (var plugin : installedDependencies) {
        installedPlugins.add(plugin.getPluginDescriptor());
      }

      var installedDependencyIds = ContainerUtil.map2Set(installedPlugins, PluginDescriptor::getPluginId);
      var notInstalled = findNotInstalledPluginDependencies(pluginDescriptor.getDependencies(), model, installedDependencyIds);
      if (!notInstalled.isEmpty()) {
        var message = IdeBundle.message("dialog.message.plugin.depends.on.unknown.plugin",
                                        pluginDescriptor.getName(),
                                        notInstalled.size(),
                                        StringUtil.join(notInstalled, ", "));
        MessagesEx.showWarningDialog(parent, message, IdeBundle.message("dialog.title.install.plugin"));
      }

      PluginManagerMain.suggestToEnableInstalledDependantPlugins(pluginEnabler, installedPlugins);

      if (!isRestartRequired) {
        PluginManagerSession session = PluginManagerSessionService.getInstance().getSession(model.mySessionId.toString());
        if (session != null) {
          session.getDynamicPluginsToInstall().put(pluginDescriptor.getPluginId(), new PendingDynamicPluginInstall(file, pluginDescriptor));
        }
      }
      callback.accept(new PluginInstallCallbackData(file, pluginDescriptor, isRestartRequired));
      for (var callbackData : installedDependencies) {
        if (!callbackData.getPluginDescriptor().getPluginId().equals(pluginDescriptor.getPluginId())) {
          callback.accept(callbackData);
        }
      }

      if (file.toString().endsWith(".zip") && keepArchive()) {
        var tempFile = MarketplacePluginDownloadService.getPluginTempFile();
        Files.copy(file, tempFile, StandardCopyOption.REPLACE_EXISTING);
        MarketplacePluginDownloadService.renameFileToZipRoot(tempFile);
      }
    }
    catch (IOException ex) {
      LOG.error(ex);
      MessagesEx.showErrorDialog(parent, IoErrorText.message(ex), CommonBundle.getErrorTitle());
    }
  }

  public static boolean installAndLoadDynamicPlugin(@NotNull Path file, @NotNull IdeaPluginDescriptorImpl descriptor) {
    return installAndLoadDynamicPlugin(file, null, descriptor);
  }

  /**
   * @return {@code true} if plugin was successfully installed without a restart, {@code false} if restart is required
   */
  public static boolean installAndLoadDynamicPlugin(
    @NotNull Path file,
    @Nullable JComponent parent,
    @NotNull IdeaPluginDescriptorImpl descriptor
  ) {
    var targetFile = installWithoutRestart(file, descriptor, parent);
    if (targetFile == null) {
      return false;
    }

    var targetDescriptor = PluginDescriptorLoader.loadDescriptor(targetFile, false, PluginXmlPathResolver.DEFAULT_PATH_RESOLVER);
    if (targetDescriptor == null) {
      return false;
    }

    var targetPluginId = targetDescriptor.getPluginId();

    // FIXME this is a bad place to do this IJPL-190806; bundled plugin may be not unloaded at this point
    var loadedPlugin = PluginManagerCore.findPlugin(targetPluginId);
    if (loadedPlugin != null && PluginManagerCore.isLoaded(loadedPlugin)) {
      LOG.warn("Plugin " + loadedPlugin + " is still loaded, restart is required"); // FIXME IJPL-193781
      return false;
    }

    var pluginSet = PluginManagerCore.getPluginSet();
    var contentModuleIdMap = pluginSet.buildContentModuleIdMap();
    var pluginMap = pluginSet.buildPluginIdMap();

    if (PluginManagerCoreKt.pluginRequiresUltimatePluginButItsDisabled(targetDescriptor, pluginMap, contentModuleIdMap)) {
      LOG.warn("Plugin " + targetPluginId + " requires Ultimate plugin, but it's disabled");
      return false;
    }

    if (DROP_DISABLED_FLAG_OF_REINSTALLED_PLUGINS && PluginEnabler.HEADLESS.isDisabled(targetPluginId)) {
      var wasInstalledBefore = pluginSet.isPluginInstalled(targetPluginId);
      if (!wasInstalledBefore) {
        // FIXME can't drop the disabled flag first because it's implementation filters ids against the current plugin set;
        //  so load first, then enable
        targetDescriptor.setMarkedForLoading(true);
        var result = DynamicPlugins.INSTANCE.loadPlugin(targetDescriptor);
        PluginEnabler.HEADLESS.enable(Set.of(targetDescriptor));
        return result;
      }
    }

    return PluginEnabler.HEADLESS.isDisabled(targetPluginId) || DynamicPlugins.INSTANCE.loadPlugin(targetDescriptor);
  }

  private static boolean keepArchive() {
    return !LoadingState.COMPONENTS_LOADED.isOccurred() || RegistryManager.getInstance().is("ide.plugins.keep.archive");
  }

  private static Set<String> findNotInstalledPluginDependencies(
    List<? extends IdeaPluginDependency> dependencies,
    InstalledPluginsTableModel model,
    Set<PluginId> installedDependencies
  ) {
    var notInstalled = new HashSet<String>();

    for (var dependency : dependencies) {
      if (dependency.isOptional()) continue;

      var pluginId = dependency.getPluginId();
      if (installedDependencies.contains(pluginId) ||
          model.isLoaded(pluginId) ||
          PluginManagerCore.looksLikePlatformPluginAlias(pluginId) ||
          PluginManagerCore.findPluginByPlatformAlias(pluginId) != null) {
        continue;
      }

      notInstalled.add(pluginId.getIdString());
    }

    return notInstalled;
  }

  private static Path getPluginsPath() {
    return Path.of(PathManager.getPluginsPath());
  }

  @RequiresEdt
  static void installPluginFromCallbackData(@NotNull PluginInstallCallbackData callbackData) {
    if (callbackData.getPluginDescriptor() instanceof IdeaPluginDescriptorImpl descriptor && callbackData.getFile() != null) {
      if (callbackData.getRestartNeeded()) {
        shutdownOrRestartAppAfterInstall(descriptor);
      }
      else {
        var loaded = installAndLoadDynamicPlugin(callbackData.getFile(), descriptor);
        if (!loaded) {
          shutdownOrRestartAppAfterInstall(descriptor);
        }
      }
    }
  }

  private static void shutdownOrRestartAppAfterInstall(IdeaPluginDescriptorImpl descriptor) {
    PluginManagerConfigurable.shutdownOrRestartAppAfterInstall(
      PluginManagerConfigurable.getUpdatesDialogTitle(),
      action -> IdeBundle.message("plugin.installed.ide.restart.required.message",
                                  descriptor.getName(),
                                  action,
                                  ApplicationNamesInfo.getInstance()
                                    .getFullProductName()));
  }
}
