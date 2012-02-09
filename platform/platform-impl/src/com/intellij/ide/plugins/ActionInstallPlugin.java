/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.plugins;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.net.IOExceptionDialog;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.io.IOException;
import java.util.*;

/**
 * @author lloix
 */
public class ActionInstallPlugin extends AnAction implements DumbAware {
  final private static String updateMessage = IdeBundle.message("action.update.plugin");

  private final PluginManagerMain installed;
  private final PluginManagerMain host;

  public ActionInstallPlugin(PluginManagerMain mgr, PluginManagerMain installed) {
    super(IdeBundle.message("action.download.and.install.plugin"), IdeBundle.message("action.download.and.install.plugin"), IconLoader.getIcon("/actions/install.png"));
    host = mgr;
    this.installed = installed;
  }

  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    IdeaPluginDescriptor[] selection = getPluginTable().getSelectedObjects();
    boolean enabled = (selection != null);

    if (enabled) {
      for (IdeaPluginDescriptor descr : selection) {
        presentation.setText(IdeBundle.message("action.download.and.install.plugin"));
        presentation.setDescription(IdeBundle.message("action.download.and.install.plugin"));
        if (descr instanceof PluginNode) {
          enabled &= !PluginManagerColumnInfo.isDownloaded((PluginNode)descr);
          if (((PluginNode)descr).getStatus() == PluginNode.STATUS_INSTALLED) {
            presentation.setText(updateMessage);
            presentation.setDescription(updateMessage);
            enabled &= InstalledPluginsTableModel.hasNewerVersion(descr.getPluginId());
          }
        }
        else if (descr instanceof IdeaPluginDescriptorImpl) {
          presentation.setText(updateMessage);
          presentation.setDescription(updateMessage);
          PluginId id = descr.getPluginId();
          enabled = enabled && InstalledPluginsTableModel.hasNewerVersion(id);
        }
      }
    }

