/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesAlphaComparator;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.elements.ManifestFileUtil;
import com.intellij.ui.ComboboxSpeedSearch;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ListCellRendererWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;

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
  private JRadioButton myCopyJarsRadioButton;
  private JCheckBox myIncludeTestsCheckBox;
  private final PackagingElementResolvingContext myContext;

  public JarArtifactFromModulesDialog(PackagingElementResolvingContext context) {
    super(context.getProject());
    myContext = context;
    setTitle("Create JAR from Modules");
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
    final ActionListener actionListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateManifestDirField();
      }
    };
    myExtractJarsRadioButton.addActionListener(actionListener);
    myCopyJarsRadioButton.addActionListener(actionListener);

    updateManifestDirField();
    myManifestDirField.addBrowseFolderListener(null, null, project, ManifestFileUtil.createDescriptorForManifestDirectory());

    setupModulesCombobox(context);
    init();
  }

  private void setupModulesCombobox(PackagingElementResolvingContext context) {
    final Module[] modules = context.getModulesProvider().getModules().clone();
    Arrays.sort(modules, ModulesAlphaComparator.INSTANCE);
    if (modules.length > 1) {
      myModuleComboBox.addItem(null);
    }
    for (Module module : modules) {
      myModuleComboBox.addItem(module);
    }
    myModuleComboBox.setRenderer(new ModuleListRenderer(myModuleComboBox));
    new ComboboxSpeedSearch(myModuleComboBox) {
      @Override
      protected String getElementText(Object element) {
        return element instanceof Module ? ((Module)element).getName() : "";
      }
    };
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

  public boolean isIncludeTests() {
    return myIncludeTestsCheckBox.isSelected();
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

  @Override
  protected String getHelpId() {
    return "reference.project.structure.artifacts.jar.from.module";
  }

  private static class ModuleListRenderer extends ListCellRendererWrapper<Module> {
    public ModuleListRenderer(JComboBox comboBox) {
      super();
    }

    @Override
    public void customize(JList list, Module value, int index, boolean selected, boolean hasFocus) {
      if (value != null) {
        setIcon(ModuleType.get(value).getIcon());
        setText(value.getName());
      }
      else {
        setText("<All Modules>");
        setIcon(null);
      }
    }
  }
}
