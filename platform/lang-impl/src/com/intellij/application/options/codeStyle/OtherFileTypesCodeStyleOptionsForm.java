// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.application.options.IndentOptionsEditor;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Used for non-language settings (if file type is not supported by Intellij IDEA), for example, plain text.
 */
public final class OtherFileTypesCodeStyleOptionsForm extends CodeStyleAbstractPanel {
  private final IndentOptionsEditorWithSmartTabs myIndentOptionsEditor;
  private JPanel myIndentOptionsPanel;
  private JPanel myTopPanel;

  OtherFileTypesCodeStyleOptionsForm(@NotNull CodeStyleSettings settings) {
    super(settings);
    myIndentOptionsEditor = new IndentOptionsEditorWithSmartTabs();
    myIndentOptionsPanel.add(myIndentOptionsEditor.createPanel(), BorderLayout.CENTER);
    addPanelToWatch(myIndentOptionsPanel);
  }

  @Override
  protected int getRightMargin() {
    return 0;
  }

  @Override
  protected @Nullable EditorHighlighter createHighlighter(@NotNull EditorColorsScheme scheme) {
    return null;
  }

  @Override
  protected @NotNull FileType getFileType() {
    return FileTypes.PLAIN_TEXT;
  }

  @Override
  protected @Nullable String getPreviewText() {
    return null;
  }

  @Override
  public void apply(@NotNull CodeStyleSettings settings) throws ConfigurationException {
    myIndentOptionsEditor.apply(settings, settings.OTHER_INDENT_OPTIONS);
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    return myIndentOptionsEditor.isModified(settings, settings.OTHER_INDENT_OPTIONS);
  }

  @Override
  public @Nullable JComponent getPanel() {
    return myTopPanel;
  }

  @Override
  protected void resetImpl(@NotNull CodeStyleSettings settings) {
    myIndentOptionsEditor.reset(settings, settings.OTHER_INDENT_OPTIONS);
  }
}

final class IndentOptionsEditorWithSmartTabs extends IndentOptionsEditor {
  private JCheckBox myCbSmartTabs;

  @Override
  protected void addTabOptions() {
    super.addTabOptions();
    myCbSmartTabs = new JCheckBox(ApplicationBundle.message("checkbox.indent.smart.tabs"));
    add(myCbSmartTabs, true);
  }

  @Override
  public void reset(@NotNull CodeStyleSettings settings, @NotNull CommonCodeStyleSettings.IndentOptions options) {
    super.reset(settings, options);
    myCbSmartTabs.setSelected(options.SMART_TABS);
  }

  @Override
  public boolean isModified(CodeStyleSettings settings, CommonCodeStyleSettings.IndentOptions options) {
    return super.isModified(settings, options) || isFieldModified(myCbSmartTabs, options.SMART_TABS);
  }

  @Override
  public void apply(CodeStyleSettings settings, CommonCodeStyleSettings.IndentOptions options) {
    super.apply(settings, options);
    options.SMART_TABS = myCbSmartTabs.isSelected();
  }
}
