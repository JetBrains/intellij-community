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
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorConfigurable;
import com.intellij.openapi.options.SettingsEditorListener;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeListener;
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
  @Nullable private Executor myExecutor;

  private ValidationResult myLastValidationResult = null;
  private boolean myValidationResultValid = false;
  private MyValidatableComponent myComponent;
  private final String myDisplayName;
  private final String myHelpTopic;
  private final boolean myBrokenConfiguration;
  private boolean myStoreProjectConfiguration;
  private boolean mySingleton;
  private String myFolderName;


  private SingleConfigurationConfigurable(RunnerAndConfigurationSettings settings, @Nullable Executor executor) {
    super(new ConfigurationSettingsEditorWrapper(settings), settings);
    myExecutor = executor;

    final Config configuration = getConfiguration();
    myDisplayName = getSettings().getName();
    myHelpTopic = "reference.dialogs.rundebug." + configuration.getType().getId();

    myBrokenConfiguration = configuration instanceof UnknownRunConfiguration;
    setFolderName(getSettings().getFolderName());

    setNameText(configuration.getName());
    myNameDocument.addDocumentListener(new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        setModified(true);
      }
    });

    getEditor().addSettingsEditorListener(new SettingsEditorListener<RunnerAndConfigurationSettings>() {
      public void stateChanged(SettingsEditor<RunnerAndConfigurationSettings> settingsEditor) {
        myValidationResultValid = false;
      }
    });
  }

  public static <Config extends RunConfiguration> SingleConfigurationConfigurable<Config> editSettings(RunnerAndConfigurationSettings settings,
                                                                                                       @Nullable Executor executor) {
    SingleConfigurationConfigurable<Config> configurable = new SingleConfigurationConfigurable<Config>(settings, executor);
    configurable.reset();
    return configurable;
  }

  public void apply() throws ConfigurationException {
    RunnerAndConfigurationSettings settings = getSettings();
    RunConfiguration runConfiguration = settings.getConfiguration();
    final RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(runConfiguration.getProject());
    runManager.shareConfiguration(runConfiguration, myStoreProjectConfiguration);
    settings.setName(getNameText());
    settings.setSingleton(mySingleton);
    settings.setFolderName(myFolderName);
    super.apply();
    RunManagerImpl.getInstanceImpl(getConfiguration().getProject()).fireRunConfigurationChanged(settings);
  }

  public void reset() {
    RunnerAndConfigurationSettings configuration = getSettings();
    setNameText(configuration.getName());
    super.reset();
    if (myComponent == null) {
      myComponent = new MyValidatableComponent();
    }
    myComponent.doReset(configuration);
  }

  public final JComponent createComponent() {
    myComponent.myNameText.setEnabled(!myBrokenConfiguration);
    return myComponent.getWholePanel();
  }

  public boolean isStoreProjectConfiguration() {
    return myStoreProjectConfiguration;
  }

  public boolean isSingleton() {
    return mySingleton;
  }

  @Nullable
  private ValidationResult getValidationResult() {
    if (!myValidationResultValid) {
      myLastValidationResult = null;
      try {
        RunnerAndConfigurationSettings snapshot = getSnapshot();
        if (snapshot != null) {
          snapshot.setName(getNameText());
          snapshot.checkSettings(myExecutor);
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

  public final void addNameListener(DocumentListener listener) {
    myNameDocument.addDocumentListener(listener);
  }

  public final void addSharedListener(ChangeListener changeListener) {
    myComponent.myCbStoreProjectConfiguration.addChangeListener(changeListener);
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

  @Override
  public String toString() {
    return myDisplayName;
  }

  public void setFolderName(@Nullable String folderName) {
    if (!Comparing.equal(myFolderName, folderName)) {
      myFolderName = folderName;
      setModified(true);
    }
  }

  @Nullable
  public String getFolderName() {
    return myFolderName;
  }

  private class MyValidatableComponent {
    private JLabel myNameLabel;
    private JTextField myNameText;
    private JComponent myWholePanel;
    private JPanel myComponentPlace;
    private JLabel myWarningLabel;
    private JButton myFixButton;
    private JSeparator mySeparator;
    private JCheckBox myCbStoreProjectConfiguration;
    private JBCheckBox myCbSingleton;

    private Runnable myQuickFix = null;

    public MyValidatableComponent() {
      myNameLabel.setLabelFor(myNameText);
      myNameText.setDocument(myNameDocument);

      getEditor().addSettingsEditorListener(new SettingsEditorListener() {
        public void stateChanged(SettingsEditor settingsEditor) {
          updateWarning();
        }
      });

      myWarningLabel.setIcon(AllIcons.RunConfigurations.ConfigurationWarning);

      myComponentPlace.setLayout(new GridBagLayout());
      myComponentPlace.add(getEditorComponent(),
                           new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                                                  new Insets(0, 0, 0, 0), 0, 0));
      myComponentPlace.doLayout();
      myFixButton.setIcon(AllIcons.Actions.QuickfixBulb);
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
      ActionListener actionListener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          setModified(true);
          myStoreProjectConfiguration = myCbStoreProjectConfiguration.isSelected();
          mySingleton = myCbSingleton.isSelected();
        }
      };
      myCbStoreProjectConfiguration.addActionListener(actionListener);
      myCbSingleton.addActionListener(actionListener);
      settingAnchor();
    }

    private void doReset(RunnerAndConfigurationSettings settings) {
      final RunConfiguration runConfiguration = settings.getConfiguration();
      final RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(runConfiguration.getProject());
      myStoreProjectConfiguration = runManager.isConfigurationShared(settings);
      myCbStoreProjectConfiguration.setEnabled(!(runConfiguration instanceof UnknownRunConfiguration));
      myCbStoreProjectConfiguration.setSelected(myStoreProjectConfiguration);
      myCbStoreProjectConfiguration.setVisible(!settings.isTemplate());

      mySingleton = settings.isSingleton();
      myCbSingleton.setEnabled(!(runConfiguration instanceof UnknownRunConfiguration));
      myCbSingleton.setSelected(mySingleton);
      ConfigurationFactory factory = settings.getFactory();
      myCbSingleton.setVisible(factory != null && factory.canConfigurationBeSingleton());
    }

    private void settingAnchor() {
    }

    public final JComponent getWholePanel() {
      return myWholePanel;
    }

    public JComponent getEditorComponent() {
      return getEditor().getComponent();
    }

    @Nullable
    public ValidationResult getValidationResult() {
      return SingleConfigurationConfigurable.this.getValidationResult();
    }

    private void updateWarning() {
      final ValidationResult configurationException = getValidationResult();

      if (configurationException != null) {
        mySeparator.setVisible(true);
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
        mySeparator.setVisible(false);
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
