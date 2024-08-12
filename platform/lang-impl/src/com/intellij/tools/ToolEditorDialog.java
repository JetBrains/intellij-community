// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools;

import com.intellij.execution.filters.RegexpFilter;
import com.intellij.ide.DataManager;
import com.intellij.ide.macro.MacrosDialog;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.keymap.MacKeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class ToolEditorDialog extends DialogWrapper {

  static final Function<String, List<String>> OUTPUT_FILTERS_SPLITTER = s -> StringUtil.split(s, MacKeymapUtil.RETURN);
  static final Function<List<String>, String> OUTPUT_FILTERS_JOINER = strings -> StringUtil.join(strings, MacKeymapUtil.RETURN);

  private final Project myProject;
  private boolean myEnabled;

  private final ToolEditorDialogPanel content = new ToolEditorDialogPanel();

  protected ToolEditorDialog(JComponent parent, @NlsContexts.DialogTitle String title) {
    super(parent, true);

    DataContext dataContext = DataManager.getInstance().getDataContext(parent);
    myProject = CommonDataKeys.PROJECT.getData(dataContext);
    setTitle(title);

    init();
    addListeners();
  }

  @Override
  protected String getHelpId() {
    return "preferences.externalToolsEdit";
  }

  public @Nullable Project getProject() {
    return myProject;
  }

  @Override
  protected @NotNull JPanel createCenterPanel() {
    fillAdditionalOptionsPanel(content.additionalOptionsPanel);
    return content.panel;
  }

  protected void fillAdditionalOptionsPanel(final @NotNull JPanel panel) {}

  protected void addWorkingDirectoryBrowseAction(final @NotNull TextFieldWithBrowseButton workingDirField) {
    workingDirField.addBrowseFolderListener(null, null, myProject, FileChooserDescriptorFactory.createSingleFolderDescriptor());
  }

  protected void addProgramBrowseAction(final @NotNull TextFieldWithBrowseButton programField) {
    programField.addBrowseFolderListener(
      new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor(), myProject) {
        @Override
        protected void onFileChosen(@NotNull VirtualFile file) {
          super.onFileChosen(file);

          String workingDirectory = content.workingDirField.getText();
          if (workingDirectory.isEmpty()) {
            VirtualFile parent = file.getParent();
            if (parent != null && parent.isDirectory()) {
              content.workingDirField.setText(parent.getPresentableUrl());
            }
          }
        }
      });
  }

  private void addListeners() {
    addProgramBrowseAction(content.programField);
    addWorkingDirectoryBrowseAction(content.workingDirField);

    MacrosDialog.addTextFieldExtension((ExtendableTextField)content.programField.getTextField());
    MacrosDialog.addTextFieldExtension((ExtendableTextField)content.argumentsField.getTextField());
    MacrosDialog.addTextFieldExtension((ExtendableTextField)content.workingDirField.getTextField());
  }

  @Override
  protected @Nullable ValidationInfo doValidate() {
    if (content.nameField.getText().trim().isEmpty()) {
      return new ValidationInfo(ToolsBundle.message("dialog.message.specify.the.tool.name"), content.nameField);
    }

    for (String s : OUTPUT_FILTERS_SPLITTER.fun(content.outputFilterField.getText())) {
      if (!s.contains(RegexpFilter.FILE_PATH_MACROS)) {
        return new ValidationInfo(
          ToolsBundle.message("dialog.message.each.output.filter.must.contain.0.macro", RegexpFilter.FILE_PATH_MACROS), content.outputFilterField);
      }
    }

    return null;
  }

  public Tool getData() {
    Tool tool = createTool();

    tool.setName(convertString(content.nameField.getText()));
    tool.setDescription(convertString(content.descriptionField.getText()));
    Object selectedItem = content.groupCombo.getSelectedItem();
    tool.setGroup(StringUtil.notNullize(selectedItem != null ? convertString(selectedItem.toString()) : ""));
    tool.setUseConsole(content.useConsoleCheckbox.isSelected());
    tool.setShowConsoleOnStdOut(content.showConsoleOnStdOutCheckbox.isSelected());
    tool.setShowConsoleOnStdErr(content.showConsoleOnStdErrCheckbox.isSelected());
    tool.setFilesSynchronizedAfterRun(content.synchronizedAfterRunCheckbox.isSelected());
    tool.setEnabled(myEnabled);

    tool.setWorkingDirectory(StringUtil.nullize(FileUtil.toSystemIndependentName(content.workingDirField.getText())));
    tool.setProgram(convertString(content.programField.getText()));
    tool.setParameters(convertString(content.argumentsField.getText()));

    final List<String> filterStrings = OUTPUT_FILTERS_SPLITTER.fun(content.outputFilterField.getText().trim());
    final FilterInfo[] filters = ContainerUtil.map2Array(filterStrings, FilterInfo.class, s -> new FilterInfo(s, "", ""));
    tool.setOutputFilters(filters);

    return tool;
  }

  protected Tool createTool() {
    return new Tool();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.tools.ToolEditorDialog";
  }

  /**
   * Initialize controls
   */
  protected void setData(Tool tool, String @Nls [] existingGroups) {
    content.nameField.setText(tool.getName());
    content.descriptionField.setText(tool.getDescription());
    if (content.groupCombo.getItemCount() > 0) {
      content.groupCombo.removeAllItems();
    }
    for (@Nls String existingGroup : existingGroups) {
      if (existingGroup != null) {
        content.groupCombo.addItem(existingGroup);
      }
    }
    content.groupCombo.setSelectedItem(tool.getGroup());
    content.useConsoleCheckbox.setSelected(tool.isUseConsole());
    content.showConsoleOnStdOutCheckbox.setEnabled(content.useConsoleCheckbox.isSelected());
    content.showConsoleOnStdOutCheckbox.setSelected(tool.isShowConsoleOnStdOut());
    content.showConsoleOnStdErrCheckbox.setEnabled(content.useConsoleCheckbox.isSelected());
    content.showConsoleOnStdErrCheckbox.setSelected(tool.isShowConsoleOnStdErr());
    content.synchronizedAfterRunCheckbox.setSelected(tool.synchronizeAfterExecution());
    myEnabled = tool.isEnabled();
    content.workingDirField.setText(FileUtil.toSystemDependentName(StringUtil.notNullize(tool.getWorkingDirectory())));
    content.programField.setText(tool.getProgram());
    content.argumentsField.setText(tool.getParameters());
    content.outputFilterField.setText(OUTPUT_FILTERS_JOINER.fun(ContainerUtil.map(tool.getOutputFilters(), info -> info.getRegExp())));
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return content.panel.getPreferredFocusedComponent();
  }

  private static String convertString(String s) {
    if (s != null && s.trim().isEmpty()) return null;
    return s;
  }
}