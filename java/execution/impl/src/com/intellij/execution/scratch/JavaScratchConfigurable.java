/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.execution.scratch;

import com.intellij.application.options.ModulesComboBox;
import com.intellij.execution.ui.CommonJavaParametersPanel;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.execution.ui.DefaultJreSelector;
import com.intellij.execution.ui.JrePathEditor;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.ide.scratch.ScratchRootType;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static java.awt.GridBagConstraints.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 30-Sep-15
 */
public class JavaScratchConfigurable extends SettingsEditor<JavaScratchConfiguration> implements PanelWithAnchor {

  private final CommonJavaParametersPanel myCommonProgramParameters;
  private final LabeledComponent<JTextField> myMainClass;
  private final LabeledComponent<TextFieldWithBrowseButton> myScratchPathField;
  private final LabeledComponent<ModulesComboBox> myModule;
  private JPanel myWholePanel;

  private final ConfigurationModuleSelector myModuleSelector;
  private JrePathEditor myJrePathEditor;
  private JComponent myAnchor;

  public JavaScratchConfigurable(final Project project) {
    myMainClass = new LabeledComponent<>();
    myMainClass.setLabelLocation(BorderLayout.WEST);
    myMainClass.setText("Main &class:");
    myMainClass.setComponent(new JTextField());

    myScratchPathField = new LabeledComponent<>();
    myScratchPathField.setLabelLocation(BorderLayout.WEST);
    myScratchPathField.setText("&Path to scratch file:");
    myScratchPathField.setComponent(new TextFieldWithBrowseButton(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        VirtualFile toSelect = getVFileFromEditor();
        if (toSelect == null) {
          final String scratchesRoot = ScratchFileService.getInstance().getRootPath(ScratchRootType.getInstance());
          toSelect = LocalFileSystem.getInstance().findFileByPath(scratchesRoot);
        }
        final VirtualFile file =
          FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(), myScratchPathField.getComponent(), project, toSelect);
        if (file != null) {
          setVFileToEditor(file);
        }
      }
    }, this));

    myModule = new LabeledComponent<>();
    myModule.setLabelLocation(BorderLayout.WEST);
    myModule.setComponent(new ModulesComboBox());
    myModule.setText("Use classpath of &module:");
    myModuleSelector = new ConfigurationModuleSelector(project, myModule.getComponent());

    myCommonProgramParameters = new CommonJavaParametersPanel();
    myCommonProgramParameters.setModuleContext(myModuleSelector.getModule());
    myModule.getComponent().addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myCommonProgramParameters.setModuleContext(myModuleSelector.getModule());
      }
    });
    myJrePathEditor = new JrePathEditor(DefaultJreSelector.projectSdk(project));

    myWholePanel = new JPanel(new GridBagLayout());
    myWholePanel.add(myMainClass, new GridBagConstraints(0, 0, 1, 1, 1.0, 0.0, NORTHWEST, HORIZONTAL, JBUI.insetsTop(6), 0, 0 ));
    myWholePanel.add(myScratchPathField, new GridBagConstraints(RELATIVE, 1, 1, 1, 1.0, 0.0, NORTHWEST, HORIZONTAL, JBUI.insetsTop(6), 0, 0 ));
    myWholePanel.add(myCommonProgramParameters, new GridBagConstraints(RELATIVE, 2, 1, 1, 1.0, 1.0, NORTHWEST, BOTH, JBUI.insets(12, 0), 0, 0 ));
    myWholePanel.add(myModule, new GridBagConstraints(RELATIVE, 3, 1, 1, 1.0, 0.0, NORTHWEST, HORIZONTAL, JBUI.emptyInsets(), 0, 0 ));
    myWholePanel.add(myJrePathEditor, new GridBagConstraints(RELATIVE, 4, 1, 1, 1.0, 0.0, NORTHWEST, HORIZONTAL, JBUI.insetsTop(6), 0, 0 ));

    myAnchor = UIUtil.mergeComponentsWithAnchor(myMainClass, myScratchPathField, myCommonProgramParameters, myJrePathEditor, myModule);
  }

  @Override
  public void applyEditorTo(JavaScratchConfiguration configuration) throws ConfigurationException {
    myCommonProgramParameters.applyTo(configuration);
    myModuleSelector.applyTo(configuration);
    configuration.MAIN_CLASS_NAME = myMainClass.getComponent().getText().trim();
    configuration.ALTERNATIVE_JRE_PATH = myJrePathEditor.getJrePathOrName();
    configuration.ALTERNATIVE_JRE_PATH_ENABLED = myJrePathEditor.isAlternativeJreSelected();

    final VirtualFile vFile = getVFileFromEditor();
    configuration.SCRATCH_FILE_ID = vFile instanceof VirtualFileWithId ? ((VirtualFileWithId)vFile).getId() : 0;
  }

  @Nullable
  private VirtualFile getVFileFromEditor() {
    final String path = FileUtil.toSystemIndependentName(myScratchPathField.getComponent().getText().trim());
    return !StringUtil.isEmpty(path) ? LocalFileSystem.getInstance().findFileByPath(path) : null;
  }

  @Override
  public void resetEditorFrom(JavaScratchConfiguration configuration) {
    myCommonProgramParameters.reset(configuration);
    myModuleSelector.reset(configuration);
    myMainClass.getComponent().setText(configuration.MAIN_CLASS_NAME != null ? configuration.MAIN_CLASS_NAME.replaceAll("\\$", "\\.") : "");
    myJrePathEditor.setPathOrName(configuration.ALTERNATIVE_JRE_PATH, configuration.ALTERNATIVE_JRE_PATH_ENABLED);
    setVFileToEditor(configuration.getScratchVirtualFile());
  }

  private void setVFileToEditor(VirtualFile file) {
    myScratchPathField.getComponent().setText(file != null? FileUtil.toSystemDependentName(file.getPath()): "");
  }

  @NotNull
  @Override
  public JComponent createEditor() {
    return myWholePanel;
  }

  @Override
  public JComponent getAnchor() {
    return myAnchor;
  }

  @Override
  public void setAnchor(@Nullable JComponent anchor) {
    myAnchor = anchor;
    myMainClass.setAnchor(anchor);
    myScratchPathField.setAnchor(anchor);
    myCommonProgramParameters.setAnchor(anchor);
    myJrePathEditor.setAnchor(anchor);
    myModule.setAnchor(anchor);
  }
}
