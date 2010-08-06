/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.packaging.impl.artifacts;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.elements.ManifestFileUtil;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;

/**
 * @author nik
 */
public class JarArtifactFromModulesDialog extends DialogWrapper {
  private JPanel myMainPanel;
  private TextFieldWithBrowseButton myMainClassField;
  private JComboBox myModuleComboBox;
  private JLabel myMainClassLabel;
  private TextFieldWithBrowseButton myManifestDirField;
  private JLabel myManifestDirLabel;
  private JRadioButton myExtractJarsRadioButton;
  private PackagingElementResolvingContext myContext;

  public JarArtifactFromModulesDialog(PackagingElementResolvingContext context) {
    super(context.getProject());
    myContext = context;
    setTitle("Create Jar from Modules");
    myMainClassLabel.setLabelFor(myMainClassField.getTextField());
    myManifestDirLabel.setLabelFor(myManifestDirField.getTextField());

    final Project project = myContext.getProject();
    ManifestFileUtil.setupMainClassField(project, myMainClassField);
    myMainClassField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        updateManifestDirField();
      }
    });

    updateManifestDirField();
    myManifestDirField.addBrowseFolderListener(null, null, project, ManifestFileUtil.createDescriptorForManifestDirectory());

    final Module[] modules = context.getModulesProvider().getModules();
    if (modules.length > 1) {
      myModuleComboBox.addItem(null);
    }
    for (Module module : modules) {
      myModuleComboBox.addItem(module);
    }
    myModuleComboBox.setRenderer(new ModuleListRenderer());
    init();
  }

  private void updateManifestDirField() {
    final boolean enable = !myMainClassField.getText().isEmpty() || !myExtractJarsRadioButton.isSelected();
    setManifestDirFieldEnabled(enable);
    if (enable && myManifestDirField.getText().isEmpty()) {
      final VirtualFile file = ManifestFileUtil.suggestManifestFileDirectory(myContext.getProject(), getSelectedModule());
      if (file != null) {
        myManifestDirField.setText(FileUtil.toSystemDependentName(file.getPath()));
      }
    }
  }

  @Nullable
  private Module getSelectedModule() {
    return (Module)myModuleComboBox.getSelectedItem();
  }

  @NotNull
  public Module[] getSelectedModules() {
    final Module module = getSelectedModule();
    if (module != null) {
      return new Module[]{module};
    }
    return myContext.getModulesProvider().getModules();
  }

  @NotNull
  public String getDirectoryForManifest() {
    return FileUtil.toSystemIndependentName(myManifestDirField.getText());
  }

  public boolean isExtractLibrariesToJar() {
    return myExtractJarsRadioButton.isSelected();
  }

  public String getMainClassName() {
    return myMainClassField.getText();
  }

  private void setManifestDirFieldEnabled(boolean enabled) {
    myManifestDirLabel.setEnabled(enabled);
    myManifestDirField.setEnabled(enabled);
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  private static class ModuleListRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      final Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (value instanceof Module) {
        final Module module = (Module)value;
        setIcon(module.getModuleType().getNodeIcon(false));
        setText(module.getName());
      }
      else {
        setText("<All Modules>");
        setIcon(null);
      }
      return component;
    }
  }
}
