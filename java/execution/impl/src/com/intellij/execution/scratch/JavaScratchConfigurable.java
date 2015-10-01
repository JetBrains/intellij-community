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
package com.intellij.execution.scratch;

import com.intellij.execution.application.ApplicationConfigurable;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.ide.scratch.ScratchRootType;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Eugene Zhuravlev
 *         Date: 30-Sep-15
 */
public class JavaScratchConfigurable extends ApplicationConfigurable{

  private final TextFieldWithBrowseButton myScratchPathField;

  public JavaScratchConfigurable(final Project project) {
    super(project);
    myScratchPathField = new TextFieldWithBrowseButton(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        VirtualFile toSelect = getVFileFromEditor();
        if (toSelect == null) {
          final String scratchesRoot = ScratchFileService.getInstance().getRootPath(ScratchRootType.getInstance());
          toSelect = LocalFileSystem.getInstance().findFileByPath(scratchesRoot);
        }
        final VirtualFile file =
          FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(), myScratchPathField, project, toSelect);
        if (file != null) {
          setVFileToEditor(file);
        }
      }
    }, this);
  }

  @Override
  public void applyEditorTo(ApplicationConfiguration configuration) throws ConfigurationException {
    super.applyEditorTo(configuration);
    final VirtualFile vFile = getVFileFromEditor();
    ((JavaScratchConfiguration)configuration).SCRATCH_FILE_ID = vFile instanceof VirtualFileWithId ? ((VirtualFileWithId)vFile).getId() : 0;
  }

  @Nullable
  private VirtualFile getVFileFromEditor() {
    final String path = FileUtil.toSystemIndependentName(myScratchPathField.getText());
    return !StringUtil.isEmpty(path) ? LocalFileSystem.getInstance().findFileByPath(path) : null;
  }

  @Override
  public void resetEditorFrom(ApplicationConfiguration configuration) {
    super.resetEditorFrom(configuration);
    final JavaScratchConfiguration scratchConfig = (JavaScratchConfiguration)configuration;
    final VirtualFile file = scratchConfig.getScratchVirtualFile();
    setVFileToEditor(file);
  }

  private void setVFileToEditor(VirtualFile file) {
    if (file != null) {
      myScratchPathField.setText(FileUtil.toSystemDependentName(file.getPath()));
    }
    else {
      myScratchPathField.setText("");
    }
  }

  @NotNull
  @Override
  public JComponent createEditor() {
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.add(new JBLabel("Path to scratch file: "), new GridBagConstraints(0, 0, 1, 1, 0.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(20,0,0,10), 0, 0));
    panel.add(myScratchPathField, new GridBagConstraints(1, 0, 1, 1, 1.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(20, 0, 0, 0), 0, 0));
    return new BorderLayoutPanel().addToCenter(super.createEditor()).addToBottom(panel);
  }
}
