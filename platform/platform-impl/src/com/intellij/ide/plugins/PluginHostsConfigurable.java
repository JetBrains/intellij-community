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

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NonEmptyInputValidator;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.ui.ListUtil;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PluginHostsConfigurable extends BaseConfigurable implements SearchableConfigurable {
  private CustomPluginRepositoriesPanel myUpdatesSettingsPanel;

  public JComponent createComponent() {
    myUpdatesSettingsPanel = new CustomPluginRepositoriesPanel();
    return myUpdatesSettingsPanel.myPanel;
  }

  public String getDisplayName() {
    return "Custom Plugin Repositories";
  }

  public String getHelpTopic() {
    return null;
  }

  public Icon getIcon() {
    return null;
  }

  public void apply() throws ConfigurationException {
    UpdateSettings settings = UpdateSettings.getInstance();
    
    settings.myPluginHosts.clear();
    settings.myPluginHosts.addAll(myUpdatesSettingsPanel.getPluginsHosts());
    
  }

  public void reset() {
    myUpdatesSettingsPanel.setPluginHosts(UpdateSettings.getInstance().myPluginHosts);
  }

  public boolean isModified() {
    if (myUpdatesSettingsPanel == null) return false;
    UpdateSettings settings = UpdateSettings.getInstance();
    return !settings.myPluginHosts.equals(myUpdatesSettingsPanel.getPluginsHosts());
  }

  public void disposeUIResources() {
    myUpdatesSettingsPanel = null;
  }

  @NotNull
  public String getId() {
    return "custom.repositories";
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }

  public Collection<? extends String> getPluginsHosts() {
    return myUpdatesSettingsPanel.getPluginsHosts();
  }

  public static class CustomPluginRepositoriesPanel {
  
    private JButton myAddButton;
    private JButton myDeleteButton;
    private JBList myUrlsList;
    private JButton myEditButton;
    private JPanel myPanel;

    public CustomPluginRepositoriesPanel() {

      myUrlsList.getEmptyText().setText(IdeBundle.message("update.no.update.hosts"));
      myUrlsList.setModel(new DefaultListModel());
      myUrlsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

      myUrlsList.addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(final ListSelectionEvent e) {
          myDeleteButton.setEnabled(ListUtil.canRemoveSelectedItems(myUrlsList));
          myEditButton.setEnabled(ListUtil.canRemoveSelectedItems(myUrlsList));
        }
      });

      myAddButton.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          final HostMessages.InputHostDialog dlg = new HostMessages.InputHostDialog(myPanel,
                                                                                    IdeBundle.message("update.plugin.host.url.message"),
                                                                                    IdeBundle.message("update.add.new.plugin.host.title"),
                                                                                    Messages.getQuestionIcon(), "",
                                                                                    new NonEmptyInputValidator());
          dlg.show();
          final String input = dlg.getInputString();
          if (input != null) {
            ((DefaultListModel)myUrlsList.getModel()).addElement(input);
          }
        }
      });

      myEditButton.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          final HostMessages.InputHostDialog dlg = new HostMessages.InputHostDialog(myPanel,
                                                                                    IdeBundle.message("update.plugin.host.url.message"),
                                                                                    IdeBundle.message("update.edit.plugin.host.title"),
                                                                                    Messages.getQuestionIcon(),
                                                                                    (String)myUrlsList.getSelectedValue(),
                                                                                    new InputValidator() {
                                                                                      public boolean checkInput(final String inputString) {
                                                                                        return inputString.length() > 0;
                                                                                      }

                                                                                      public boolean canClose(final String inputString) {
                                                                                        return checkInput(inputString);
                                                                                      }
                                                                                    });
          dlg.show();
          final String input = dlg.getInputString();
          if (input != null) {
            ((DefaultListModel)myUrlsList.getModel()).set(myUrlsList.getSelectedIndex(), input);
          }
        }
      });

      myDeleteButton.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          ListUtil.removeSelectedItems(myUrlsList);
        }
      });
      myEditButton.setEnabled(false);
      myDeleteButton.setEnabled(false);


    }

    public List<String> getPluginsHosts() {
      final List<String> result = new ArrayList<String>();
      for (int i = 0; i < myUrlsList.getModel().getSize(); i++) {
        result.add((String)myUrlsList.getModel().getElementAt(i));
      }
      return result;
    }

    public void setPluginHosts(final List<String> pluginHosts) {
      final DefaultListModel model = (DefaultListModel)myUrlsList.getModel();
      model.clear();
      for (String host : pluginHosts) {
        model.addElement(host);
      }
    }
  }

  public static class HostMessages extends Messages {
    public static class InputHostDialog extends InputDialog {

      public InputHostDialog(Component parentComponent,
                             String message,
                             String title,
                             Icon icon,
                             String initialValue,
                             InputValidator validator) {
        super(parentComponent, message, title, icon, initialValue, validator);
      }

      protected Action[] createActions() {
        final Action[] actions = super.createActions();
        return ArrayUtil.append(actions, new AbstractAction("Check Now") {
          public void actionPerformed(final ActionEvent e) {
            try {
              if (UpdateChecker.checkPluginsHost(getTextField().getText(), new ArrayList<PluginDownloader>())) {
                showInfoMessage(myField, "Plugins Host was successfully checked", "Check Plugins Host");
              }
              else {
                showErrorDialog(myField, "Plugin descriptions contain some errors. Please, check idea.log for details.");
              }
            }
            catch (Exception e1) {
              showErrorDialog(myField, "Connection failed: " + e1.getMessage());
            }
          }
        });
      }
    }
  }
}
