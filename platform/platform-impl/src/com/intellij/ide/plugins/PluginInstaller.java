// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.Decompressor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author stathik
 */
public class PluginInstaller {
  private static final Logger LOG = Logger.getInstance(PluginInstaller.class);
  public static final String UNKNOWN_HOST_MARKER = "__unknown_repository__";

  private static final Object ourLock = new Object();
  private static final InstalledPluginsState ourState = InstalledPluginsState.getInstance();
  private static final String PLUGINS_PRESELECTION_PATH = "plugins.preselection.path";

  private PluginInstaller() { }

  public static boolean prepareToInstall(List<PluginNode> pluginsToInstall,
                                         List<? extends IdeaPluginDescriptor> allPlugins,
                                         PluginManagerMain.PluginEnabler pluginEnabler,
                                         @NotNull ProgressIndicator indicator) {
    updateUrls(pluginsToInstall, indicator);
    Set<PluginNode> dependant = new THashSet<>();
    boolean install = prepareToInstall(pluginsToInstall, allPlugins, dependant, pluginEnabler, indicator);
    for (PluginNode node : dependant) {
      if (!pluginsToInstall.contains(node)) {
        pluginsToInstall.add(node);
      }
    }
    return install;
  }

  private static void updateUrls(List<? extends PluginNode> pluginsToInstall, @NotNull ProgressIndicator indicator) {
    boolean unknownNodes = false;
    for (PluginNode node : pluginsToInstall) {
      if (node.getRepositoryName() == UNKNOWN_HOST_MARKER) {
        unknownNodes = true;
        break;
      }
    }
    if (!unknownNodes) return;

    List<String> hosts = ContainerUtil.newSmartList();
    ContainerUtil.addIfNotNull(hosts, ApplicationInfoEx.getInstanceEx().getBuiltinPluginsUrl());
    hosts.addAll(UpdateSettings.getInstance().getPluginHosts());
    Map<PluginId, IdeaPluginDescriptor> allPlugins = new HashMap<>();
    for (String host : hosts) {
      try {
        List<IdeaPluginDescriptor> descriptors = RepositoryHelper.loadPlugins(host, indicator);
        for (IdeaPluginDescriptor descriptor : descriptors) {
          allPlugins.put(descriptor.getPluginId(), descriptor);
        }
      }
      catch (IOException ignored) { }
    }

    for (PluginNode node : pluginsToInstall) {
      if (node.getRepositoryName() == UNKNOWN_HOST_MARKER) {
        IdeaPluginDescriptor descriptor = allPlugins.get(node.getPluginId());
        if (descriptor != null) {
          node.setRepositoryName(((PluginNode)descriptor).getRepositoryName());
          node.setDownloadUrl(((PluginNode)descriptor).getDownloadUrl());
        }
        else {
          node.setRepositoryName(null);
        }
      }
    }
  }

  private static boolean prepareToInstall(List<? extends PluginNode> pluginsToInstall,
                                          List<? extends IdeaPluginDescriptor> allPlugins,
                                          Set<PluginNode> installedDependant,
                                          PluginManagerMain.PluginEnabler pluginEnabler,
                                          @NotNull ProgressIndicator indicator) {
    List<PluginId> pluginIds = new SmartList<>();
    for (PluginNode pluginNode : pluginsToInstall) {
      pluginIds.add(pluginNode.getPluginId());
    }

    boolean result = false;
    for (PluginNode pluginNode : pluginsToInstall) {
      indicator.setText(pluginNode.getName());
      try {
        result |= prepareToInstall(pluginNode, pluginIds, allPlugins, installedDependant, pluginEnabler, indicator);
      }
      catch (IOException e) {
        String title = IdeBundle.message("title.plugin.error");
        Notifications.Bus.notify(new Notification(title, title, pluginNode.getName() + ": " + e.getMessage(), NotificationType.ERROR));
        return false;
      }
    }

    return result;
  }

