// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options;

import com.intellij.java.frontback.impl.JavaFrontbackBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.ui.components.fields.IntegerField;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.psi.codeStyle.CodeStyleConstraints.MAX_INDENT_SIZE;
import static com.intellij.psi.codeStyle.CodeStyleConstraints.MIN_INDENT_SIZE;


public class JavaIndentOptionsEditor extends SmartIndentOptionsEditor {
  private IntegerField myLabelIndent;
  private JLabel myLabelIndentLabel;

  private JCheckBox myLabelIndentAbsolute;
  private JCheckBox myCbDontIndentTopLevelMembers;
  private JCheckBox myCbUseRelativeIndent;

  @Override
  protected void addComponents() {
    super.addComponents();

    myLabelIndent = new IntegerField(getLabelIndentLabel(), MIN_INDENT_SIZE, MAX_INDENT_SIZE);
    myLabelIndent.setColumns(4);
    add(myLabelIndentLabel = new JLabel(getLabelIndentLabel()), myLabelIndent);

    myLabelIndentAbsolute = new JCheckBox(ApplicationBundle.message("checkbox.indent.absolute.label.indent"));
    add(myLabelIndentAbsolute, true);

    myCbDontIndentTopLevelMembers = new JCheckBox(JavaFrontbackBundle.message("checkbox.do.not.indent.top.level.class.members"));
    add(myCbDontIndentTopLevelMembers);

    myCbUseRelativeIndent = new JCheckBox(ApplicationBundle.message("checkbox.use.relative.indents"));
    add(myCbUseRelativeIndent);
  }

  @Override
  public boolean isModified(final CodeStyleSettings settings, final CommonCodeStyleSettings.IndentOptions options) {
    boolean isModified = super.isModified(settings, options);
    CommonCodeStyleSettings javaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);

    isModified |= isFieldModified(myLabelIndent, options.LABEL_INDENT_SIZE);
    isModified |= isFieldModified(myLabelIndentAbsolute, options.LABEL_INDENT_ABSOLUTE);
    isModified |= isFieldModified(myCbDontIndentTopLevelMembers, javaSettings.DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS);
    isModified |= isFieldModified(myCbUseRelativeIndent, options.USE_RELATIVE_INDENTS);

    return isModified;
  }

  @Override
  public void apply(final CodeStyleSettings settings, final CommonCodeStyleSettings.IndentOptions options) {
    super.apply(settings, options);
    options.LABEL_INDENT_SIZE = myLabelIndent.getValue();

    options.LABEL_INDENT_ABSOLUTE = myLabelIndentAbsolute.isSelected();
    CommonCodeStyleSettings javaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaSettings.DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS = myCbDontIndentTopLevelMembers.isSelected();
    options.USE_RELATIVE_INDENTS = myCbUseRelativeIndent.isSelected();
  }

  @Override
  public void reset(final @NotNull CodeStyleSettings settings, final @NotNull CommonCodeStyleSettings.IndentOptions options) {
    super.reset(settings, options);
    myLabelIndent.setValue(options.LABEL_INDENT_SIZE);
    myLabelIndentAbsolute.setSelected(options.LABEL_INDENT_ABSOLUTE);
    CommonCodeStyleSettings javaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    myCbDontIndentTopLevelMembers.setSelected(javaSettings.DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS);
    myCbUseRelativeIndent.setSelected(options.USE_RELATIVE_INDENTS);
  }

  @Override
  public void setEnabled(final boolean enabled) {
    super.setEnabled(enabled);
    myLabelIndent.setEnabled(enabled);
    myLabelIndentLabel.setEnabled(enabled);
    myLabelIndentAbsolute.setEnabled(enabled);
  }

  private static @Nls String getLabelIndentLabel() {
    return ApplicationBundle.message("editbox.indent.label.indent");
  }
}
