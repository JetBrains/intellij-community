// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard;

import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Collections;

/**
 * @author Dmitry Avdeev
 */
public class JavaSettingsStep extends SdkSettingsStep {

  private static final @NotNull @NonNls String MODULE_SOURCE_ROOT_KEY = "java.module.default.source.root";
  private static final @NotNull @NonNls String DEFAULT_MODULE_SOURCE_ROOT_PATH = "src";

  private final ModuleBuilder             myModuleBuilder;
  private       JBCheckBox                myCreateSourceRoot;
  private       TextFieldWithBrowseButton mySourcePath;
  private       JPanel                    myPanel;

  public JavaSettingsStep(@NotNull SettingsStep settingsStep, @NotNull ModuleBuilder moduleBuilder, @NotNull Condition<? super SdkTypeId> sdkFilter) {
    super(settingsStep, moduleBuilder, sdkFilter);
    mySourcePath.setText(PropertiesComponent.getInstance().getValue(MODULE_SOURCE_ROOT_KEY, DEFAULT_MODULE_SOURCE_ROOT_PATH));
    myModuleBuilder = moduleBuilder;

    if (moduleBuilder instanceof JavaModuleBuilder) {
      addSourcePath(settingsStep);
    }
  }

  private void addSourcePath(SettingsStep settingsStep) {
    Project project = settingsStep.getContext().getProject();
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    descriptor.setTitle(JavaUiBundle.message("prompt.select.source.directory"));
    mySourcePath.addBrowseFolderListener(new TextBrowseFolderListener(descriptor, project) {
      @Override
      protected @NotNull String chosenFileToResultingText(@NotNull VirtualFile chosenFile) {
        String contentEntryPath = myModuleBuilder.getContentEntryPath();
        String path = chosenFile.getPath();
        return contentEntryPath == null ? path : path.substring(StringUtil.commonPrefixLength(contentEntryPath, path));
      }
    });
    myCreateSourceRoot.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        mySourcePath.setEnabled(myCreateSourceRoot.isSelected());
      }
    });
    settingsStep.addExpertPanel(myPanel);
  }

  @Override
  public void updateDataModel() {
    super.updateDataModel();
    if (myModuleBuilder instanceof JavaModuleBuilder) {
      if (myCreateSourceRoot.isSelected()) {
        String contentEntryPath = myModuleBuilder.getContentEntryPath();
        if (contentEntryPath != null) {
          final String dirName = mySourcePath.getText().trim().replace(File.separatorChar, '/');
          PropertiesComponent.getInstance().setValue(MODULE_SOURCE_ROOT_KEY, dirName);
          String text = !dirName.isEmpty() ? contentEntryPath + "/" + dirName : contentEntryPath;
          ((JavaModuleBuilder)myModuleBuilder).setSourcePaths(Collections.singletonList(Pair.create(text, "")));
        }
      }
      else {
        ((JavaModuleBuilder)myModuleBuilder).setSourcePaths(Collections.emptyList());
      }
    }
  }

  @TestOnly
  public void setSourcePath(@NlsSafe String path) {
    mySourcePath.setText(path);
  }
}
