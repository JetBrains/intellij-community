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
package com.intellij.platform.templates;

import com.intellij.ide.util.projectWizard.ProjectTemplateParameterFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.PathKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class SaveProjectAsTemplateDialog extends DialogWrapper {

  private static final String WHOLE_PROJECT = "<whole project>";
  @NotNull private final Project myProject;
  private JPanel myPanel;
  private JTextField myName;
  private EditorTextField myDescription;
  private JComboBox myModuleCombo;
  private JLabel myModuleLabel;
  private JBCheckBox myReplaceParameters;

  protected SaveProjectAsTemplateDialog(@NotNull Project project, @Nullable VirtualFile descriptionFile) {
    super(project);
    myProject = project;

    setTitle("Save Project As Template");
    myName.setText(project.getName());

    Module[] modules = ModuleManager.getInstance(project).getModules();
    if (modules.length < 2) {
      myModuleLabel.setVisible(false);
      myModuleCombo.setVisible(false);
    }
    else {
      List<String> items = new ArrayList<>(ContainerUtil.map(modules, module -> module.getName()));
      items.add(WHOLE_PROJECT);
      myModuleCombo.setModel(new CollectionComboBoxModel(items, WHOLE_PROJECT));
    }
    myDescription.setFileType(FileTypeManager.getInstance().getFileTypeByExtension(".html"));
    if (descriptionFile != null) {
      try {
        String s = VfsUtilCore.loadText(descriptionFile);
        myDescription.setText(s);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }

    boolean showReplaceParameters = Extensions.getExtensions(ProjectTemplateParameterFactory.EP_NAME).length > 0;
    myReplaceParameters.setVisible(showReplaceParameters);
    myReplaceParameters.setSelected(showReplaceParameters);

    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myName;
  }

  @Nullable
  @Override
  protected String getDimensionServiceKey() {
    return "save.project.as.template.dialog";
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    if (StringUtil.isEmpty(myName.getText())) {
      return new ValidationInfo("Template name should not be empty");
    }
    return null;
  }

  @Override
  protected void doOKAction() {
    Path file = getTemplateFile();
    if (PathKt.exists(file)) {
      if (Messages.showYesNoDialog(myPanel,
                                   FileUtil.getNameWithoutExtension(file.getFileName().toString()) + " exists already.\n" +
                                   "Do you want to replace it with the new one?", "Template Already Exists",
                                   Messages.getWarningIcon()) == Messages.NO) {
        return;
      }
      PathKt.delete(file);
    }
    super.doOKAction();
  }

  Path getTemplateFile() {
    String name = myName.getText();
    return ArchivedTemplatesFactory.getTemplateFile(name);
  }

  String getDescription() {
    return myDescription.getText();
  }

  boolean isReplaceParameters() {
    return myReplaceParameters.isSelected();
  }

  @Nullable
  Module getModuleToSave() {
    String item = (String)myModuleCombo.getSelectedItem();
    if (item == null || item.equals(WHOLE_PROJECT)) return null;
    return ModuleManager.getInstance(myProject).findModuleByName(item);
  }

  private final static Logger LOG = Logger.getInstance(SaveProjectAsTemplateDialog.class);
}
