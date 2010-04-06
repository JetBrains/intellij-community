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
package com.intellij.application.options.codeStyle;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.OptionGroup;

import javax.swing.*;
import java.awt.*;

public class CodeStyleBlankLinesPanel extends MultilanguageCodeStyleAbstractPanel {
  private JTextField myKeepBlankLinesInDeclarations;
  private JTextField myKeepBlankLinesInCode;
  private JTextField myBlankLinesBeforePackage;
  private JTextField myBlankLinesAfterPackage;
  private JTextField myBlankLinesBeforeImports;
  private JTextField myBlankLinesAfterImports;
  private JTextField myBlankLinesAroundClass;
  private JTextField myBlankLinesAroundField;
  private JTextField myBlankLinesAroundMethod;
  private JTextField myBlankLinesAroundFieldI;
  private JTextField myBlankLinesAroundMethodI;
  private JTextField myBlankLinesAfterClassHeader;
  private JTextField myKeepBlankLinesBeforeRBrace;

  private final JPanel myPanel = new JPanel(new GridBagLayout());

  private static final LanguageFileType myFileType = StdFileTypes.JSP; //TODO: Replace hardcoded value

  public CodeStyleBlankLinesPanel(CodeStyleSettings settings) {
    super(settings);

    myPanel
      .add(createKeepBlankLinesPanel(),
           new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0, 4, 0, 4), 0, 0));
    myPanel
      .add(createBlankLinesPanel(),
           new GridBagConstraints(0, 1, 1, 1, 0, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0, 4, 0, 4), 0, 0));

    final JPanel previewPanel = createPreviewPanel();
    myPanel
      .add(previewPanel,
           new GridBagConstraints(1, 0, 1, 2, 1, 1, GridBagConstraints.NORTH, GridBagConstraints.BOTH, new Insets(0, 0, 0, 4), 0, 0));

    installPreviewPanel(previewPanel);
    addPanelToWatch(myPanel);

  }

  @Override
  protected LanguageCodeStyleSettingsProvider.SettingsType getSettingsType() {
    return LanguageCodeStyleSettingsProvider.SettingsType.BLANK_LINE_SETTINGS;
  }

  private JPanel createBlankLinesPanel() {
    OptionGroup optionGroup = new OptionGroup(ApplicationBundle.message("title.blank.lines"));

    myBlankLinesBeforePackage = createTextField();
    optionGroup.add(new JLabel(ApplicationBundle.message("editbox.blanklines.before.package.statement")), myBlankLinesBeforePackage);

    myBlankLinesAfterPackage = createTextField();
    optionGroup.add(new JLabel(ApplicationBundle.message("editbox.blanklines.after.package.statement")), myBlankLinesAfterPackage);

    myBlankLinesBeforeImports = createTextField();
    optionGroup.add(new JLabel(ApplicationBundle.message("editbox.blanklines.before.imports")), myBlankLinesBeforeImports);

    myBlankLinesAfterImports = createTextField();
    optionGroup.add(new JLabel(ApplicationBundle.message("editbox.blanklines.after.imports")), myBlankLinesAfterImports);

    myBlankLinesAroundClass = createTextField();
    optionGroup.add(new JLabel(ApplicationBundle.message("editbox.blanklines.around.class")), myBlankLinesAroundClass);

    myBlankLinesAroundField = createTextField();
    optionGroup.add(new JLabel(ApplicationBundle.message("editbox.blanklines.around.field")), myBlankLinesAroundField);

    myBlankLinesAroundMethod = createTextField();
    optionGroup.add(new JLabel(ApplicationBundle.message("editbox.blanklines.around.method")), myBlankLinesAroundMethod);

    myBlankLinesAroundFieldI = createTextField();
    optionGroup.add(new JLabel("Around field in interface:"), myBlankLinesAroundFieldI);

    myBlankLinesAroundMethodI = createTextField();
    optionGroup.add(new JLabel("Around method in interface:"), myBlankLinesAroundMethodI);

    myBlankLinesAfterClassHeader = createTextField();
    optionGroup.add(new JLabel(ApplicationBundle.message("editbox.blanklines.after.class.header")), myBlankLinesAfterClassHeader);

    return optionGroup.createPanel();
  }

  private JPanel createKeepBlankLinesPanel() {
    OptionGroup optionGroup = new OptionGroup(ApplicationBundle.message("title.keep.blank.lines"));

    myKeepBlankLinesInDeclarations = createTextField();
    optionGroup.add(new JLabel(ApplicationBundle.message("editbox.keep.blanklines.in.declarations")), myKeepBlankLinesInDeclarations);

    myKeepBlankLinesInCode = createTextField();
    optionGroup.add(new JLabel(ApplicationBundle.message("editbox.keep.blanklines.in.code")), myKeepBlankLinesInCode);

    myKeepBlankLinesBeforeRBrace = createTextField();
    optionGroup.add(new JLabel(ApplicationBundle.message("editbox.keep.blanklines.before.rbrace")), myKeepBlankLinesBeforeRBrace);

    return optionGroup.createPanel();
  }

  protected void resetImpl(final CodeStyleSettings settings) {
    myKeepBlankLinesInDeclarations.setText(String.valueOf(settings.KEEP_BLANK_LINES_IN_DECLARATIONS));
    myKeepBlankLinesInCode.setText(String.valueOf(settings.KEEP_BLANK_LINES_IN_CODE));
    myKeepBlankLinesBeforeRBrace.setText(String.valueOf(settings.KEEP_BLANK_LINES_BEFORE_RBRACE));
    myBlankLinesBeforePackage.setText(String.valueOf(settings.BLANK_LINES_BEFORE_PACKAGE));
    myBlankLinesAfterPackage.setText(String.valueOf(settings.BLANK_LINES_AFTER_PACKAGE));
    myBlankLinesBeforeImports.setText(String.valueOf(settings.BLANK_LINES_BEFORE_IMPORTS));
    myBlankLinesAfterImports.setText(String.valueOf(settings.BLANK_LINES_AFTER_IMPORTS));
    myBlankLinesAroundClass.setText(String.valueOf(settings.BLANK_LINES_AROUND_CLASS));
    myBlankLinesAroundField.setText(String.valueOf(settings.BLANK_LINES_AROUND_FIELD));
    myBlankLinesAroundMethod.setText(String.valueOf(settings.BLANK_LINES_AROUND_METHOD));
    myBlankLinesAroundFieldI.setText(String.valueOf(settings.BLANK_LINES_AROUND_FIELD_IN_INTERFACE));
    myBlankLinesAroundMethodI.setText(String.valueOf(settings.BLANK_LINES_AROUND_METHOD_IN_INTERFACE));
    myBlankLinesAfterClassHeader.setText(String.valueOf(settings.BLANK_LINES_AFTER_CLASS_HEADER));

  }

  public void apply(CodeStyleSettings settings) {
    settings.KEEP_BLANK_LINES_IN_DECLARATIONS = getValue(myKeepBlankLinesInDeclarations);
    settings.KEEP_BLANK_LINES_IN_CODE = getValue(myKeepBlankLinesInCode);
    settings.KEEP_BLANK_LINES_BEFORE_RBRACE = getValue(myKeepBlankLinesBeforeRBrace);
    settings.BLANK_LINES_BEFORE_PACKAGE = getValue(myBlankLinesBeforePackage);
    settings.BLANK_LINES_AFTER_PACKAGE = getValue(myBlankLinesAfterPackage);
    settings.BLANK_LINES_BEFORE_IMPORTS = getValue(myBlankLinesBeforeImports);
    settings.BLANK_LINES_AFTER_IMPORTS = getValue(myBlankLinesAfterImports);
    settings.BLANK_LINES_AROUND_CLASS = getValue(myBlankLinesAroundClass);
    settings.BLANK_LINES_AROUND_FIELD = getValue(myBlankLinesAroundField);
    settings.BLANK_LINES_AROUND_METHOD = getValue(myBlankLinesAroundMethod);

    settings.BLANK_LINES_AROUND_FIELD_IN_INTERFACE = getValue(myBlankLinesAroundFieldI);
    settings.BLANK_LINES_AROUND_METHOD_IN_INTERFACE = getValue(myBlankLinesAroundMethodI);

    settings.BLANK_LINES_AFTER_CLASS_HEADER = getValue(myBlankLinesAfterClassHeader);

  }

  public boolean isModified(CodeStyleSettings settings) {
    boolean isModified;
    isModified = settings.KEEP_BLANK_LINES_IN_DECLARATIONS != getValue(myKeepBlankLinesInDeclarations);
    isModified |= settings.KEEP_BLANK_LINES_IN_CODE != getValue(myKeepBlankLinesInCode);
    isModified |= settings.KEEP_BLANK_LINES_BEFORE_RBRACE != getValue(myKeepBlankLinesBeforeRBrace);
    isModified |= settings.BLANK_LINES_BEFORE_PACKAGE != getValue(myBlankLinesBeforePackage);
    isModified |= settings.BLANK_LINES_AFTER_PACKAGE != getValue(myBlankLinesAfterPackage);
    isModified |= settings.BLANK_LINES_BEFORE_IMPORTS != getValue(myBlankLinesBeforeImports);
    isModified |= settings.BLANK_LINES_AFTER_IMPORTS != getValue(myBlankLinesAfterImports);
    isModified |= settings.BLANK_LINES_AROUND_CLASS != getValue(myBlankLinesAroundClass);
    isModified |= settings.BLANK_LINES_AROUND_FIELD != getValue(myBlankLinesAroundField);
    isModified |= settings.BLANK_LINES_AROUND_METHOD != getValue(myBlankLinesAroundMethod);
    isModified |= settings.BLANK_LINES_AROUND_FIELD_IN_INTERFACE != getValue(myBlankLinesAroundFieldI);
    isModified |= settings.BLANK_LINES_AROUND_METHOD_IN_INTERFACE != getValue(myBlankLinesAroundMethodI);
    isModified |= settings.BLANK_LINES_AFTER_CLASS_HEADER != getValue(myBlankLinesAfterClassHeader);
    return isModified;

  }

  private static int getValue(JTextField textField) {
    int ret = 0;
    try {
      ret = Integer.parseInt(textField.getText());
      if (ret < 0) {
        ret = 0;
      }
      if (ret > 10) {
        ret = 10;
      }
    }
    catch (NumberFormatException e) {
      //bad number entered
    }
    return ret;
  }

  private static JTextField createTextField() {

    return new JTextField(6);
  }

  protected int getRightMargin() {
    return 37;
  }

  public JComponent getPanel() {
    return myPanel;
  }

  protected void prepareForReformat(final PsiFile psiFile) {
    //psiFile.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, LanguageLevel.HIGHEST);
  }
}