    presentation.setEnabled(enabled);
  }

  public void actionPerformed(AnActionEvent e) {
    install();
  }

  public void install() {
    IdeaPluginDescriptor[] selection = getPluginTable().getSelectedObjects();

    if (userConfirm(selection)) {
      final ArrayList<PluginNode> list = new ArrayList<PluginNode>();
      for (IdeaPluginDescriptor descr : selection) {
        PluginNode pluginNode = null;
        if (descr instanceof PluginNode) {
          pluginNode = (PluginNode)descr;
        }
        else if (descr instanceof IdeaPluginDescriptorImpl) {
          pluginNode = new PluginNode(descr.getPluginId());
          pluginNode.setName(descr.getName());
          pluginNode.setDepends(Arrays.asList(descr.getDependentPluginIds()), descr.getOptionalDependentPluginIds());
          pluginNode.setSize("-1");
        }

        if (pluginNode != null) {
          list.add(pluginNode);
        }
      }
      try {
        final Runnable onInstallRunnable = new Runnable() {
          @Override
          public void run() {
            installedPluginsToModel(list);
            installed.setRequireShutdown(true);
            if (!installed.isDisposed()) {
              getPluginTable().updateUI();
              final InstalledPluginsTableModel pluginsModel = (InstalledPluginsTableModel)installed.getPluginsModel();
              final Set<IdeaPluginDescriptor> disabled = new HashSet<IdeaPluginDescriptor>();
              final Set<IdeaPluginDescriptor> disabledDependants = new HashSet<IdeaPluginDescriptor>();
              for (PluginNode node : list) {
                final PluginId pluginId = node.getPluginId();
                if (pluginsModel.isDisabled(pluginId)) {
                  disabled.add(node);
                }
                final List<PluginId> depends = node.getDepends();
                if (depends != null) {
                  for (PluginId dependantId : depends) {
                    final IdeaPluginDescriptor pluginDescriptor = PluginManager.getPlugin(dependantId);
                    if (pluginDescriptor != null && pluginsModel.isDisabled(dependantId)) {
                      disabledDependants.add(pluginDescriptor);
                    }
                  }
                }
              }
              suggestToEnableInstalledPlugins(pluginsModel, disabled, disabledDependants, list);
            }
            else {
              notifyPluginsWereInstalled();
            }
          }
        };
        PluginManagerMain.downloadPlugins(list, host.getPluginsModel().view, onInstallRunnable);
      }
      catch (IOException e1) {
        PluginManagerMain.LOG.error(e1);
        IOExceptionDialog
          .showErrorDialog(IdeBundle.message("action.download.and.install.plugin"), IdeBundle.message("error.plugin.download.failed"));
      }
    }
  }

  private static void suggestToEnableInstalledPlugins(final InstalledPluginsTableModel pluginsModel,
                                                      final Set<IdeaPluginDescriptor> disabled,
                                                      final Set<IdeaPluginDescriptor> disabledDependants, 
                                                      final ArrayList<PluginNode> list) {
    if (!disabled.isEmpty() || !disabledDependants.isEmpty()) {
      String message = "";
      if (disabled.size() == 1) {
        message = "Updated plugin '" + disabled.iterator().next().getName() + "' is disabled";
      } else if (!disabled.isEmpty()) {
        message = "Updated plugins " + StringUtil.join(disabled, new Function<IdeaPluginDescriptor, String>() {
          @Override
          public String fun(IdeaPluginDescriptor pluginDescriptor) {
            return pluginDescriptor.getName();
          }
        }, ", ") + " are disabled.";
      }

      if (!disabledDependants.isEmpty()) {
        if (!message.isEmpty()) {
          message += "\n";
        }
        message += "Updated plugin" + (list.size() > 1 ? "s depend " : " depends ") + "on disabled";
        if (disabledDependants.size() == 1) {
          message += " plugin '" + disabledDependants.iterator().next().getName() + "'.";
        } else {
          message += " plugins " + StringUtil.join(disabledDependants, new Function<IdeaPluginDescriptor, String>() {
            @Override
            public String fun(IdeaPluginDescriptor pluginDescriptor) {
              return pluginDescriptor.getName();
            }
          }, ", ") + ".";
        }
      }
      message += "\nWould you like to enable plugins with dependencies?";

      int result;
      if (!disabled.isEmpty() && !disabledDependants.isEmpty()) {
        result =
          Messages.showYesNoCancelDialog(message, CommonBundle.getWarningTitle(), "Enable all",
                                         "Enable updated plugin" + (disabled.size() > 1 ? "s" : ""), CommonBundle.getCancelButtonText(),
                                         Messages.getQuestionIcon());
      } else {
        result = Messages.showOkCancelDialog(message, CommonBundle.getWarningTitle(), "Enable", CommonBundle.getCancelButtonText(), Messages.getQuestionIcon());
      }

      if (result == DialogWrapper.OK_EXIT_CODE) {
        disabled.addAll(disabledDependants);
        pluginsModel.enableRows(disabled.toArray(new IdeaPluginDescriptor[disabled.size()]), true);
      } else if (result == DialogWrapper.CANCEL_EXIT_CODE && !disabled.isEmpty()) {
        pluginsModel.enableRows(disabled.toArray(new IdeaPluginDescriptor[disabled.size()]), true);
      }
    }
  }

  private void installedPluginsToModel(ArrayList<PluginNode> list) {
    for (PluginNode pluginNode : list) {
      final String idString = pluginNode.getPluginId().getIdString();
      final PluginManagerUISettings pluginManagerUISettings = PluginManagerUISettings.getInstance();
      if (!pluginManagerUISettings.myInstalledPlugins.contains(idString)) {
        pluginManagerUISettings.myInstalledPlugins.add(idString);
      }
      pluginManagerUISettings.myOutdatedPlugins.remove(idString);
    }

    final InstalledPluginsTableModel installedPluginsModel = (InstalledPluginsTableModel)installed.getPluginsModel();
    for (PluginNode node : list) {
      installedPluginsModel.appendOrUpdateDescriptor(node);
    }
  }

  private static void notifyPluginsWereInstalled() {
    final ApplicationEx app = ApplicationManagerEx.getApplicationEx();
    final boolean restartCapable = app.isRestartCapable();
    String message = "<html>";
    message += restartCapable ? IdeBundle.message("message.idea.restart.required", ApplicationNamesInfo.getInstance().getFullProductName())
                              : IdeBundle.message("message.idea.shutdown.required", ApplicationNamesInfo.getInstance().getFullProductName());
    message += "<br><a href=";
    message += restartCapable ? "\"restart\">Restart now" : "\"shutdown\">Shutdown";
    message += "</a></html>";
    Notifications.Bus.notify(new Notification(IdeBundle.message("title.plugin.error"), IdeBundle.message("title.plugin.error"),
                                              message, NotificationType.INFORMATION, new NotificationListener() {
      @Override
      public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
        notification.expire();
        if (restartCapable) {
          app.restart();
        }
        else {
          app.exit(true);
        }
      }
    }));
  }


  public PluginTable getPluginTable() {
    return host.getPluginTable();
  }

  //---------------------------------------------------------------------------
  //  Show confirmation message depending on the amount and type of the
  //  selected plugin descriptors: already downloaded plugins need "update"
  //  while non-installed yet need "install".
  //---------------------------------------------------------------------------
  private boolean userConfirm(IdeaPluginDescriptor[] selection) {
    String message;
    if (selection.length == 1) {
      if (selection[0] instanceof IdeaPluginDescriptorImpl) {
        message = IdeBundle.message("prompt.update.plugin", selection[0].getName());
      }
      else {
        message = IdeBundle.message("prompt.download.and.install.plugin", selection[0].getName());
      }
    }
    else {
      message = IdeBundle.message("prompt.install.several.plugins", selection.length);
    }

    return Messages.showYesNoDialog(host.getMainPanel(), message, IdeBundle.message("action.download.and.install.plugin"), Messages.getQuestionIcon()) == 0;
  }
}
