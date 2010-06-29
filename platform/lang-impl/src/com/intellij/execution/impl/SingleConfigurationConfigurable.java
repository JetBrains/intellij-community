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

package com.intellij.execution.impl;

import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorConfigurable;
import com.intellij.openapi.options.SettingsEditorListener;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public final class SingleConfigurationConfigurable<Config extends RunConfiguration>
    extends SettingsEditorConfigurable<RunnerAndConfigurationSettings> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.impl.SingleConfigurationConfigurable");
  private final PlainDocument myNameDocument = new PlainDocument();
  private ValidationResult myLastValidationResult = null;
  private boolean myValidationResultValid = false;
  private MyValidatableComponent myComponent;
  private final String myDisplayName;
  private final String myHelpTopic;
  private final Icon myIcon;
  private final boolean myBrokenConfiguration;

  private SingleConfigurationConfigurable(RunnerAndConfigurationSettings settings) {
    super(new ConfigurationSettingsEditorWrapper(settings), settings);

    final Config configuration = (Config)getSettings().getConfiguration();
    myDisplayName = getSettings().getName();
    myHelpTopic = null; //TODO
    myIcon = configuration.getType().getIcon();

    myBrokenConfiguration = configuration instanceof UnknownRunConfiguration;

    setNameText(configuration.getName());
    myNameDocument.addDocumentListener(new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        setModified(true);
      }
    });

    getEditor().addSettingsEditorListener(new SettingsEditorListener() {
      public void stateChanged(SettingsEditor settingsEditor) {
        myValidationResultValid = false;
      }
    });
  }

  public static <Config extends RunConfiguration> SingleConfigurationConfigurable<Config> editSettings(RunnerAndConfigurationSettings settings) {
    SingleConfigurationConfigurable<Config> configurable = new SingleConfigurationConfigurable<Config>(settings);
    configurable.reset();
    return configurable;
  }

  public void apply() throws ConfigurationException {
    getSettings().setName(getNameText());
    super.apply();
  }

  public void reset() {
    RunnerAndConfigurationSettings configuration = getSettings();
    if (configuration != null) {
      setNameText(configuration.getName());
    }
    super.reset();
  }

  public final JComponent createComponent() {
    if (myComponent == null) {
      myComponent = new MyValidatableComponent();
      myComponent.myNameText.setEnabled(!myBrokenConfiguration);
    }
    return myComponent.getWholePanel();
  }

  private ValidationResult getValidationResult() {
    if (!myValidationResultValid) {
      myLastValidationResult = null;
      try {
        RunnerAndConfigurationSettings snapshot = getSnapshot();
        if (snapshot != null) {
          snapshot.setName(getNameText());
          snapshot.checkSettings();
          for (ProgramRunner runner : RunnerRegistry.getInstance().getRegisteredRunners()) {
            for (Executor executor : ExecutorRegistry.getInstance().getRegisteredExecutors()) {
              if (runner.canRun(executor.getId(), snapshot.getConfiguration())) {
                checkConfiguration(runner, snapshot);
                break;
              }
            }
          }
        }
      }
      catch (RuntimeConfigurationException exception) {
        myLastValidationResult =
            exception != null ? new ValidationResult(exception.getLocalizedMessage(), exception.getTitle(), exception.getQuickFix()) : null;
      }
      catch (ConfigurationException e) {
        myLastValidationResult = new ValidationResult(e.getLocalizedMessage(), ExecutionBundle.message("invalid.data.dialog.title"), null);
      }

      myValidationResultValid = true;
    }
    return myLastValidationResult;
  }

  private static void checkConfiguration(final ProgramRunner runner, final RunnerAndConfigurationSettings snapshot)
      throws RuntimeConfigurationException {
    final RunnerSettings runnerSettings = snapshot.getRunnerSettings(runner);
    final ConfigurationPerRunnerSettings configurationSettings = snapshot.getConfigurationSettings(runner);
    try {
      runner.checkConfiguration(runnerSettings, configurationSettings);
    }
    catch (AbstractMethodError e) {
      //backward compatibility
    }
  }

  public final void disposeUIResources() {
    super.disposeUIResources();
    myComponent = null;
  }

  public final String getNameText() {
    try {
      return myNameDocument.getText(0, myNameDocument.getLength());
    }
    catch (BadLocationException e) {
      LOG.error(e);
      return "";
    }
  }

  public final void addNameListner(DocumentListener listener) {
    myNameDocument.addDocumentListener(listener);
  }

  public final void setNameText(final String name) {
    try {
      myNameDocument.replace(0, myNameDocument.getLength(), name, null);
    }
    catch (BadLocationException e) {
      LOG.error(e);
    }
  }

  public final boolean isValid() {
    return getValidationResult() == null;
  }

  public final JTextField getNameTextField() {
    return myComponent.myNameText;
  }

  public String getDisplayName() {
    return myDisplayName;
  }

  public Icon getIcon() {
    return myIcon;
  }

  public String getHelpTopic() {
    return myHelpTopic;
  }

  public Config getConfiguration() {
    return (Config)getSettings().getConfiguration();
  }

  public RunnerAndConfigurationSettings getSnapshot() throws ConfigurationException {
    final SettingsEditor<RunnerAndConfigurationSettings> editor = getEditor();
    return editor == null ? null : editor.getSnapshot();
  }

  private class MyValidatableComponent {
    private JLabel myNameLabel;
    private JTextField myNameText;
    private JComponent myWholePanel;
    private JPanel myComponentPlace;
    private JPanel myOutlinePanel;
    private JLabel myWarningLabel;
    private JButton myFixButton;
    private JBScrollPane myScrollPane;
    private Runnable myQuickFix = null;

    public MyValidatableComponent() {
      myNameLabel.setLabelFor(myNameText);
      myNameText.setDocument(myNameDocument);

      getEditor().addSettingsEditorListener(new SettingsEditorListener() {
        public void stateChanged(SettingsEditor settingsEditor) {
          updateWarning();
        }
      });

      myWarningLabel.setIcon(IconLoader.getIcon("/runConfigurations/configurationWarning.png"));

      myScrollPane.setBorder(null);
      myComponentPlace.setLayout(new GridBagLayout());
      myComponentPlace.add(getEditorComponent(),
                           new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                                                  new Insets(0, 0, 0, 0), 0, 0));
      myComponentPlace.doLayout();
      myFixButton.setIcon(IconLoader.getIcon("/actions/quickfixBulb.png"));
      updateWarning();
      myFixButton.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          if (myQuickFix == null) {
            return;
          }
          myQuickFix.run();
          myValidationResultValid = false;
          updateWarning();
        }
      });
    }

    public final JComponent getWholePanel() {
      return myWholePanel;
    }

    public JComponent getEditorComponent() {
      return getEditor().getComponent();
    }

    public ValidationResult getValidationResult() {
      return SingleConfigurationConfigurable.this.getValidationResult();
    }

    private void updateWarning() {
      final ValidationResult configurationException = getValidationResult();

      if (configurationException != null) {
        myOutlinePanel.setVisible(true);
        myWarningLabel.setVisible(true);
        myWarningLabel.setText(generateWarningLabelText(configurationException));
        final Runnable quickFix = configurationException.getQuickFix();
        if (quickFix == null) {
          myFixButton.setVisible(false);
        }
        else {
          myFixButton.setVisible(true);
          myQuickFix = quickFix;
        }

      }
      else {
        myOutlinePanel.setVisible(false);
        myWarningLabel.setVisible(false);
        myFixButton.setVisible(false);
      }
    }

    @NonNls
    private String generateWarningLabelText(final ValidationResult configurationException) {
      return "<html><body><b>" + configurationException.getTitle() + ": </b>" + configurationException.getMessage() + "</body></html>";
    }
  }
}