  private static boolean prepareToInstall(PluginNode pluginNode,
                                          List<? extends PluginId> pluginIds,
                                          List<? extends IdeaPluginDescriptor> allPlugins,
                                          Set<PluginNode> installedDependant,
                                          PluginManagerMain.PluginEnabler pluginEnabler,
                                          @NotNull ProgressIndicator indicator) throws IOException {
    installedDependant.add(pluginNode);

    // check for dependent plugins at first.
    if (pluginNode.getDepends() != null && !pluginNode.getDepends().isEmpty()) {
      // prepare plugins list for install
      final PluginId[] optionalDependentPluginIds = pluginNode.getOptionalDependentPluginIds();
      final List<PluginNode> depends = new ArrayList<>();
      final List<PluginNode> optionalDeps = new ArrayList<>();
      for (int i = 0; i < pluginNode.getDepends().size(); i++) {
        PluginId depPluginId = pluginNode.getDepends().get(i);
        if (PluginManagerCore.isPluginInstalled(depPluginId) || PluginManagerCore.isModuleDependency(depPluginId) ||
            InstalledPluginsState.getInstance().wasInstalled(depPluginId) ||
            pluginIds != null && pluginIds.contains(depPluginId)) {
          // ignore installed or installing plugins
          continue;
        }

        IdeaPluginDescriptor depPluginDescriptor = findPluginInRepo(depPluginId, allPlugins);
        PluginNode depPlugin;
        if (depPluginDescriptor instanceof PluginNode) {
          depPlugin = (PluginNode) depPluginDescriptor;
        }
        else {
          depPlugin = new PluginNode(depPluginId, depPluginId.getIdString(), "-1");
        }

        if (depPluginDescriptor != null) {
          if (ArrayUtil.indexOf(optionalDependentPluginIds, depPluginId) != -1) {
            optionalDeps.add(depPlugin);
          }
          else {
            depends.add(depPlugin);
          }
        }
      }

      if (!depends.isEmpty()) { // has something to install prior installing the plugin
        final boolean[] proceed = new boolean[1];
        try {
          ApplicationManager.getApplication().invokeAndWait(() -> {
            String title = IdeBundle.message("plugin.manager.dependencies.detected.title");
            String deps = StringUtil.join(depends, node -> node.getName(), ", ");
            String message = IdeBundle.message("plugin.manager.dependencies.detected.message", depends.size(), deps);
            proceed[0] = Messages.showYesNoDialog(message, title, Messages.getWarningIcon()) == Messages.YES;
          }, ModalityState.any());
        }
        catch (Exception e) {
          return false;
        }
        if (!proceed[0] || !prepareToInstall(depends, allPlugins, installedDependant, pluginEnabler, indicator)) {
          return false;
        }
      }

      if (!optionalDeps.isEmpty()) {
        final boolean[] proceed = new boolean[1];
        try {
          ApplicationManager.getApplication().invokeAndWait(() -> {
            String title = IdeBundle.message("plugin.manager.dependencies.detected.title");
            String deps = StringUtil.join(optionalDeps, node -> node.getName(), ", ");
            String message = IdeBundle.message("plugin.manager.optional.dependencies.detected.message", optionalDeps.size(), deps);
            proceed[0] = Messages.showYesNoDialog(message, title, Messages.getWarningIcon()) == Messages.YES;
          }, ModalityState.any());
        }
        catch (Exception e) {
          return false;
        }
        if (proceed[0] && !prepareToInstall(optionalDeps, allPlugins, installedDependant, pluginEnabler, indicator)) {
          return false;
        }
      }
    }

    Ref<IdeaPluginDescriptor> toDisable = Ref.create(null);
    PluginReplacement pluginReplacement = ContainerUtil.find(PluginReplacement.EP_NAME.getExtensions(),
      r -> r.getNewPluginId().equals(pluginNode.getPluginId().getIdString()));
    if (pluginReplacement != null) {
      IdeaPluginDescriptor oldPlugin = PluginManagerCore.getPlugin(pluginReplacement.getOldPluginDescriptor().getPluginId());
      if (oldPlugin == null) {
        LOG.warn("Plugin with id '" + pluginReplacement.getOldPluginDescriptor().getPluginId() + "' not found");
      }
      else if (!pluginEnabler.isDisabled(oldPlugin.getPluginId())) {
        ApplicationManager.getApplication().invokeAndWait(() -> {
          String title = IdeBundle.message("plugin.manager.obsolete.plugins.detected.title");
          String message = pluginReplacement.getReplacementMessage(oldPlugin, pluginNode);
          if (Messages.showYesNoDialog(message, title, Messages.getWarningIcon()) == Messages.YES) {
            toDisable.set(oldPlugin);
          }
        });
      }
    }

    PluginDownloader downloader = PluginDownloader.createDownloader(pluginNode, pluginNode.getRepositoryName(), null);

    if (downloader.prepareToInstall(indicator)) {
      synchronized (ourLock) {
        downloader.install();
      }
      pluginNode.setStatus(PluginNode.Status.DOWNLOADED);
      if (!toDisable.isNull()) {
        pluginEnabler.disablePlugins(Collections.singleton(toDisable.get()));
      }
    }
    else {
      return false;
    }

    return true;
  }

