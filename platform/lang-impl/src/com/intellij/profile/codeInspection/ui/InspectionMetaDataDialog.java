// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class InspectionMetaDataDialog extends DialogWrapper {
  private final Pattern mySuppressIdPattern = Pattern.compile(LocalInspectionTool.VALID_ID_PATTERN);

  private final Function<String, @Nullable @NlsContexts.DialogMessage String> myNameValidator;
  private final JTextField myNameTextField;
  private final JTextField myProblemDescriptorTextField;
  private final EditorTextField myDescriptionTextArea;
  private final JTextField mySuppressIdTextField;
  private final JBCheckBox myCleanupCheckbox;
  private boolean showCleanupOption = false;

  public InspectionMetaDataDialog(@NotNull Project project,
                                  @NotNull String profileName,
                                  @NotNull Function<String, @Nullable @NlsContexts.DialogMessage String> nameValidator) {
    this(project, profileName, nameValidator, null, null, null, null);
  }

  public InspectionMetaDataDialog(@NotNull Project project,
                                  @NotNull String profileName,
                                  @NotNull Function<String, @Nullable @NlsContexts.DialogMessage String> nameValidator,
                                  @NlsSafe String name,
                                  @NlsSafe String description,
                                  @NlsSafe String problemDescriptor,
                                  @NlsSafe String suppressId) {
    super(project);
    myNameTextField = new JTextField(name == null ? InspectionsBundle.message("unnamed.inspection") : name);
    myProblemDescriptorTextField = new JTextField(problemDescriptor);
    final FileType htmlFileType = FileTypeManager.getInstance().getStdFileType("HTML");
    myDescriptionTextArea = new EditorTextField(ObjectUtils.notNull(description, ""), project, htmlFileType);
    myDescriptionTextArea.setOneLineMode(false);
    myDescriptionTextArea.setFont(EditorFontType.getGlobalPlainFont());
    myDescriptionTextArea.setPreferredSize(new Dimension(375, 125));
    myDescriptionTextArea.setMinimumSize(new Dimension(200, 50));
    mySuppressIdTextField = new JTextField(suppressId);
    myCleanupCheckbox = new JBCheckBox(InspectionsBundle.message("checkbox.cleanup.inspection"));
    myNameValidator = nameValidator;
    setTitle(InspectionsBundle.message("dialog.title.user.defined.inspection", profileName));
  }

  @Override
  public void show() {
    init();
    super.show();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameTextField;
  }

  @Override
  protected @NotNull List<ValidationInfo> doValidateAll() {
    final List<ValidationInfo> warnings = new SmartList<>();
    final String name = getName();
    if (StringUtil.isEmpty(name)) {
      warnings.add(new ValidationInfo(InspectionsBundle.message("dialog.message.name.must.not.be.empty"), myNameTextField));
    }
    else {
      String errorMessage = myNameValidator.apply(name);
      if (errorMessage != null) {
        warnings.add(new ValidationInfo(errorMessage, myNameTextField));
      }
    }
    final String suppressId = getSuppressId();
    if (!StringUtil.isEmpty(suppressId)) {
      if (!mySuppressIdPattern.matcher(suppressId).matches()) {
        warnings.add(new ValidationInfo(InspectionsBundle.message("dialog.message.suppress.id.must.match.regex"), mySuppressIdTextField));
      }
    }
    return warnings;
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    FormBuilder builder = new FormBuilder()
      .addLabeledComponent(InspectionsBundle.message("label.inspection.name"), myNameTextField, true);
    if (showCleanupOption) {
      builder.addComponent(myCleanupCheckbox);
    }
    builder
      .addLabeledComponentFillVertically(InspectionsBundle.message("label.description"), myDescriptionTextArea)
      .addLabeledComponent(InspectionsBundle.message("label.problem.tool.tip"), myProblemDescriptorTextField, true)
      .addLabeledComponent(InspectionsBundle.message("label.suppress.id"), mySuppressIdTextField);
    return builder.getPanel();
  }

  public @NlsSafe String getName() {
    return myNameTextField.getText().trim();
  }

  public @NlsSafe @Nullable String getDescription() {
    return convertEmptyToNull(myDescriptionTextArea.getText());
  }

  public @NlsSafe @Nullable String getSuppressId() {
    return convertEmptyToNull(mySuppressIdTextField.getText());
  }

  public @NlsSafe @Nullable String getProblemDescriptor() {
    return convertEmptyToNull(myProblemDescriptorTextField.getText());
  }

  public boolean isCleanup() {
    return myCleanupCheckbox.isSelected();
  }

  public void showCleanupOption(boolean cleanupValue) {
    myCleanupCheckbox.setSelected(cleanupValue);
    showCleanupOption = true;
  }

  private static String convertEmptyToNull(String s) {
    return StringUtil.isEmpty(s.trim()) ? null : s;
  }
}