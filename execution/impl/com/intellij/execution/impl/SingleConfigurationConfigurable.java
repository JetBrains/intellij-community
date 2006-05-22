package com.intellij.execution.impl;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.ExecutionRegistry;
import com.intellij.execution.runners.JavaProgramRunner;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorConfigurable;
import com.intellij.openapi.options.SettingsEditorListener;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 *
 * @author dyoma
 */
public final class SingleConfigurationConfigurable<Config extends RunConfiguration> extends SettingsEditorConfigurable<RunnerAndConfigurationSettingsImpl> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.impl.SingleConfigurationConfigurable");
  private final PlainDocument myNameDocument = new PlainDocument();
  private ValidationResult myLastValidationResult = null;
  private boolean myValidationResultValid = false;
  private MyValidatableComponent myComponent;
  private final String myDisplayName;
  private final String myHelpTopic;
  private final Icon myIcon;

  private SingleConfigurationConfigurable(RunnerAndConfigurationSettingsImpl settings) {
    super(new ConfigurationSettingsEditorWrapper(settings), settings);

    final Config configuration = (Config)getSettings().getConfiguration();
    myDisplayName = getSettings().getName();
    myHelpTopic = null; //TODO
    myIcon = configuration.getType().getIcon();

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

  public static <Config extends RunConfiguration> SingleConfigurationConfigurable<Config> editSettings(RunnerAndConfigurationSettingsImpl settings) {
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
    }
    return myComponent.getWholePanel();
  }

  private ValidationResult getValidationResult() {
    if (!myValidationResultValid) {
      myLastValidationResult = null;
      try {
        RunnerAndConfigurationSettings snapshot = getSnapshot();
        snapshot.setName(getNameText());
        snapshot.getConfiguration().checkConfiguration();
        for (JavaProgramRunner runner : ExecutionRegistry.getInstance().getRegisteredRunners()) {
          runner.checkConfiguration(snapshot.getRunnerSettings(runner), snapshot.getConfigurationSettings(runner));
        }
      }
      catch (RuntimeConfigurationException exception) {
        myLastValidationResult = exception != null ? new ValidationResult(exception.getLocalizedMessage(),
                                                                          exception.getTitle(),
                                                                          exception.getQuickFix()) : null;
      }
      catch (ConfigurationException e) {
        myLastValidationResult = new ValidationResult(e.getLocalizedMessage(), ExecutionBundle.message("invalid.data.dialog.title"), null);
      }
      myValidationResultValid = true;
    }
    return myLastValidationResult;
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

  public final JComponent getNameTextField() {
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

  public RunnerAndConfigurationSettingsImpl getSnapshot() throws ConfigurationException {
    return getEditor().getSnapshot();
  }

  private class MyValidatableComponent {
    private JLabel myNameLabel;
    private JTextField myNameText;
    private JComponent myWholePanel;
    private JPanel myComponentPlace;
    private JPanel myOutlinePanel;
    private JLabel myWarningLabel;
    private JButton myFixButton;
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

      myComponentPlace.setLayout(new GridBagLayout());
      myComponentPlace.add(getEditorComponent(), new GridBagConstraints(0,0,1,1,1.0,1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0,0,0,0), 0,0));
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
      return "<html><body><b>" + configurationException.getTitle() + ": </b>" +
             configurationException.getMessage() + "</body></html>";
    }
  }
}
