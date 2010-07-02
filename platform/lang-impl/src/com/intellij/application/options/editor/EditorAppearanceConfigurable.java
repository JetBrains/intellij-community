/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.application.options.editor;

import com.intellij.application.options.OptionId;
import com.intellij.application.options.OptionsApplicabilityFilter;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.CompositeConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.List;

/**
 * @author yole
 */
public class EditorAppearanceConfigurable extends CompositeConfigurable<UnnamedConfigurable> implements EditorOptionsProvider {
  public static final ExtensionPointName<UnnamedConfigurable> EP_NAME = ExtensionPointName.create("com.intellij.editorAppearanceConfigurable");
  private JPanel myRootPanel;
  private JCheckBox myCbBlinkCaret;
  private JCheckBox myCbBlockCursor;
  private JCheckBox myCbRightMargin;
  private JCheckBox myCbShowLineNumbers;
  private JCheckBox myCbShowWhitespaces;
  private JCheckBox myCbShowSoftwraps;
  private JTextField myBlinkIntervalField;
  private JPanel myAddonPanel;
  private JCheckBox myCbShowMethodSeparators;
  private JCheckBox myAntialiasingInEditorCheckBox;
  private JCheckBox myCbShowIconsInGutter;
  private JCheckBox myShowVerticalIndentGuidesCheckBox;

  public EditorAppearanceConfigurable() {
    myCbBlinkCaret.addActionListener(
    new ActionListener() {
      public void actionPerformed(ActionEvent event) {
        myBlinkIntervalField.setEnabled(myCbBlinkCaret.isSelected());
      }
    }
    );
    if (!OptionsApplicabilityFilter.isApplicable(OptionId.ICONS_IN_GUTTER)) {
      myCbShowIconsInGutter.setVisible(false);
    }
  }



  @Override
  public void reset() {
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();

    myCbShowMethodSeparators.setSelected(DaemonCodeAnalyzerSettings.getInstance().SHOW_METHOD_SEPARATORS);
    myCbBlinkCaret.setSelected(editorSettings.isBlinkCaret());
    myBlinkIntervalField.setText(Integer.toString(editorSettings.getBlinkPeriod()));
    myBlinkIntervalField.setEnabled(editorSettings.isBlinkCaret());
    myCbRightMargin.setSelected(editorSettings.isRightMarginShown());
    myCbShowLineNumbers.setSelected(editorSettings.isLineNumbersShown());
    myCbBlockCursor.setSelected(editorSettings.isBlockCursor());
    myCbShowWhitespaces.setSelected(editorSettings.isWhitespacesShown());
    myCbShowSoftwraps.setSelected(editorSettings.isSoftWrapsShown());
    myShowVerticalIndentGuidesCheckBox.setSelected(editorSettings.isIndentGuidesShown());
    myAntialiasingInEditorCheckBox.setSelected(UISettings.getInstance().ANTIALIASING_IN_EDITOR);
    myCbShowIconsInGutter.setSelected(DaemonCodeAnalyzerSettings.getInstance().SHOW_SMALL_ICONS_IN_GUTTER);

    super.reset();
  }

  @Override
  public void apply() throws ConfigurationException {
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    editorSettings.setBlinkCaret(myCbBlinkCaret.isSelected());
    try {
      editorSettings.setBlinkPeriod(Integer.parseInt(myBlinkIntervalField.getText()));
    }
    catch (NumberFormatException e) {
    }

    editorSettings.setBlockCursor(myCbBlockCursor.isSelected());
    editorSettings.setRightMarginShown(myCbRightMargin.isSelected());
    editorSettings.setLineNumbersShown(myCbShowLineNumbers.isSelected());
    editorSettings.setWhitespacesShown(myCbShowWhitespaces.isSelected());
    editorSettings.setSoftwrapsShown(myCbShowSoftwraps.isSelected());
    editorSettings.setIndentGuidesShown(myShowVerticalIndentGuidesCheckBox.isSelected());

    EditorOptionsPanel.reinitAllEditors();

    DaemonCodeAnalyzerSettings.getInstance().SHOW_METHOD_SEPARATORS = myCbShowMethodSeparators.isSelected();
    DaemonCodeAnalyzerSettings.getInstance().SHOW_SMALL_ICONS_IN_GUTTER = myCbShowIconsInGutter.isSelected();

    UISettings uiSettings = UISettings.getInstance();
    if (uiSettings.ANTIALIASING_IN_EDITOR != myAntialiasingInEditorCheckBox.isSelected()) {
      uiSettings.ANTIALIASING_IN_EDITOR = myAntialiasingInEditorCheckBox.isSelected();
      LafManager.getInstance().repaintUI();
      uiSettings.fireUISettingsChanged();
    }

    EditorOptionsPanel.restartDaemons();

    super.apply();
  }

  @Override
  public boolean isModified() {
    if (super.isModified()) return true;
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    boolean isModified = isModified(myCbBlinkCaret, editorSettings.isBlinkCaret());
    isModified |= isModified(myBlinkIntervalField, editorSettings.getBlinkPeriod());

    isModified |= isModified(myCbBlockCursor, editorSettings.isBlockCursor());

    isModified |= isModified(myCbRightMargin, editorSettings.isRightMarginShown());

    isModified |= isModified(myCbShowLineNumbers, editorSettings.isLineNumbersShown());
    isModified |= isModified(myCbShowWhitespaces, editorSettings.isWhitespacesShown());
    isModified |= isModified(myCbShowSoftwraps, editorSettings.isSoftWrapsShown());
    isModified |= isModified(myShowVerticalIndentGuidesCheckBox, editorSettings.isIndentGuidesShown());
    isModified |= isModified(myCbShowMethodSeparators, DaemonCodeAnalyzerSettings.getInstance().SHOW_METHOD_SEPARATORS);
    isModified |= isModified(myCbShowIconsInGutter, DaemonCodeAnalyzerSettings.getInstance().SHOW_SMALL_ICONS_IN_GUTTER);
    isModified |= myAntialiasingInEditorCheckBox.isSelected() != UISettings.getInstance().ANTIALIASING_IN_EDITOR;

    return isModified;
  }

  private static boolean isModified(JToggleButton checkBox, boolean value) {
    return checkBox.isSelected() != value;
  }

  private static boolean isModified(JTextField textField, int value) {
    try {
      int fieldValue = Integer.parseInt(textField.getText().trim());
      return fieldValue != value;
    }
    catch(NumberFormatException e) {
      return false;
    }
  }

  @Nls
  public String getDisplayName() {
    return ApplicationBundle.message("tab.editor.settings.appearance");
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return "reference.settingsdialog.IDE.editor.appearance";
  }

  public JComponent createComponent() {
    for (UnnamedConfigurable provider : getConfigurables()) {
      myAddonPanel.add(provider.createComponent(), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0, 0, GridBagConstraints.NORTHWEST,
                                                                            GridBagConstraints.NONE, new Insets(0,0,0,0), 0,0));
    }
    return myRootPanel;
  }

  protected List<UnnamedConfigurable> createConfigurables() {
    return Arrays.asList(Extensions.getExtensions(EP_NAME));
  }

  public String getId() {
    return "editor.preferences.appearance";
  }

  public Runnable enableSearch(final String option) {
    return null;
  }
}
