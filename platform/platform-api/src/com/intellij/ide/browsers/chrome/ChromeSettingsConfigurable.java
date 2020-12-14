// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.browsers.chrome;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.RawCommandLineEditor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

import static com.intellij.ide.SpecialConfigFiles.CHROME_USER_DATA;

public class ChromeSettingsConfigurable implements Configurable {
  private final ChromeSettings mySettings;
  private JPanel myMainPanel;
  private JCheckBox myUseCustomProfileCheckBox;
  private TextFieldWithBrowseButton myUserDataDirField;
  private JLabel myCommandLineOptionsLabel;
  private RawCommandLineEditor myCommandLineOptionsEditor;
  private final String myDefaultUserDirPath;

  public ChromeSettingsConfigurable(@NotNull ChromeSettings settings) {
    mySettings = settings;
    myUserDataDirField.addBrowseFolderListener(IdeBundle.message("chooser.title.select.user.data.directory"), IdeBundle
                                                 .message("chooser.description.specifies.user.data.directory"), null,
                                               FileChooserDescriptorFactory.createSingleFolderDescriptor());
    myUseCustomProfileCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myUserDataDirField.setEnabled(myUseCustomProfileCheckBox.isSelected());
      }
    });
    myDefaultUserDirPath = getDefaultUserDataPath();
    myCommandLineOptionsEditor.setDialogCaption("Chrome Command Line Options");
    myCommandLineOptionsLabel.setLabelFor(myCommandLineOptionsEditor.getTextField());
  }

  @Override
  public JComponent createComponent() {
    return myMainPanel;
  }

  @Override
  public boolean isModified() {
    if (myUseCustomProfileCheckBox.isSelected() != mySettings.isUseCustomProfile()
        || !myCommandLineOptionsEditor.getText().equals(StringUtil.notNullize(mySettings.getCommandLineOptions()))) {
      return true;
    }

    String configuredPath = getConfiguredUserDataDirPath();
    String storedPath = mySettings.getUserDataDirectoryPath();
    if (myDefaultUserDirPath.equals(configuredPath) && storedPath == null) return false;
    return !configuredPath.equals(storedPath);
  }


  private String getConfiguredUserDataDirPath() {
    return FileUtil.toSystemIndependentName(myUserDataDirField.getText());
  }

  @Override
  public void apply() throws ConfigurationException {
    mySettings.setCommandLineOptions(myCommandLineOptionsEditor.getText());
    mySettings.setUseCustomProfile(myUseCustomProfileCheckBox.isSelected());
    mySettings.setUserDataDirectoryPath(getConfiguredUserDataDirPath());
  }

  @Override
  public void reset() {
    myCommandLineOptionsEditor.setText(mySettings.getCommandLineOptions());
    myUseCustomProfileCheckBox.setSelected(mySettings.isUseCustomProfile());
    myUserDataDirField.setEnabled(mySettings.isUseCustomProfile());
    String path = mySettings.getUserDataDirectoryPath();
    if (path != null) {
      myUserDataDirField.setText(FileUtil.toSystemDependentName(path));
    }
    else {
      myUserDataDirField.setText(FileUtil.toSystemDependentName(myDefaultUserDirPath));
    }
  }

  public void enableRecommendedOptions() {
    if (!myUseCustomProfileCheckBox.isSelected()) {
      myUseCustomProfileCheckBox.doClick(0);
    }
  }

  private static String getDefaultUserDataPath() {
    File dir = new File(PathManager.getConfigPath(), CHROME_USER_DATA);
    try {
      return FileUtil.toSystemIndependentName(dir.getCanonicalPath());
    }
    catch (IOException e) {
      return FileUtil.toSystemIndependentName(dir.getAbsolutePath());
    }
  }

  @Nls
  @Override
  public String getDisplayName() {
    return IdeBundle.message("configurable.ChromeSettingsConfigurable.display.name");
  }

  @Override
  public String getHelpTopic() {
    return "reference.settings.ide.settings.web.browsers.edit";
  }
}