  @Nullable
  private static IdeaPluginDescriptor findPluginInRepo(PluginId depPluginId, List<? extends IdeaPluginDescriptor> allPlugins) {
    return allPlugins.stream().parallel().filter(p -> p.getPluginId().equals(depPluginId)).findAny().orElse(null);
  }

  /**
   * @return true if restart is needed
   */
  public static boolean prepareToUninstall(PluginId pluginId) throws IOException {
    synchronized (ourLock) {
      if (PluginManagerCore.isPluginInstalled(pluginId)) {
        IdeaPluginDescriptor pluginDescriptor = PluginManagerCore.getPlugin(pluginId);
        if (pluginDescriptor == null) {
          PluginManagerMain.LOG.error("Plugin not found: " + pluginId);
        }
        else if (pluginDescriptor.isBundled()) {
          PluginManagerMain.LOG.error("Plugin is bundled: " + pluginId);
        }
        else {
          boolean needRestart = !DynamicPlugins.isUnloadSafe(pluginDescriptor);
          if (needRestart) {
            StartupActionScriptManager.addActionCommand(new StartupActionScriptManager.DeleteCommand(pluginDescriptor.getPath()));
          }

          PluginStateManager.fireState(pluginDescriptor, false);
          return needRestart;
        }
      }
    }
    return false;
  }

  public static boolean uninstallDynamicPlugin(IdeaPluginDescriptor pluginDescriptor) {
    boolean uninstalledWithoutRestart;
    uninstalledWithoutRestart = DynamicPlugins.unloadPlugin((IdeaPluginDescriptorImpl)pluginDescriptor);
    if (uninstalledWithoutRestart) {
      FileUtil.delete(pluginDescriptor.getPath());
      PluginManagerCore.setPlugins(ArrayUtil.remove(PluginManagerCore.getPlugins(), pluginDescriptor));
    }
    return uninstalledWithoutRestart;
  }

  public static void installAfterRestart(@NotNull File sourceFile,
                                         boolean deleteSourceFile,
                                         @Nullable File existingPlugin,
                                         @NotNull IdeaPluginDescriptor descriptor) throws IOException {
    List<StartupActionScriptManager.ActionCommand> commands = new ArrayList<>();

    if (existingPlugin != null) {
      commands.add(new StartupActionScriptManager.DeleteCommand(existingPlugin));
    }

    String pluginsPath = PathManager.getPluginsPath();
    if (sourceFile.getName().endsWith(".jar")) {
      commands.add(new StartupActionScriptManager.CopyCommand(sourceFile, new File(pluginsPath, sourceFile.getName())));
    }
    else {
      commands.add(new StartupActionScriptManager.DeleteCommand(new File(pluginsPath, rootEntryName(sourceFile))));  // drops stale directory
      commands.add(new StartupActionScriptManager.UnzipCommand(sourceFile, new File(pluginsPath)));
    }

    if (deleteSourceFile) {
      commands.add(new StartupActionScriptManager.DeleteCommand(sourceFile));
    }

    StartupActionScriptManager.addActionCommands(commands);

    PluginStateManager.fireState(descriptor, true);
  }

