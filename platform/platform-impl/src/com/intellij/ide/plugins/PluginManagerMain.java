// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.CommonBundle;
import com.intellij.core.CoreBundle;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector;
import com.intellij.ide.plugins.marketplace.statistics.enums.DialogAcceptanceResultEnum;
import com.intellij.idea.Main;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLFrameHyperlinkEvent;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class PluginManagerMain {
  private PluginManagerMain() { }

  /**
   * @deprecated Please migrate to either {@link #downloadPluginsAndCleanup(List, Collection, Runnable, com.intellij.ide.plugins.PluginEnabler, ModalityState, Runnable)}
   * or {@link #downloadPlugins(List, Collection, boolean, Runnable, com.intellij.ide.plugins.PluginEnabler, Consumer)}.
   */
  @Deprecated(since = "2020.2", forRemoval = true)
  public static boolean downloadPlugins(@NotNull List<PluginNode> plugins,
                                        @NotNull List<? extends IdeaPluginDescriptor> customPlugins,
                                        @Nullable Runnable onSuccess,
                                        @NotNull PluginEnabler pluginEnabler,
                                        @Nullable Runnable cleanup) throws IOException {
    return downloadPluginsAndCleanup(plugins, ContainerUtil.filterIsInstance(customPlugins, PluginNode.class), onSuccess, pluginEnabler, ModalityState.any(), cleanup);
  }

  public static boolean downloadPluginsAndCleanup(@NotNull List<PluginNode> plugins,
                                                  @NotNull Collection<PluginNode> customPlugins,
                                                  @Nullable Runnable onSuccess,
                                                  @NotNull com.intellij.ide.plugins.PluginEnabler pluginEnabler,
                                                  @NotNull ModalityState modalityState,
                                                  @Nullable Runnable cleanup) throws IOException {
    return downloadPlugins(plugins, customPlugins, false, onSuccess, pluginEnabler, modalityState, cleanup != null ? __ -> cleanup.run() : null);
  }

  /**
   * @deprecated Please use the overload with explicitly passed modality state
   */
  @Deprecated
  public static boolean downloadPlugins(@NotNull List<PluginNode> plugins,
                                        @NotNull Collection<PluginNode> customPlugins,
                                        boolean allowInstallWithoutRestart,
                                        @Nullable Runnable onSuccess,
                                        @NotNull com.intellij.ide.plugins.PluginEnabler pluginEnabler,
                                        @Nullable Consumer<? super Boolean> function) throws IOException {
    return downloadPlugins(plugins, customPlugins, allowInstallWithoutRestart, onSuccess, pluginEnabler, ModalityState.any(), function);
  }

  public static boolean downloadPlugins(
    @NotNull List<PluginNode> plugins,
    @NotNull Collection<PluginNode> customPlugins,
    boolean allowInstallWithoutRestart,
    @Nullable Runnable onSuccess,
    @NotNull com.intellij.ide.plugins.PluginEnabler pluginEnabler,
    @NotNull final ModalityState modalityState,
    @Nullable Consumer<? super Boolean> function) throws IOException {
    try {
      boolean[] result = new boolean[1];
      ProgressManager.getInstance().run(new Task.Backgroundable(null, IdeBundle.message("progress.download.plugins"), true, PluginManagerUISettings.getInstance()) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            //TODO: `PluginInstallOperation` expects only `customPlugins`, but it can take `allPlugins` too
            PluginInstallOperation operation = new PluginInstallOperation(plugins, customPlugins, pluginEnabler, indicator);
            operation.setAllowInstallWithoutRestart(allowInstallWithoutRestart);
            operation.run();

            boolean success = operation.isSuccess();
            result[0] = success;
            if (success) {
              ApplicationManager.getApplication().invokeLater(() -> {
                if (allowInstallWithoutRestart) {
                  for (PendingDynamicPluginInstall install : operation.getPendingDynamicPluginInstalls()) {
                    PluginInstaller.installAndLoadDynamicPlugin(install.getFile(), install.getPluginDescriptor());
                  }
                }
                if (onSuccess != null) {
                  onSuccess.run();
                }
              }, modalityState);
            }
          }
          finally {
            if (function != null) {
              ApplicationManager.getApplication().invokeLater(() -> function.accept(result[0]), ModalityState.any());
            }
          }
        }
      });
      return result[0];
    }
    catch (RuntimeException e) {
      Throwable cause = e.getCause();
      if (cause instanceof IOException) {
        throw (IOException)cause;
      }
      else {
        throw e;
      }
    }
  }

  public static class MyHyperlinkListener extends HyperlinkAdapter {
    @Override
    protected void hyperlinkActivated(HyperlinkEvent e) {
      JEditorPane pane = (JEditorPane)e.getSource();
      if (e instanceof HTMLFrameHyperlinkEvent) {
        HTMLDocument doc = (HTMLDocument)pane.getDocument();
        doc.processHTMLFrameHyperlinkEvent((HTMLFrameHyperlinkEvent)e);
      }
      else {
        URL url = e.getURL();
        if (url != null) {
          BrowserUtil.browse(url);
        }
      }
    }
  }

  public static boolean suggestToEnableInstalledDependantPlugins(@NotNull com.intellij.ide.plugins.PluginEnabler pluginEnabler,
                                                                 @NotNull List<? extends IdeaPluginDescriptor> list) {
    Set<IdeaPluginDescriptor> disabled = new HashSet<>();
    Set<IdeaPluginDescriptor> disabledDependants = new HashSet<>();
    for (IdeaPluginDescriptor node : list) {
      PluginId pluginId = node.getPluginId();
      if (pluginEnabler.isDisabled(pluginId)) {
        disabled.add(node);
      }
      for (IdeaPluginDependency dependency : node.getDependencies()) {
        if (dependency.isOptional()) {
          continue;
        }

        PluginId dependantId = dependency.getPluginId();
        // If there is no installed plugin implementing the module, then it can only be a platform module which cannot be disabled
        if (PluginManagerCore.isModuleDependency(dependantId) &&
            PluginManagerCore.findPluginByModuleDependency(dependantId) == null) {
          continue;
        }

        IdeaPluginDescriptor pluginDescriptor = PluginManagerCore.getPlugin(dependantId);
        if (pluginDescriptor != null && pluginEnabler.isDisabled(dependantId)) {
          disabledDependants.add(pluginDescriptor);
        }
      }
    }

    if (!disabled.isEmpty() || !disabledDependants.isEmpty()) {
      String message = "";
      if (disabled.size() == 1) {
        message += IdeBundle.message("plugin.manager.main.suggest.to.enable.message.part1", disabled.iterator().next().getName());
      }
      else if (!disabled.isEmpty()) {
        message += IdeBundle.message("plugin.manager.main.suggest.to.enable.message.part2", StringUtil.join(disabled, pluginDescriptor -> pluginDescriptor.getName(), ", "));
      }

      if (!disabledDependants.isEmpty()) {
        message += "<br>";
        message += IdeBundle.message("plugin.manager.main.suggest.to.enable.message.part3", list.size());
        message += " ";
        if (disabledDependants.size() == 1) {
          message += IdeBundle.message("plugin.manager.main.suggest.to.enable.message.part4", disabledDependants.iterator().next().getName());
        }
        else {
          message += IdeBundle.message("plugin.manager.main.suggest.to.enable.message.part5", StringUtil.join(disabledDependants, pluginDescriptor -> pluginDescriptor.getName(), ", "));
        }
      }
      message += " ";
      message += IdeBundle.message(disabled.isEmpty() ? "plugin.manager.main.suggest.to.enable.message.part6" : "plugin.manager.main.suggest.to.enable.message.part7");

      boolean result;
      if (!disabled.isEmpty() && !disabledDependants.isEmpty()) {
        int code =
          MessageDialogBuilder.yesNoCancel(IdeBundle.message("dialog.title.dependent.plugins.found"), XmlStringUtil.wrapInHtml(message))
            .yesText(IdeBundle.message("button.enable.all"))
            .noText(IdeBundle.message("button.enable.updated.plugins", disabled.size()))
            .guessWindowAndAsk();
        if (code == Messages.CANCEL) {
          return false;
        }
        result = code == Messages.YES;
      }
      else {
        message += "<br>";
        if (!disabled.isEmpty()) {
          message += IdeBundle.message("plugin.manager.main.suggest.to.enable.message.part8", disabled.size());
        }
        else {
          message += IdeBundle.message("plugin.manager.main.suggest.to.enable.message.part9", disabledDependants.size());
        }
        message += "?";
        result = MessageDialogBuilder.yesNo(IdeBundle.message("dialog.title.dependent.plugins.found"), XmlStringUtil.wrapInHtml(message)).guessWindowAndAsk();
        if (!result) {
          return false;
        }
      }

      if (result) {
        disabled.addAll(disabledDependants);
        pluginEnabler.enable(disabled);
      }
      else if (!disabled.isEmpty()) {
        pluginEnabler.enable(disabled);
      }
      return true;
    }

    return false;
  }

  /** @deprecated Please use {@link com.intellij.ide.plugins.PluginEnabler} directly. */
  @Deprecated
  public interface PluginEnabler extends com.intellij.ide.plugins.PluginEnabler {
    @Override
    default boolean isDisabled(@NotNull PluginId pluginId) {
      return HEADLESS.isDisabled(pluginId);
    }

    @Override
    default boolean enableById(@NotNull Set<PluginId> pluginIds) {
      return HEADLESS.enableById(pluginIds);
    }

    @Override
    default boolean enable(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors) {
      return HEADLESS.enable(descriptors);
    }

    @Override
    default boolean disableById(@NotNull Set<PluginId> pluginIds) {
      return HEADLESS.disableById(pluginIds);
    }

    @Override
    default boolean disable(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors) {
      return HEADLESS.disable(descriptors);
    }

    final class HEADLESS implements PluginEnabler { }
  }

  @ApiStatus.Internal
  public static void onEvent(String description) {
    switch (description) {
      case PluginManagerCore.DISABLE:
        PluginManagerCore.onEnable(false);
        break;
      case PluginManagerCore.ENABLE:
        if (PluginManagerCore.onEnable(true)) {
          notifyPluginsUpdated(null);
        }
        break;
      case PluginManagerCore.EDIT:
        IdeFrame frame = WindowManagerEx.getInstanceEx().findFrameFor(null);
        PluginManagerConfigurable.showPluginConfigurable(frame != null ? frame.getComponent() : null, null, List.of());
        break;
    }
  }

  public static void notifyPluginsUpdated(@Nullable Project project) {
    ApplicationEx app = ApplicationManagerEx.getApplicationEx();
    String title = IdeBundle.message("updates.notification.title", ApplicationNamesInfo.getInstance().getFullProductName());
    String action = IdeBundle.message("ide.restart.required.notification", app.isRestartCapable() ? 1 : 0);
    UpdateChecker.getNotificationGroupForPluginUpdateResults()
      .createNotification(title, NotificationType.INFORMATION)
      .setDisplayId("plugins.updated.suggest.restart")
      .addAction(new NotificationAction(action) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
          if (PluginManagerConfigurable.showRestartDialog() == Messages.YES) {
            notification.expire();
            ApplicationManagerEx.getApplicationEx().restart(true);
          }
        }
      })
      .notify(project);
  }

  public static boolean checkThirdPartyPluginsAllowed(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors) {
    @SuppressWarnings("SSBasedInspection") Collection<? extends IdeaPluginDescriptor> aliens = descriptors.stream()
      .filter(descriptor -> !(descriptor.isBundled() || PluginManagerCore.isDevelopedByJetBrains(descriptor)))
      .collect(Collectors.toList());
    if (aliens.isEmpty()) return true;

    UpdateSettings updateSettings = UpdateSettings.getInstance();
    if (updateSettings.isThirdPartyPluginsAllowed()) {
      PluginManagerUsageCollector.thirdPartyAcceptanceCheck(DialogAcceptanceResultEnum.AUTO_ACCEPTED);
      return true;
    }

    if (Main.isHeadless()) {
      // postponing the dialog till the next start
      PluginManagerCore.write3rdPartyPlugins(aliens);
      return true;
    }

    String title = CoreBundle.message("third.party.plugins.privacy.note.title");
    String pluginList = aliens.stream()
      .map(descriptor -> "&nbsp;&nbsp;&nbsp;" + descriptor.getName() + " (" + descriptor.getVendor() + ')')
      .collect(Collectors.joining("<br>"));
    String message = CoreBundle.message("third.party.plugins.privacy.note.text", pluginList);
    String yesText = CoreBundle.message("third.party.plugins.privacy.note.accept"), noText = CommonBundle.getCancelButtonText();
    if (Messages.showYesNoDialog(message, title, yesText, noText, Messages.getWarningIcon()) == Messages.YES) {
      updateSettings.setThirdPartyPluginsAllowed(true);
      PluginManagerUsageCollector.thirdPartyAcceptanceCheck(DialogAcceptanceResultEnum.ACCEPTED);
      return true;
    }
    else {
      PluginManagerUsageCollector.thirdPartyAcceptanceCheck(DialogAcceptanceResultEnum.DECLINED);
      return false;
    }
  }

  @ApiStatus.Internal
  public static void checkThirdPartyPluginsAllowed() {
    Boolean noteAccepted = PluginManagerCore.isThirdPartyPluginsNoteAccepted();
    if (noteAccepted == Boolean.TRUE) {
      UpdateSettings.getInstance().setThirdPartyPluginsAllowed(true);
      PluginManagerUsageCollector.thirdPartyAcceptanceCheck(DialogAcceptanceResultEnum.ACCEPTED);
    }
    else if (noteAccepted == Boolean.FALSE) {
      PluginManagerUsageCollector.thirdPartyAcceptanceCheck(DialogAcceptanceResultEnum.DECLINED);
    }
  }
}
