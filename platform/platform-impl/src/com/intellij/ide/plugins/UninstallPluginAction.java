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
package com.intellij.ide.plugins;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;

import java.io.IOException;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class UninstallPluginAction extends AnAction implements DumbAware {
  private final PluginTable pluginTable;
  private final PluginManagerMain host;

  public UninstallPluginAction(PluginManagerMain mgr, PluginTable table) {
    super(IdeBundle.message("action.uninstall.plugin"), IdeBundle.message("action.uninstall.plugin"), AllIcons.Actions.Uninstall);

    pluginTable = table;
    host = mgr;
  }

  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    if (!pluginTable.isShowing()) {
      presentation.setEnabled(false);
      return;
    }
    IdeaPluginDescriptor[] selection = pluginTable.getSelectedObjects();
    boolean enabled = (selection != null);

    if (enabled) {
      for (IdeaPluginDescriptor descriptor : selection) {
        if (descriptor instanceof IdeaPluginDescriptorImpl) {
          final IdeaPluginDescriptorImpl ideaPluginDescriptor = (IdeaPluginDescriptorImpl)descriptor;
          if (ideaPluginDescriptor.isDeleted() || ideaPluginDescriptor.isBundled()) {
            enabled = false;
            break;
          }
        }
        if (descriptor instanceof PluginNode) {
          enabled = false;
          break;
        }
      }
    }
    presentation.setEnabled(enabled);
  }

  public void actionPerformed(AnActionEvent e) {
    uninstall(host, false, pluginTable.getSelectedObjects());
    pluginTable.updateUI();
  }

  public static void uninstall(PluginManagerMain host, boolean confirmed, IdeaPluginDescriptor... selection) {
    String message;

    if (selection.length == 1) {
      message = IdeBundle.message("prompt.uninstall.plugin", selection[0].getName());
    }
    else {
      message = IdeBundle.message("prompt.uninstall.several.plugins", selection.length);
    }

    if (!confirmed && Messages.showYesNoDialog(host.getMainPanel(), message, IdeBundle.message("title.plugin.uninstall"), Messages.getQuestionIcon()) != Messages.YES) return;

    for (IdeaPluginDescriptor descriptor : selection) {
      IdeaPluginDescriptorImpl pluginDescriptor = (IdeaPluginDescriptorImpl)descriptor;

      boolean actualDelete = true;

      //  Get the list of plugins which depend on this one. If this list is
      //  not empty - issue warning instead of simple prompt.
      List<IdeaPluginDescriptorImpl> dependant = host.getDependentList(pluginDescriptor);
      if (dependant.size() > 0) {
        message = IdeBundle.message("several.plugins.depend.on.0.continue.to.remove", pluginDescriptor.getName());
        actualDelete = (Messages.showYesNoDialog(host.getMainPanel(), message, IdeBundle.message("title.plugin.uninstall"), Messages.getQuestionIcon()) == Messages.YES);
      }

      if (actualDelete) {
        uninstallPlugin(pluginDescriptor, host);
      }
    }
  }

  private static void uninstallPlugin(IdeaPluginDescriptorImpl descriptor, PluginManagerMain host) {
    PluginId pluginId = descriptor.getPluginId();
    descriptor.setDeleted(true);

    try {
      PluginInstaller.prepareToUninstall(pluginId);
      host.setRequireShutdown(descriptor.isEnabled());
    }
    catch (IOException e1) {
      PluginManagerMain.LOG.error(e1);
    }
  }
}
