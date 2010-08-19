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

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.intellij.ui.OptionGroup;

import javax.swing.*;
import java.awt.*;

public class CodeStyleIndentAndBracesPanel extends MultilanguageCodeStyleAbstractPanel {
  private static final String[] BRACE_PLACEMENT_OPTIONS = new String[]{
    ApplicationBundle.message("combobox.brace.placement.end.of.line"),
    ApplicationBundle.message("combobox.brace.placement.next.line.if.wrapped"),
    ApplicationBundle.message("combobox.brace.placement.next.line"),
    ApplicationBundle.message("combobox.brace.placement.next.line.shifted"),
    ApplicationBundle.message("combobox.brace.placement.next.line.shifted2")
  };

  private static final int[] BRACE_PLACEMENT_VALUES = new int[] {
    CodeStyleSettings.END_OF_LINE,
    CodeStyleSettings.NEXT_LINE_IF_WRAPPED,
    CodeStyleSettings.NEXT_LINE,
    CodeStyleSettings.NEXT_LINE_SHIFTED,
    CodeStyleSettings.NEXT_LINE_SHIFTED2
  };

  private static final String[] BRACE_FORCE_OPTIONS = new String[]{
    ApplicationBundle.message("combobox.force.braces.do.not.force"),
    ApplicationBundle.message("combobox.force.braces.when.multiline"),
    ApplicationBundle.message("combobox.force.braces.always")
  };

  private static final int[] BRACE_FORCE_VALUES = new int[]{
    CodeStyleSettings.DO_NOT_FORCE,
    CodeStyleSettings.FORCE_BRACES_IF_MULTILINE,
    CodeStyleSettings.FORCE_BRACES_ALWAYS
  };

  private JComboBox myClassDeclarationCombo = new JComboBox();
  private JComboBox myMethodDeclarationCombo = new JComboBox();
  private JComboBox myOtherCombo = new JComboBox();

  private JCheckBox myCbElseOnNewline;
  private JCheckBox myCbWhileOnNewline;
  private JCheckBox myCbCatchOnNewline;
  private JCheckBox myCbFinallyOnNewline;

  private JCheckBox myCbSpecialElseIfTreatment;
  private JCheckBox myCbIndentCaseFromSwitch;


  private JComboBox myIfForceCombo;
  private JComboBox myForForceCombo;
  private JComboBox myWhileForceCombo;
  private JComboBox myDoWhileForceCombo;

  private JCheckBox myAlignChainedMethods;
  private JCheckBox myAlignDeclarationParameters;
  private JCheckBox myAlignCallParameters;
  private JCheckBox myAlignMethodBrackets;
  private JCheckBox myAlignExtendsList;
  private JCheckBox myAlignForStatement;
  private JCheckBox myAlignThrowsList;
  private JCheckBox myAlignSubsequentDeclarations;
  private JCheckBox myAlignParenthesizedExpression;
  private JCheckBox myAlignBinaryExpression;
  private JCheckBox myAlignTernaryExpression;
  private JCheckBox myAlignAssignment;
  private JCheckBox myAlignArrayInitializerExpression;
  private JCheckBox myKeepLineBreaks;
  private JCheckBox myKeepCommentAtFirstColumn;
  private JCheckBox myKeepMethodsInOneLine;
  private JCheckBox myKeepSimpleBlocksInOneLine;
  private JCheckBox myKeepControlStatementInOneLine;

  private final JPanel myPanel = new JPanel(new GridBagLayout());

  public CodeStyleIndentAndBracesPanel(CodeStyleSettings settings) {
    super(settings);

    init();
  }

  @Override
  protected void init() {
    super.init();

    myPanel.add(createKeepWhenReformattingPanel(),
                new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL,
                                       new Insets(0, 4, 0, 4), 0, 0));

    myPanel.add(createBracesPanel(),
                new GridBagConstraints(0, 1, 1, 1, 0, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL,
                                       new Insets(0, 4, 0, 4), 0, 0));

    myPanel.add(createAlignmentsPanel(),
                new GridBagConstraints(1, 0, 1, 2, 0, 0, GridBagConstraints.NORTH, GridBagConstraints.BOTH,
                                       new Insets(0, 4, 0, 4), 0, 0));

