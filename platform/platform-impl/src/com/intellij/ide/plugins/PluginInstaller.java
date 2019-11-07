// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.CommonBundle;
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
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Consumer;
import com.intellij.util.io.Decompressor;
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
public final class PluginInstaller {
  private static final Logger LOG = Logger.getInstance(PluginInstaller.class);

  public static final String UNKNOWN_HOST_MARKER = "__unknown_repository__";

  static final Object ourLock = new Object();
  private static final String PLUGINS_PRESELECTION_PATH = "plugins.preselection.path";

  private PluginInstaller() { }

  public static boolean prepareToInstall(List<PluginNode> pluginsToInstall,
                                         List<? extends IdeaPluginDescriptor> allPlugins,
                                         boolean allowInstallWithoutRestart,
                                         PluginManagerMain.PluginEnabler pluginEnabler,
                                         Runnable onSuccess,
                                         @NotNull ProgressIndicator indicator) {
    PluginInstallOperation operation = new PluginInstallOperation(pluginsToInstall, allPlugins, pluginEnabler, indicator);
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
          PluginManagerMain.LOG.error("Plugin is bundled: " + pluginDescriptor.getPluginId());
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
    StartupActionScriptManager.addActionCommand(new StartupActionScriptManager.DeleteCommand(pluginDescriptor.getPath()));
  }

  public static boolean uninstallDynamicPlugin(IdeaPluginDescriptor pluginDescriptor) {
    boolean uninstalledWithoutRestart = DynamicPlugins.unloadPlugin((IdeaPluginDescriptorImpl)pluginDescriptor);
    if (uninstalledWithoutRestart) {
      FileUtil.delete(pluginDescriptor.getPath());
    }
    else {
      try {
        uninstallAfterRestart(pluginDescriptor);
      }
      catch (IOException e) {
        LOG.error(e);
      }
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

  public static boolean install(@NotNull InstalledPluginsTableModel model,
                                @NotNull File file,
                                @NotNull Consumer<? super PluginInstallCallbackData> callback,
                                @Nullable Component parent) {
    try {
      IdeaPluginDescriptorImpl pluginDescriptor = PluginDownloader.loadDescriptionFromJar(file.toPath());
      if (pluginDescriptor == null) {
        MessagesEx.showErrorDialog(parent, "Fail to load plugin descriptor from file " + file.getName(), CommonBundle.getErrorTitle());
        return false;
      }

      InstalledPluginsState ourState = InstalledPluginsState.getInstance();

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

      IdeaPluginDescriptor installedPlugin = PluginManagerCore.getPlugin(pluginDescriptor.getPluginId());
      if (installedPlugin != null && ApplicationInfoEx.getInstanceEx().isEssentialPlugin(installedPlugin.getPluginId())) {
        String message = "Plugin '" + pluginDescriptor.getName() + "' is a core part of " + ApplicationNamesInfo.getInstance().getFullProductName()
                         + ". In order to update it to a newer version you should update the IDE.";
        MessagesEx.showErrorDialog(parent, message, CommonBundle.getErrorTitle());
        return false;
      }

      File oldFile = null;
      if (installedPlugin != null && !installedPlugin.isBundled()) {
        oldFile = installedPlugin.getPath();
      }

      boolean installWithoutRestart = oldFile == null && DynamicPlugins.allowLoadUnloadWithoutRestart(pluginDescriptor);
      if (!installWithoutRestart) {
        installAfterRestart(file, false, oldFile, pluginDescriptor);
      }

      ourState.onPluginInstall(pluginDescriptor, installedPlugin != null, !installWithoutRestart);
      checkInstalledPluginDependencies(model, pluginDescriptor, parent);
      callback.consume(new PluginInstallCallbackData(file, pluginDescriptor, !installWithoutRestart));
      return true;
    }
    catch (IOException ex) {
      MessagesEx.showErrorDialog(parent, ex.getMessage(), CommonBundle.getErrorTitle());
    }
    return false;
  }

  @Nullable
  public static IdeaPluginDescriptorImpl installAndLoadDynamicPlugin(@NotNull File file,
                                                                     @Nullable Component parent,
                                                                     IdeaPluginDescriptorImpl pluginDescriptor) {
    File targetFile = installWithoutRestart(file, pluginDescriptor, parent);
    if (targetFile != null) {
      IdeaPluginDescriptorImpl targetDescriptor = PluginManager.loadDescriptor(targetFile.toPath(), PluginManagerCore.PLUGIN_XML);
      if (targetDescriptor != null) {
        DynamicPlugins.loadPlugin(targetDescriptor);
        return targetDescriptor;
      }
    }
    return null;
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
      String message =
        "Plugin " + pluginDescriptor.getName() + " depends on unknown plugin" + (notInstalled.size() > 1 ? "s " : " ") + deps;
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
      if (Messages
            .showOkCancelDialog(message, "Install Plugin", "Install", CommonBundle.getCancelButtonText(), Messages.getWarningIcon()) ==
          Messages.OK) {
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
    final VirtualFile toSelect =
      oldPath == null ? null : VfsUtil.findFileByIoFile(new File(FileUtil.toSystemDependentName(oldPath)), false);
    FileChooser.chooseFile(descriptor, null, parent, toSelect, virtualFile -> {
      File file = VfsUtilCore.virtualToIoFile(virtualFile);
      PropertiesComponent.getInstance().setValue(PLUGINS_PRESELECTION_PATH, FileUtil.toSystemIndependentName(file.getParent()));
      install(model, file, callback, parent);
    });
  }
}