// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.templates;

import com.intellij.ide.util.projectWizard.ProjectTemplateParameterFactory;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.io.FileUtilRt;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public final class SaveProjectAsTemplateDialog extends DialogWrapper {

  private final @NotNull Project myProject;
  private JPanel myPanel;
  private JTextField myName;
  private EditorTextField myDescription;
  private JComboBox<String> myModuleCombo;
  private JLabel myModuleLabel;
  private JBCheckBox myReplaceParameters;

  SaveProjectAsTemplateDialog(@NotNull Project project, @Nullable VirtualFile descriptionFile) {
    super(project);
    myProject = project;

    setTitle(LangBundle.message("dialog.title.save.project.as.template"));
    myName.setText(project.getName());

    Module[] modules = ModuleManager.getInstance(project).getModules();
    if (modules.length < 2) {
      myModuleLabel.setVisible(false);
      myModuleCombo.setVisible(false);
    }
    else {
      List<String> items = new ArrayList<>(ContainerUtil.map(modules, module -> module.getName()));
      String wholeProject = LangBundle.message("save.project.combobox.whole.project");
      items.add(wholeProject);
      myModuleCombo.setModel(new CollectionComboBoxModel<>(items, wholeProject));
    }
    myDescription.setFileType(FileTypeManager.getInstance().getFileTypeByExtension("html"));
    if (descriptionFile != null) {
      try {
        String s = VfsUtilCore.loadText(descriptionFile);
        myDescription.setText(s);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }

    boolean showReplaceParameters = ProjectTemplateParameterFactory.EP_NAME.getExtensionList().size() > 0;
    myReplaceParameters.setVisible(showReplaceParameters);
    myReplaceParameters.setSelected(showReplaceParameters);

    init();
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return myName;
  }

  @Override
  protected @Nullable String getDimensionServiceKey() {
    return "save.project.as.template.dialog";
  }

  @Override
  protected @Nullable ValidationInfo doValidate() {
    if (StringUtil.isEmpty(myName.getText())) {
      return new ValidationInfo(LangBundle.message("dialog.message.template.name.should.be.empty"));
    }
    return null;
  }

  @Override
  protected void doOKAction() {
    Path file = getTemplateFile();
    if (Files.exists(file)) {
      if (Messages.showYesNoDialog(myPanel,
                                   LangBundle.message("dialog.message.exists.already.do.you.want.to.replace.it.with.new.one",
                                                      FileUtilRt.getNameWithoutExtension(file.getFileName().toString())),
                                   LangBundle.message("dialog.title.template.already.exists"),
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
    if (item == null || item.equals(LangBundle.message("save.project.combobox.whole.project"))) return null;
    return ModuleManager.getInstance(myProject).findModuleByName(item);
  }

  private static final Logger LOG = Logger.getInstance(SaveProjectAsTemplateDialog.class);
}