    myPanel.add(createPlaceOnNewLinePanel(),
                new GridBagConstraints(1, 2, 1, 1, 0, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL,
                                       new Insets(0, 4, 0, 4), 0, 0));
    myPanel.add(createForceBracesPanel(),
                new GridBagConstraints(0, 2, 1, 1, 0, 0, GridBagConstraints.NORTH, GridBagConstraints.BOTH,
                                       new Insets(0, 4, 0, 4), 0, 0));

    myPanel.add(new JPanel() {
      public Dimension getPreferredSize() {
        return new Dimension(1, 1);
      }
    }, new GridBagConstraints(0, 3, 2, 1, 0, 0, GridBagConstraints.NORTH, GridBagConstraints.NONE,
                              new Insets(0, 0, 0, 0), 0, 0));

    final JPanel previewPanel = createPreviewPanel();
    myPanel.add(previewPanel,
                new GridBagConstraints(2, 0, 1, 4, 1, 1, GridBagConstraints.NORTH, GridBagConstraints.BOTH,
                                       new Insets(0, 0, 0, 4), 0, 0));
    installPreviewPanel(previewPanel);
    addPanelToWatch(myPanel);
  }

  @Override
  protected LanguageCodeStyleSettingsProvider.SettingsType getSettingsType() {
    return LanguageCodeStyleSettingsProvider.SettingsType.INDENTS_AND_BRACES_SETTINGS;
  }

  @Override
  public void showAllStandardOptions() {
    // TODO: IMPLEMENT
  }

  @Override
  public void showStandardOptions(String... optionNames) {
    // TODO: IMPLEMENT
  }

  @Override
  public void showCustomOption(Class<? extends CustomCodeStyleSettings> settingsClass,
                               String fieldName,
                               String optionName,
                               String groupName) {
    // TODO: IMPLEMENT
  }

  private Component createKeepWhenReformattingPanel() {
    OptionGroup optionGroup = new OptionGroup(ApplicationBundle.message("title.keep.when.reformatting"));

    myKeepLineBreaks = createCheckBox(ApplicationBundle.message("checkbox.keep.when.reformatting.line.breaks"));
    optionGroup.add(myKeepLineBreaks);

    myKeepCommentAtFirstColumn = createCheckBox(ApplicationBundle.message("checkbox.keep.when.reformatting.comment.at.first.column"));
    optionGroup.add(myKeepCommentAtFirstColumn);

    myKeepMethodsInOneLine = createCheckBox(ApplicationBundle.message("checkbox.keep.when.reformatting.simple.methods.in.one.line"));
    optionGroup.add(myKeepMethodsInOneLine);

    myKeepSimpleBlocksInOneLine = createCheckBox(ApplicationBundle.message("checkbox.keep.when.reformatting.simple.blocks.in.one.line"));
    optionGroup.add(myKeepSimpleBlocksInOneLine);

    myKeepControlStatementInOneLine = createCheckBox(ApplicationBundle.message("checkbox.keep.when.reformatting.control.statement.in.one.line"));
    optionGroup.add(myKeepControlStatementInOneLine);


    return optionGroup.createPanel();

  }

  private JPanel createBracesPanel() {
    OptionGroup optionGroup = new OptionGroup(ApplicationBundle.message("title.braces.placement"));

    myClassDeclarationCombo = createBraceStyleCombo();
    optionGroup.add(new JLabel(ApplicationBundle.message("combobox.brace.placement.class.declaration")), myClassDeclarationCombo);

    myMethodDeclarationCombo = createBraceStyleCombo();
    optionGroup.add(new JLabel(ApplicationBundle.message("combobox.brace.placement.method.declaration")), myMethodDeclarationCombo);

    myOtherCombo = createBraceStyleCombo();
    optionGroup.add(new JLabel(ApplicationBundle.message("combobox.brace.placement.other")), myOtherCombo);

    myCbSpecialElseIfTreatment = createCheckBox(ApplicationBundle.message("checkbox.brace.special.else.if.treatment"));
    optionGroup.add(myCbSpecialElseIfTreatment);

    myCbIndentCaseFromSwitch = createCheckBox(ApplicationBundle.message("checkbox.brace.indent.case.from.switch"));
    optionGroup.add(myCbIndentCaseFromSwitch);

    return optionGroup.createPanel();
  }

  private JPanel createForceBracesPanel() {
    OptionGroup optionGroup = new OptionGroup(ApplicationBundle.message("title.force.braces"));

    myIfForceCombo = createForceBracesCombo();
    optionGroup.add(new JLabel(ApplicationBundle.message("combobox.force.braces.if")), myIfForceCombo);

    myForForceCombo = createForceBracesCombo();
    optionGroup.add(new JLabel(ApplicationBundle.message("combobox.force.braces.for")), myForForceCombo);

    myWhileForceCombo = createForceBracesCombo();
    optionGroup.add(new JLabel(ApplicationBundle.message("combobox.force.braces.while")), myWhileForceCombo);

    myDoWhileForceCombo = createForceBracesCombo();
    optionGroup.add(new JLabel(ApplicationBundle.message("combobox.force.braces.do.while")), myDoWhileForceCombo);

    return optionGroup.createPanel();
  }

  private JPanel createAlignmentsPanel() {
    OptionGroup optionGroup = new OptionGroup(ApplicationBundle.message("title.align.when.multiline"));

    myAlignChainedMethods = createCheckBox(ApplicationBundle.message("checkbox.align.multiline.chained.methods"));
    optionGroup.add(myAlignChainedMethods);

    myAlignDeclarationParameters = createCheckBox(ApplicationBundle.message("checkbox.align.multiline.method.parameters"));
    optionGroup.add(myAlignDeclarationParameters);

    myAlignCallParameters = createCheckBox(ApplicationBundle.message("checkbox.align.multiline.call.arguments"));
    optionGroup.add(myAlignCallParameters);

    myAlignMethodBrackets = createCheckBox(ApplicationBundle.message("checkbox.align.multiline.method.parentheses"));
    optionGroup.add(myAlignMethodBrackets);

    myAlignExtendsList = createCheckBox(ApplicationBundle.message("checkbox.align.multiline.extends.list"));
    optionGroup.add(myAlignExtendsList);

    myAlignThrowsList = createCheckBox(ApplicationBundle.message("checkbox.align.multiline.throws.list"));
    optionGroup.add(myAlignThrowsList);

    myAlignSubsequentDeclarations = createCheckBox(ApplicationBundle.message("checkbox.align.multiline.fields.variables.groups"));
    optionGroup.add(myAlignSubsequentDeclarations);

    myAlignParenthesizedExpression = createCheckBox(ApplicationBundle.message("checkbox.align.multiline.parenthesized.expression"));
    optionGroup.add(myAlignParenthesizedExpression);

    myAlignBinaryExpression = createCheckBox(ApplicationBundle.message("checkbox.align.multiline.binary.operation"));
    optionGroup.add(myAlignBinaryExpression);

    myAlignTernaryExpression = createCheckBox(ApplicationBundle.message("checkbox.align.multiline.ternary.operation"));
    optionGroup.add(myAlignTernaryExpression);

    myAlignAssignment = createCheckBox(ApplicationBundle.message("checkbox.align.multiline.assignments"));
    optionGroup.add(myAlignAssignment);

    myAlignForStatement = createCheckBox(ApplicationBundle.message("checkbox.align.multiline.for.statement"));
    optionGroup.add(myAlignForStatement);

    myAlignArrayInitializerExpression = createCheckBox(ApplicationBundle.message("checkbox.align.multiline.array.initializer"));
    optionGroup.add(myAlignArrayInitializerExpression);

    return optionGroup.createPanel();
  }

  private JPanel createPlaceOnNewLinePanel() {
    OptionGroup optionGroup = new OptionGroup(ApplicationBundle.message("title.place.on.new.line"));

    myCbElseOnNewline = createCheckBox(ApplicationBundle.message("checkbox.place.else.on.new.line"));
    optionGroup.add(myCbElseOnNewline);

    myCbWhileOnNewline = createCheckBox(ApplicationBundle.message("checkbox.place.while.on.new.line"));
    optionGroup.add(myCbWhileOnNewline);

    myCbCatchOnNewline = createCheckBox(ApplicationBundle.message("checkbox.place.catch.on.new.line"));
    optionGroup.add(myCbCatchOnNewline);

    myCbFinallyOnNewline = createCheckBox(ApplicationBundle.message("checkbox.place.finally.on.new.line"));
    optionGroup.add(myCbFinallyOnNewline);

    return optionGroup.createPanel();
  }

  private static JCheckBox createCheckBox(String text) {
    return new JCheckBox(text);
  }

  private static JComboBox createForceBracesCombo() {
    return new JComboBox(BRACE_FORCE_OPTIONS);
  }

  private static void setForceBracesComboValue(JComboBox comboBox, int value) {
    for (int i = 0; i < BRACE_FORCE_VALUES.length; i++) {
      int forceValue = BRACE_FORCE_VALUES[i];
      if (forceValue == value) {
        comboBox.setSelectedItem(BRACE_FORCE_OPTIONS[i]);
      }
    }
  }

  private static int getForceBracesValue(JComboBox comboBox) {
    String selected = (String)comboBox.getSelectedItem();
    for (int i = 0; i < BRACE_FORCE_OPTIONS.length; i++) {
      String s = BRACE_FORCE_OPTIONS[i];
      if (s.equals(selected)) {
        return BRACE_FORCE_VALUES[i];
      }
    }
    return 0;
  }

  private static JComboBox createBraceStyleCombo() {
    return new JComboBox(BRACE_PLACEMENT_OPTIONS);
  }

  private static void setBraceStyleComboValue(JComboBox comboBox, int value) {
    for (int i = 0; i < BRACE_PLACEMENT_OPTIONS.length; i++) {
      if (BRACE_PLACEMENT_VALUES[i] == value) {
        comboBox.setSelectedItem(BRACE_PLACEMENT_OPTIONS[i]);
        return;
      }
    }
  }

  private static int getBraceComboValue(JComboBox comboBox) {
    Object item = comboBox.getSelectedItem();
    for (int i = 1; i < BRACE_PLACEMENT_OPTIONS.length; i++) {
      if (BRACE_PLACEMENT_OPTIONS[i].equals(item)) {
        return BRACE_PLACEMENT_VALUES[i];
      }
    }
    return BRACE_PLACEMENT_VALUES[0];
  }

  public boolean isModified(CodeStyleSettings settings) {
    boolean isModified;
    isModified = isModified(myCbElseOnNewline, settings.ELSE_ON_NEW_LINE);
    isModified |= isModified(myCbWhileOnNewline, settings.WHILE_ON_NEW_LINE);
    isModified |= isModified(myCbCatchOnNewline, settings.CATCH_ON_NEW_LINE);
    isModified |= isModified(myCbFinallyOnNewline, settings.FINALLY_ON_NEW_LINE);


    isModified |= isModified(myCbSpecialElseIfTreatment, settings.SPECIAL_ELSE_IF_TREATMENT);
    isModified |= isModified(myCbIndentCaseFromSwitch, settings.INDENT_CASE_FROM_SWITCH);


    isModified |= settings.BRACE_STYLE != getBraceComboValue(myOtherCombo);
    isModified |= settings.CLASS_BRACE_STYLE != getBraceComboValue(myClassDeclarationCombo);
    isModified |= settings.METHOD_BRACE_STYLE != getBraceComboValue(myMethodDeclarationCombo);

    isModified |= isModified(myAlignChainedMethods, settings.ALIGN_MULTILINE_CHAINED_METHODS);
    isModified |= isModified(myAlignAssignment, settings.ALIGN_MULTILINE_ASSIGNMENT);
    isModified |= isModified(myAlignBinaryExpression, settings.ALIGN_MULTILINE_BINARY_OPERATION);
    isModified |= isModified(myAlignCallParameters, settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS);
    isModified |= isModified(myAlignMethodBrackets, settings.ALIGN_MULTILINE_METHOD_BRACKETS);
    isModified |= isModified(myAlignDeclarationParameters, settings.ALIGN_MULTILINE_PARAMETERS);
    isModified |= isModified(myAlignExtendsList, settings.ALIGN_MULTILINE_EXTENDS_LIST);
    isModified |= isModified(myAlignForStatement, settings.ALIGN_MULTILINE_FOR);
    isModified |= isModified(myAlignParenthesizedExpression, settings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION);
    isModified |= isModified(myAlignTernaryExpression, settings.ALIGN_MULTILINE_TERNARY_OPERATION);
    isModified |= isModified(myAlignThrowsList, settings.ALIGN_MULTILINE_THROWS_LIST);
    isModified |= isModified(myAlignSubsequentDeclarations, settings.ALIGN_GROUP_FIELDS_VARIABLES);
    isModified |= isModified(myAlignArrayInitializerExpression, settings.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION);

    isModified |= settings.FOR_BRACE_FORCE != getForceBracesValue(myForForceCombo);
    isModified |= settings.IF_BRACE_FORCE != getForceBracesValue(myIfForceCombo);
    isModified |= settings.WHILE_BRACE_FORCE != getForceBracesValue(myWhileForceCombo);
    isModified |= settings.DOWHILE_BRACE_FORCE != getForceBracesValue(myDoWhileForceCombo);

    isModified |= isModified(myKeepLineBreaks, settings.KEEP_LINE_BREAKS);
    isModified |= isModified(myKeepCommentAtFirstColumn, settings.KEEP_FIRST_COLUMN_COMMENT);
    isModified |= isModified(myKeepControlStatementInOneLine, settings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE);
    isModified |= isModified(myKeepSimpleBlocksInOneLine, settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
    isModified |= isModified(myKeepMethodsInOneLine, settings.KEEP_SIMPLE_METHODS_IN_ONE_LINE);


    return isModified;

  }

  private static boolean isModified(JCheckBox checkBox, boolean value) {
    return checkBox.isSelected() != value;
  }

  protected void resetImpl(final CodeStyleSettings settings) {
    myCbElseOnNewline.setSelected(settings.ELSE_ON_NEW_LINE);
    myCbWhileOnNewline.setSelected(settings.WHILE_ON_NEW_LINE);
    myCbCatchOnNewline.setSelected(settings.CATCH_ON_NEW_LINE);
    myCbFinallyOnNewline.setSelected(settings.FINALLY_ON_NEW_LINE);

    myCbSpecialElseIfTreatment.setSelected(settings.SPECIAL_ELSE_IF_TREATMENT);
    myCbIndentCaseFromSwitch.setSelected(settings.INDENT_CASE_FROM_SWITCH);

    setBraceStyleComboValue(myOtherCombo, settings.BRACE_STYLE);
    setBraceStyleComboValue(myClassDeclarationCombo, settings.CLASS_BRACE_STYLE);
    setBraceStyleComboValue(myMethodDeclarationCombo, settings.METHOD_BRACE_STYLE);

    myAlignAssignment.setSelected(settings.ALIGN_MULTILINE_ASSIGNMENT);
    myAlignBinaryExpression.setSelected(settings.ALIGN_MULTILINE_BINARY_OPERATION);
    myAlignCallParameters.setSelected(settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS);
    myAlignMethodBrackets.setSelected(settings.ALIGN_MULTILINE_METHOD_BRACKETS);
    myAlignChainedMethods.setSelected(settings.ALIGN_MULTILINE_CHAINED_METHODS);
    myAlignDeclarationParameters.setSelected(settings.ALIGN_MULTILINE_PARAMETERS);
    myAlignExtendsList.setSelected(settings.ALIGN_MULTILINE_EXTENDS_LIST);
    myAlignForStatement.setSelected(settings.ALIGN_MULTILINE_FOR);
    myAlignParenthesizedExpression.setSelected(settings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION);
    myAlignTernaryExpression.setSelected(settings.ALIGN_MULTILINE_TERNARY_OPERATION);
    myAlignThrowsList.setSelected(settings.ALIGN_MULTILINE_THROWS_LIST);
    myAlignSubsequentDeclarations.setSelected(settings.ALIGN_GROUP_FIELDS_VARIABLES);
    myAlignArrayInitializerExpression.setSelected(settings.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION);

    setForceBracesComboValue(myForForceCombo, settings.FOR_BRACE_FORCE);
    setForceBracesComboValue(myIfForceCombo, settings.IF_BRACE_FORCE);
    setForceBracesComboValue(myWhileForceCombo, settings.WHILE_BRACE_FORCE);
    setForceBracesComboValue(myDoWhileForceCombo, settings.DOWHILE_BRACE_FORCE);

    myKeepLineBreaks.setSelected(settings.KEEP_LINE_BREAKS);
    myKeepCommentAtFirstColumn.setSelected(settings.KEEP_FIRST_COLUMN_COMMENT);
    myKeepControlStatementInOneLine.setSelected(settings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE);
    myKeepSimpleBlocksInOneLine.setSelected(settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
    myKeepMethodsInOneLine.setSelected(settings.KEEP_SIMPLE_METHODS_IN_ONE_LINE);

  }

  public void apply(CodeStyleSettings settings) {
    settings.ELSE_ON_NEW_LINE = myCbElseOnNewline.isSelected();
    settings.WHILE_ON_NEW_LINE = myCbWhileOnNewline.isSelected();
    settings.CATCH_ON_NEW_LINE = myCbCatchOnNewline.isSelected();
    settings.FINALLY_ON_NEW_LINE = myCbFinallyOnNewline.isSelected();


    settings.SPECIAL_ELSE_IF_TREATMENT = myCbSpecialElseIfTreatment.isSelected();
    settings.INDENT_CASE_FROM_SWITCH = myCbIndentCaseFromSwitch.isSelected();

    settings.BRACE_STYLE = getBraceComboValue(myOtherCombo);
    settings.CLASS_BRACE_STYLE = getBraceComboValue(myClassDeclarationCombo);
    settings.METHOD_BRACE_STYLE = getBraceComboValue(myMethodDeclarationCombo);

    settings.ALIGN_MULTILINE_ASSIGNMENT = myAlignAssignment.isSelected();
    settings.ALIGN_MULTILINE_BINARY_OPERATION = myAlignBinaryExpression.isSelected();
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = myAlignCallParameters.isSelected();
    settings.ALIGN_MULTILINE_METHOD_BRACKETS = myAlignMethodBrackets.isSelected();
    settings.ALIGN_MULTILINE_CHAINED_METHODS = myAlignChainedMethods.isSelected();
    settings.ALIGN_MULTILINE_PARAMETERS = myAlignDeclarationParameters.isSelected();
    settings.ALIGN_MULTILINE_EXTENDS_LIST = myAlignExtendsList.isSelected();
    settings.ALIGN_MULTILINE_FOR = myAlignForStatement.isSelected();
    settings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = myAlignParenthesizedExpression.isSelected();
    settings.ALIGN_MULTILINE_TERNARY_OPERATION = myAlignTernaryExpression.isSelected();
    settings.ALIGN_MULTILINE_THROWS_LIST = myAlignThrowsList.isSelected();
    settings.ALIGN_GROUP_FIELDS_VARIABLES = myAlignSubsequentDeclarations.isSelected();
    settings.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = myAlignArrayInitializerExpression.isSelected();
//    mySettings.LABEL_INDENT =

    settings.FOR_BRACE_FORCE = getForceBracesValue(myForForceCombo);
    settings.IF_BRACE_FORCE = getForceBracesValue(myIfForceCombo);
    settings.WHILE_BRACE_FORCE = getForceBracesValue(myWhileForceCombo);
    settings.DOWHILE_BRACE_FORCE = getForceBracesValue(myDoWhileForceCombo);

    settings.KEEP_LINE_BREAKS = myKeepLineBreaks.isSelected();
    settings.KEEP_FIRST_COLUMN_COMMENT = myKeepCommentAtFirstColumn.isSelected();
    settings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE = myKeepControlStatementInOneLine.isSelected();
    settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = myKeepSimpleBlocksInOneLine.isSelected();
    settings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = myKeepMethodsInOneLine.isSelected();

  }

  protected int getRightMargin() {
    return -1;
  }

  public JComponent getPanel() {
    return myPanel;
  }

  protected void prepareForReformat(final PsiFile psiFile) {
    //psiFile.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, LanguageLevel.HIGHEST);
  }
}