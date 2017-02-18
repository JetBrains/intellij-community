/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NonEmptyInputValidator;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

public class PluginHostsConfigurable extends BaseConfigurable implements Configurable.NoScroll {
  private CustomPluginRepositoriesPanel myUpdatesSettingsPanel;

  @Override
  public JComponent createComponent() {
    myUpdatesSettingsPanel = new CustomPluginRepositoriesPanel();
    return myUpdatesSettingsPanel.myPanel;
  }

  @Override
  public String getDisplayName() {
    return "Custom Plugin Repositories";
  }

  @Override
  public String getHelpTopic() {
    return null;
  }

  @Override
  public void apply() throws ConfigurationException {
    UpdateSettings settings = UpdateSettings.getInstance();

    settings.getStoredPluginHosts().clear();
    settings.getStoredPluginHosts().addAll(myUpdatesSettingsPanel.getPluginsHosts());
  }

  @Override
  public void reset() {
    myUpdatesSettingsPanel.setPluginHosts(UpdateSettings.getInstance().getStoredPluginHosts());
  }

  @Override
  public boolean isModified() {
    if (myUpdatesSettingsPanel == null) {
      return false;
    }
    //noinspection EqualsBetweenInconvertibleTypes
    return !UpdateSettings.getInstance().getStoredPluginHosts().equals(myUpdatesSettingsPanel.getPluginsHosts());
  }

  @Override
  public void disposeUIResources() {
    myUpdatesSettingsPanel = null;
  }

  public static class CustomPluginRepositoriesPanel {
    private final JBList myUrlsList;
    private final JPanel myPanel;

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
              //noinspection unchecked
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
                                                 @Override
                                                 public boolean checkInput(final String inputString) {
                                                   return inputString.length() > 0;
                                                 }

                                                 @Override
                                                 public boolean canClose(final String inputString) {
                                                   return checkInput(inputString);
                                                 }
                                               });
            dlg.show();
            final String input = dlg.getInputString();
            if (input != null) {
              //noinspection unchecked
              ((DefaultListModel)myUrlsList.getModel()).set(myUrlsList.getSelectedIndex(), input);
            }
          }
        }).disableUpDownActions().createPanel();
    }


    public List<String> getPluginsHosts() {
      final List<String> result = new ArrayList<>();
      for (int i = 0; i < myUrlsList.getModel().getSize(); i++) {
        result.add((String)myUrlsList.getModel().getElementAt(i));
      }
      return result;
    }

    public void setPluginHosts(final List<String> pluginHosts) {
      final DefaultListModel model = (DefaultListModel)myUrlsList.getModel();
      model.clear();
      for (String host : pluginHosts) {
        //noinspection unchecked
        model.addElement(host);
      }
    }
  }

  private static String correctRepositoryRule(String input) {
    String protocol = VirtualFileManager.extractProtocol(input);
    if (protocol == null) {
      input = VirtualFileManager.constructUrl(URLUtil.HTTP_PROTOCOL, input);
    }
    return input;
  }

  private static class HostMessages extends Messages {
    public static class InputHostDialog extends InputDialog {
      public InputHostDialog(Component parent, String message, String title, Icon icon, String initialValue, InputValidator validator) {
        super(parent, message, title, icon, initialValue, validator);
      }

      @Override
      @NotNull
      protected Action[] createActions() {
        final AbstractAction checkNowAction = new AbstractAction("Check Now") {
          @Override
          public void actionPerformed(@Nullable ActionEvent e) {
            ProgressManager.getInstance().run(new Task.Modal(null, "Checking plugins repository...", true) {
              private int result;
              private Exception error;

              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                String host = correctRepositoryRule(getTextField().getText());
                try {
                  result = RepositoryHelper.loadPlugins(host, indicator).size();
                }
                catch (Exception e) {
                  error = e;
                }
              }

              @Override
              public void onSuccess() {
                if (error != null) {
                  showErrorDialog(myField, "Connection failed: " + error.getMessage());
                }
                else if (result == 0) {
                  showWarningDialog(myField, "No plugins found. Please check log file for possible errors.", "Check Plugins Repository");
                }
                else {
                  showInfoMessage(myField, "Repository was successfully checked", "Check Plugins Repository");
                }
              }
            });
          }
        };
        myField.getDocument().addDocumentListener(new DocumentAdapter() {
          @Override
          protected void textChanged(DocumentEvent e) {
            checkNowAction.setEnabled(!StringUtil.isEmptyOrSpaces(myField.getText()));
          }
        });
        checkNowAction.setEnabled(!StringUtil.isEmptyOrSpaces(myField.getText()));
        return ArrayUtil.append(super.createActions(), checkNowAction);
      }
    }
  }
}