  @Nullable
  public static File installWithoutRestart(File sourceFile, IdeaPluginDescriptorImpl descriptor, Component parent) {
    Ref<IOException> ref = new Ref<>();
    Ref<File> refTarget = new Ref<>();
    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      String pluginsPath = PathManager.getPluginsPath();
      try {
        File target;
        if (sourceFile.getName().endsWith(".jar")) {
          target = new File(pluginsPath, sourceFile.getName());
          FileUtilRt.copy(sourceFile, target);
        }
        else {
          target = new File(pluginsPath, rootEntryName(sourceFile));
          FileUtil.delete(target);
          new Decompressor.Zip(sourceFile).extract(new File(pluginsPath));
        }
        refTarget.set(target);
      }
      catch (IOException e) {
        ref.set(e);
      }
    }, "Installing Plugin...", false, null, parent instanceof JComponent ? (JComponent)parent : null);
    IOException exception = ref.get();
    if (exception != null) {
      Messages.showErrorDialog(parent, "Plugin installation failed: " + exception.getMessage());
    }
    PluginStateManager.fireState(descriptor, true);
    return exception != null ? null : refTarget.get();
  }

  private static String rootEntryName(File zip) throws IOException {
    try (ZipFile zipFile = new ZipFile(zip)) {
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

  public static void removeStateListener(@NotNull PluginStateListener listener) {
    PluginStateManager.removeStateListener(listener);
  }

  public static boolean install(@NotNull InstalledPluginsTableModel model,
                                @NotNull File file,
                                @NotNull Consumer<? super PluginInstallCallbackData> callback,
                                @Nullable Component parent) {
    try {
      IdeaPluginDescriptorImpl pluginDescriptor = PluginDownloader.loadDescriptionFromJar(file);
      if (pluginDescriptor == null) {
        MessagesEx.showErrorDialog(parent, "Fail to load plugin descriptor from file " + file.getName(), CommonBundle.getErrorTitle());
        return false;
      }

      if (ourState.wasInstalled(pluginDescriptor.getPluginId())) {
        String message = "Plugin '" + pluginDescriptor.getName() + "' was already installed";
        MessagesEx.showWarningDialog(parent, message, "Install Plugin");
        return false;
      }

      if (PluginManagerCore.isIncompatible(pluginDescriptor)) {
        String message = "Plugin '" + pluginDescriptor.getName() + "' is incompatible with this installation";
        MessagesEx.showErrorDialog(parent, message, CommonBundle.getErrorTitle());
        return false;
      }

      File oldFile = null;
      IdeaPluginDescriptor installedPlugin = PluginManagerCore.getPlugin(pluginDescriptor.getPluginId());
      if (installedPlugin != null && !installedPlugin.isBundled()) {
        oldFile = installedPlugin.getPath();
      }

      boolean installWithoutRestart = oldFile == null && DynamicPlugins.isUnloadSafe(pluginDescriptor);
      if (!installWithoutRestart) {
        installAfterRestart(file, false, oldFile, pluginDescriptor);
      }

      ourState.onPluginInstall(pluginDescriptor, installedPlugin != null, !installWithoutRestart);
      checkInstalledPluginDependencies(model, pluginDescriptor, parent);
      callback.consume(new PluginInstallCallbackData(file, pluginDescriptor, !installWithoutRestart,
                                                     installWithoutRestart ? () -> installAndLoadPlugin(file, parent, pluginDescriptor) : () -> {}));
      return true;
    }
    catch (IOException ex) {
      MessagesEx.showErrorDialog(parent, ex.getMessage(), CommonBundle.getErrorTitle());
    }
    return false;
  }

  private static void installAndLoadPlugin(@NotNull File file,
                                           @Nullable Component parent,
                                           IdeaPluginDescriptorImpl pluginDescriptor) {
    File targetFile = installWithoutRestart(file, pluginDescriptor, parent);
    if (targetFile != null) {
      IdeaPluginDescriptorImpl targetDescriptor = PluginManagerCore.loadDescriptor(targetFile, PluginManagerCore.PLUGIN_XML);
      if (targetDescriptor != null) {
        DynamicPlugins.loadPlugin(targetDescriptor);
        PluginManagerCore.setPlugins(ArrayUtil.mergeArrays(PluginManagerCore.getPlugins(), new IdeaPluginDescriptor[] { targetDescriptor }));
      }
    }
  }

  private static void checkInstalledPluginDependencies(@NotNull InstalledPluginsTableModel model,
                                                       @NotNull IdeaPluginDescriptorImpl pluginDescriptor,
                                                       @Nullable Component parent) {
    final Set<PluginId> notInstalled = new HashSet<>();
    final Set<PluginId> disabledIds = new HashSet<>();
    final PluginId[] dependentPluginIds = pluginDescriptor.getDependentPluginIds();
    final PluginId[] optionalDependentPluginIds = pluginDescriptor.getOptionalDependentPluginIds();
    for (PluginId id : dependentPluginIds) {
      if (ArrayUtilRt.find(optionalDependentPluginIds, id) > -1) continue;
      final boolean disabled = model.isDisabled(id);
      final boolean enabled = model.isEnabled(id);
      if (!enabled && !disabled && !PluginManagerCore.isModuleDependency(id)) {
        notInstalled.add(id);
      }
      else if (disabled) {
        disabledIds.add(id);
      }
    }
    if (!notInstalled.isEmpty()) {
      String deps = StringUtil.join(notInstalled, PluginId::toString, ", ");
      String message = "Plugin " + pluginDescriptor.getName() + " depends on unknown plugin" + (notInstalled.size() > 1 ? "s " : " ") + deps;
      MessagesEx.showWarningDialog(parent, message, "Install Plugin");
    }
    if (!disabledIds.isEmpty()) {
      final Set<IdeaPluginDescriptor> dependencies = new HashSet<>();
      for (IdeaPluginDescriptor ideaPluginDescriptor : model.getAllPlugins()) {
        if (disabledIds.contains(ideaPluginDescriptor.getPluginId())) {
          dependencies.add(ideaPluginDescriptor);
        }
      }
      String part = "disabled plugin" + (dependencies.size() > 1 ? "s " : " ");
      String deps = StringUtil.join(dependencies, IdeaPluginDescriptor::getName, ", ");
      String message = "Plugin " + pluginDescriptor.getName() + " depends on " + part + deps + ". Enable " + part.trim() + "?";
      if (Messages.showOkCancelDialog(message, "Install Plugin", "Install", CommonBundle.getCancelButtonText(), Messages.getWarningIcon()) == Messages.OK) {
        model.enableRows(dependencies.toArray(new IdeaPluginDescriptor[0]), Boolean.TRUE);
      }
    }
  }

  static void chooseAndInstall(@NotNull final InstalledPluginsTableModel model,
                               @Nullable final Component parent, @NotNull final Consumer<? super PluginInstallCallbackData> callback) {
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, false, true, true, false, false) {
      @Override
      public boolean isFileSelectable(VirtualFile file) {
        final String extension = file.getExtension();
        return Comparing.strEqual(extension, "jar") || Comparing.strEqual(extension, "zip");
      }
    };
    descriptor.setTitle("Choose Plugin File");
    descriptor.setDescription("JAR and ZIP archives are accepted");
    final String oldPath = PropertiesComponent.getInstance().getValue(PLUGINS_PRESELECTION_PATH);
    final VirtualFile toSelect = oldPath == null ? null : VfsUtil.findFileByIoFile(new File(FileUtil.toSystemDependentName(oldPath)), false);
    FileChooser.chooseFile(descriptor, null, parent, toSelect, virtualFile -> {
      File file = VfsUtilCore.virtualToIoFile(virtualFile);
      PropertiesComponent.getInstance().setValue(PLUGINS_PRESELECTION_PATH, FileUtil.toSystemIndependentName(file.getParent()));
      install(model, file, callback, parent);
    });
  }
}