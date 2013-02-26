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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NonEmptyInputValidator;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PluginHostsConfigurable extends BaseConfigurable {
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

  public Collection<? extends String> getPluginsHosts() {
    return myUpdatesSettingsPanel.getPluginsHosts();
  }

  public static class CustomPluginRepositoriesPanel {
    private JBList myUrlsList;
    private JPanel myPanel;

    public CustomPluginRepositoriesPanel() {
      myUrlsList = new JBList(new DefaultListModel());
      myUrlsList.getEmptyText().setText(IdeBundle.message("update.no.update.hosts"));
      myUrlsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

      myPanel = ToolbarDecorator.createDecorator(myUrlsList)
        .setAddAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton button) {
            final HostMessages.InputHostDialog dlg =
              new HostMessages.InputHostDialog(myPanel,
                                               IdeBundle.message("update.plugin.host.url.message"),
                                               IdeBundle.message("update.add.new.plugin.host.title"),
                                               Messages.getQuestionIcon(), "",
                                               new NonEmptyInputValidator());
            dlg.show();
            String input = dlg.getInputString();
            if (input != null) {
              ((DefaultListModel)myUrlsList.getModel()).addElement(correctRepositoryRule(input));
            }
          }
        }).setEditAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton button) {
            final HostMessages.InputHostDialog dlg =
              new HostMessages.InputHostDialog(myPanel,
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
        }).disableUpDownActions().createPanel();
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

  private static String correctRepositoryRule(String input) {
    String protocol = VirtualFileManager.extractProtocol(input);
    if (protocol == null) {
      input = VirtualFileManager.constructUrl(HttpFileSystem.PROTOCOL, input);
    }
    return input;
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

      @NotNull
      protected Action[] createActions() {
        final Action[] actions = super.createActions();
        final AbstractAction checkNowAction = new AbstractAction("Check Now") {
          public void actionPerformed(final ActionEvent e) {
            final boolean[] result = new boolean[1];
            final Exception[] ex = new Exception[1];
            if (ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
              @Override
              public void run() {
                try {
                  result[0] =
                    UpdateChecker.checkPluginsHost(correctRepositoryRule(getTextField().getText()), new ArrayList<PluginDownloader>());
                }
                catch (Exception e1) {
                  ex[0] = e1;
                }
              }
            }, "Checking plugins repository...", true, null, getPreferredFocusedComponent())) {
              if (ex[0] != null) {
                showErrorDialog(myField, "Connection failed: " + ex[0].getMessage());
              }
              else if (result[0]) {
                showInfoMessage(myField, "Plugins repository was successfully checked", "Check Plugins Repository");
              }
              else {
                showErrorDialog(myField, "Plugin descriptions contain some errors. Please, check idea.log for details.");
              }
            }
          }
        };
        myField.getDocument().addDocumentListener(new DocumentAdapter() {
          @Override
          protected void textChanged(DocumentEvent e) {
            checkNowAction.setEnabled(!StringUtil.isEmptyOrSpaces(myField.getText()));
          }
        });
        checkNowAction.setEnabled(!StringUtil.isEmptyOrSpaces(myField.getText()));
        return ArrayUtil.append(actions, checkNowAction);
      }
    }
  }
}
