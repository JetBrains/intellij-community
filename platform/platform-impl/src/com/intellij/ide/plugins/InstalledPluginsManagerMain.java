/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: anna
 */
public class InstalledPluginsManagerMain extends PluginManagerMain {

  public InstalledPluginsManagerMain(PluginManagerUISettings uiSettings) {
    super(uiSettings);
    init();
    myActionsPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
    final JButton button = new JButton("Browse repositories...");
    button.setMnemonic('b');
    button.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final PluginManagerConfigurable configurable = createAvailableConfigurable();
        new SingleConfigurableEditor(myActionsPanel, configurable, ShowSettingsUtilImpl.createDimensionKey(configurable), false).show();
      }
    });
    myActionsPanel.add(button);

    final JButton installPluginFromFileSystem = new JButton("Install plugin from disk...");
    installPluginFromFileSystem.setMnemonic('d');
    installPluginFromFileSystem.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, true, true, false, false){
          @Override
          public boolean isFileSelectable(VirtualFile file) {
            final String extension = file.getExtension();
            return Comparing.strEqual(extension, "jar") || Comparing.strEqual(extension, "zip");
          }
        };
        descriptor.setTitle("Choose Plugin File");
        descriptor.setDescription("JAR and ZIP archives are accepted");
        final VirtualFile virtualFile = FileChooser.chooseFile(myActionsPanel, descriptor);
        if (virtualFile != null) {
          final File file = VfsUtil.virtualToIoFile(virtualFile);
          try {
            PluginDownloader.install(file, file.getName());
            setRequireShutdown(true);
          }
          catch (IOException ex) {
            Messages.showErrorDialog(ex.getMessage(), CommonBundle.getErrorTitle());
          }
        }
      }
    });
    myActionsPanel.add(installPluginFromFileSystem);
  }

  @Override
  protected void propagateUpdates(ArrayList<IdeaPluginDescriptor> list) {
  }

  private PluginManagerConfigurable createAvailableConfigurable() {
    return new PluginManagerConfigurable(PluginManagerUISettings.getInstance(), true) {
      @Override
      protected PluginManagerMain createPanel() {
        return new AvailablePluginsManagerMain(InstalledPluginsManagerMain.this, myUISettings);
      }

      @Override
      public String getDisplayName() {
        return "Browse Repositories";
      }
    };
  }

  protected JScrollPane createTable() {
    pluginsModel = new InstalledPluginsTableModel();
    pluginTable = new PluginTable(pluginsModel);
    pluginTable.setTableHeader(null);

    JScrollPane installedScrollPane = ScrollPaneFactory.createScrollPane(pluginTable);
    pluginTable.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final int column = InstalledPluginsTableModel.getCheckboxColumn();
        final int[] selectedRows = pluginTable.getSelectedRows();
        boolean currentlyMarked = true;
        for (final int selectedRow : selectedRows) {
          if (selectedRow < 0 || !pluginTable.isCellEditable(selectedRow, column)) {
            return;
          }
          final Boolean enabled = (Boolean)pluginTable.getValueAt(selectedRow, column);
          currentlyMarked &= enabled == null || enabled.booleanValue();
        }
        final IdeaPluginDescriptor[] selected = new IdeaPluginDescriptor[selectedRows.length];
        for (int i = 0, selectedLength = selected.length; i < selectedLength; i++) {
          selected[i] = pluginsModel.getObjectAt(pluginTable.convertRowIndexToModel(selectedRows[i]));
        }
        ((InstalledPluginsTableModel)pluginsModel).enableRows(selected, currentlyMarked ? Boolean.FALSE : Boolean.TRUE);
        pluginTable.repaint();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), JComponent.WHEN_FOCUSED);
    return installedScrollPane;
  }

  @Override
  protected ActionGroup getActionGroup(boolean inToolbar) {
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new RefreshAction());
    actionGroup.add(new ActionInstallPlugin(this, this));
    actionGroup.add(new ActionUninstallPlugin(this, pluginTable));
    if (inToolbar) {
      actionGroup.add(new MyFilterEnabledAction());
      //actionGroup.add(new MyFilterBundleAction());
    }
    return actionGroup;
  }

  @Override
  public boolean isModified() {
    final boolean modified = super.isModified();
    if (modified) return true;
    for (int i = 0; i < pluginsModel.getRowCount(); i++) {
      final IdeaPluginDescriptorImpl pluginDescriptor = (IdeaPluginDescriptorImpl)pluginsModel.getObjectAt(i);
      if (pluginDescriptor.isEnabled() != ((InstalledPluginsTableModel)pluginsModel).isEnabled(pluginDescriptor.getPluginId())) {
        return true;
      }
    }
    for (IdeaPluginDescriptor descriptor : pluginsModel.filtered) {
      if (((IdeaPluginDescriptorImpl)descriptor).isEnabled() !=
          ((InstalledPluginsTableModel)pluginsModel).isEnabled(descriptor.getPluginId())) {
        return true;
      }
    }
    final List<String> disabledPlugins = PluginManager.getDisabledPlugins();
    for (Map.Entry<PluginId, Boolean> entry : ((InstalledPluginsTableModel)pluginsModel).getEnabledMap().entrySet()) {
      final Boolean enabled = entry.getValue();
      if (enabled != null && !enabled.booleanValue() && !disabledPlugins.contains(entry.getKey().toString())) {
        return true;
      }
    }

    return false;
  }

  @Override
  public String apply() {
    final String apply = super.apply();
    if (apply != null) return apply;
    for (int i = 0; i < pluginTable.getRowCount(); i++) {
      final IdeaPluginDescriptorImpl pluginDescriptor = (IdeaPluginDescriptorImpl)pluginsModel.getObjectAt(i);
      final Boolean enabled = (Boolean)pluginsModel.getValueAt(i, InstalledPluginsTableModel.getCheckboxColumn());
      pluginDescriptor.setEnabled(enabled != null && enabled.booleanValue());
    }
    for (IdeaPluginDescriptor descriptor : pluginsModel.filtered) {
      ((IdeaPluginDescriptorImpl)descriptor).setEnabled(
        ((InstalledPluginsTableModel)pluginsModel).isEnabled(descriptor.getPluginId()));
    }
    try {
      final ArrayList<String> ids = new ArrayList<String>();
      for (Map.Entry<PluginId, Boolean> entry : ((InstalledPluginsTableModel)pluginsModel).getEnabledMap().entrySet()) {
        final Boolean value = entry.getValue();
        if (value != null && !value.booleanValue()) {
          ids.add(entry.getKey().getIdString());
        }
      }
      PluginManager.saveDisabledPlugins(ids, false);
    }
    catch (IOException e) {
      LOG.error(e);
    }

    return null;
  }

  @Override
  protected String canApply() {
    final Map<PluginId, Set<PluginId>> dependentToRequiredListMap =
      ((InstalledPluginsTableModel)pluginsModel).getDependentToRequiredListMap();
    if (!dependentToRequiredListMap.isEmpty()) {
      final StringBuffer sb = new StringBuffer("<html><body style=\"padding: 5px;\">Unable to apply changes: plugin")
        .append(dependentToRequiredListMap.size() == 1 ? " " : "s ");
      sb.append(StringUtil.join(dependentToRequiredListMap.keySet(), new Function<PluginId, String>() {
        public String fun(final PluginId pluginId) {
          final IdeaPluginDescriptor ideaPluginDescriptor = PluginManager.getPlugin(pluginId);
          return "\"" + (ideaPluginDescriptor != null ? ideaPluginDescriptor.getName() : pluginId.getIdString()) + "\"";
        }
      }, ", "));
      sb.append(" won't be able to load.</body></html>");
      return sb.toString();
    }
    return super.canApply();
  }

  private class MyFilterEnabledAction extends ComboBoxAction implements DumbAware {

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setText("Show: " + ((InstalledPluginsTableModel)pluginsModel).getEnabledFilter());
    }

    @NotNull
    @Override
    protected DefaultActionGroup createPopupActionGroup(JComponent button) {
      final DefaultActionGroup gr = new DefaultActionGroup();
      for (final String enabledValue : InstalledPluginsTableModel.ENABLED_VALUES) {
        gr.add(new AnAction(enabledValue) {
          @Override
          public void actionPerformed(AnActionEvent e) {
            final String filter = myFilter.getFilter().toLowerCase();
            ((InstalledPluginsTableModel)pluginsModel).setEnabledFilter(enabledValue, filter);
          }
        });
      }
      return gr;
    }
  }

  private class MyFilterBundleAction extends ComboBoxAction implements DumbAware {
    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setVisible(((InstalledPluginsTableModel)pluginsModel).isBundledEnabled());
      e.getPresentation().setText("Bundled: " + ((InstalledPluginsTableModel)pluginsModel).getBundledFilter());
    }

    @NotNull
    @Override
    protected DefaultActionGroup createPopupActionGroup(JComponent button) {
      final DefaultActionGroup gr = new DefaultActionGroup();
      for (final String bundledValue : InstalledPluginsTableModel.BUNDLED_VALUES) {
        gr.add(new AnAction(bundledValue) {
          @Override
          public void actionPerformed(AnActionEvent e) {
            final String filter = myFilter.getFilter().toLowerCase();
            ((InstalledPluginsTableModel)pluginsModel).setBundledFilter(bundledValue, filter);
          }
        });
      }
      return gr;
    }
  }
}
