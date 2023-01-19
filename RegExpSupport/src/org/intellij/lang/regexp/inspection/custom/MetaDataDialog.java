// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.inspection.custom;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.ui.FormBuilder;
import org.intellij.lang.regexp.RegExpBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.regex.Pattern;

public class MetaDataDialog extends DialogWrapper {
  private final Pattern mySuppressIdPattern = Pattern.compile(LocalInspectionTool.VALID_ID_PATTERN);

  private final CustomRegExpInspection myInspection;
  @NotNull private final RegExpInspectionConfiguration myConfiguration;
  private final boolean myNewInspection;
  private final JTextField myNameTextField;
  private final JTextField myProblemDescriptorTextField;
  private final EditorTextField myDescriptionTextArea;
  private final JTextField mySuppressIdTextField;

  public MetaDataDialog(Project project, @NotNull CustomRegExpInspection inspection, @NotNull RegExpInspectionConfiguration configuration, boolean newInspection) {
    super((Project)null);
    myInspection = inspection;

    myConfiguration = configuration;
    myNewInspection = newInspection;
    myNameTextField = new JTextField(configuration.getName());
    myProblemDescriptorTextField = new JTextField(configuration.getProblemDescriptor());
    final FileType htmlFileType = FileTypeManager.getInstance().getStdFileType("HTML");
    myDescriptionTextArea = new EditorTextField(ObjectUtils.notNull(configuration.getDescription(), ""), project, htmlFileType);
    myDescriptionTextArea.setOneLineMode(false);
    myDescriptionTextArea.setFont(EditorFontType.getGlobalPlainFont());
    myDescriptionTextArea.setPreferredSize(new Dimension(375, 125));
    myDescriptionTextArea.setMinimumSize(new Dimension(200, 50));
    mySuppressIdTextField = new JTextField(configuration.getSuppressId());
    setTitle(RegExpBundle.message("dialog.title.custom.regexp.inspection"));
    init();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameTextField;
  }

  @Override
  protected @NotNull java.util.List<ValidationInfo> doValidateAll() {
    final List<ValidationInfo> warnings = new SmartList<>();
    final List<RegExpInspectionConfiguration> configurations = myInspection.getConfigurations();
    final String name = getName();
    if (StringUtil.isEmpty(name)) {
      warnings.add(new ValidationInfo(RegExpBundle.message("dialog.message.name.must.not.be.empty"), myNameTextField));
    }
    final String suppressId = getSuppressId();
    if (!StringUtil.isEmpty(suppressId)) {
      if (!mySuppressIdPattern.matcher(suppressId).matches()) {
        warnings.add(new ValidationInfo(RegExpBundle.message("dialog.message.suppress.id.must.match.regex.za.z"), mySuppressIdTextField));
      }
      else {
        final HighlightDisplayKey key = HighlightDisplayKey.findById(suppressId);
        if (key != null && key != HighlightDisplayKey.find(myConfiguration.getUuid())) {
          warnings.add(new ValidationInfo(
            RegExpBundle.message("dialog.message.suppress.id.already.in.use.by.another.inspection", suppressId), mySuppressIdTextField));
        }
        else {
          for (RegExpInspectionConfiguration configuration : configurations) {
            if (suppressId.equals(configuration.getSuppressId()) && !myConfiguration.getUuid().equals(configuration.getUuid())) {
              warnings.add(new ValidationInfo(
                RegExpBundle.message("dialog.message.suppress.id.already.in.use.by.another.inspection", suppressId), mySuppressIdTextField));
              break;
            }
          }
        }
      }
    }
    return warnings;
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    if (getOKAction().isEnabled()) {
      myConfiguration.name = getName();
      myConfiguration.description = getDescription();
      myConfiguration.suppressId = getSuppressId();
      myConfiguration.problemDescriptor = getProblemDescriptor();
    }
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return new FormBuilder()
      .addLabeledComponent(RegExpBundle.message("label.inspection.name"), myNameTextField, true)
      .addLabeledComponent(RegExpBundle.message("label.problem.tool.tip"), myProblemDescriptorTextField, true)
      .addLabeledComponentFillVertically(RegExpBundle.message("label.description"), myDescriptionTextArea)
      .addLabeledComponent(RegExpBundle.message("label.suppress.id"), mySuppressIdTextField)
      .getPanel();
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

  private static String convertEmptyToNull(String s) {
    return StringUtil.isEmpty(s.trim()) ? null : s;
  }
}