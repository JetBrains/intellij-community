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
import com.intellij.psi.codeStyle.CodeStyleSettings;

/**
 * @author max
 */
public class WrappingPanel extends OptionTableWithPreviewPanel {
  private static final String[] FULL_WRAP_OPTIONS = new String[] {
    ApplicationBundle.message("combobox.codestyle.do.not.wrap"),
    ApplicationBundle.message("combobox.codestyle.wrap.if.long"),
    ApplicationBundle.message("combobox.codestyle.chop.down.if.long"),
    ApplicationBundle.message("combobox.codestyle.wrap.always")
  };
  private static final String[] SINGLE_ITEM_WRAP_OPTIONS = new String[]{
    ApplicationBundle.message("combobox.codestyle.do.not.wrap"),
    ApplicationBundle.message("combobox.codestyle.wrap.if.long"),
    ApplicationBundle.message("combobox.codestyle.wrap.always")
  };
  private static final int[] FULL_WRAP_VALUES = new int[]{CodeStyleSettings.DO_NOT_WRAP,
                                                          CodeStyleSettings.WRAP_AS_NEEDED,
                                                          CodeStyleSettings.WRAP_AS_NEEDED |
                                                          CodeStyleSettings.WRAP_ON_EVERY_ITEM,
                                                          CodeStyleSettings.WRAP_ALWAYS};

  private static final int[] SINGLE_ITEM_WRAP_VALUES = new int[]{CodeStyleSettings.DO_NOT_WRAP,
                                                                 CodeStyleSettings.WRAP_AS_NEEDED,
                                                                 CodeStyleSettings.WRAP_ALWAYS};

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

  private static final String METHOD_PARAMETERS_WRAPPING = ApplicationBundle.message("combobox.wrap.method.declaration.parameters");
  private static final String METHOD_PARENTHESES = ApplicationBundle.message("checkbox.align.multiline.method.parentheses");
  private static final String CALL_PARAMETERS_WRAPPING = ApplicationBundle.message("combobox.wrap.method.call.arguments");
  private static final String CALL_CHAIN_WRAPPING = ApplicationBundle.message("combobox.wrap.chained.method.calls");
  private static final String FOR_STATEMENT_WRAPPING = ApplicationBundle.message("combobox.wrap.for.statement");
  private static final String IF_STATEMENT_WRAPPING = ApplicationBundle.message("combobox.wrap.if.statement");
  private static final String WHILE_STATEMENT_WRAPPING = ApplicationBundle.message("combobox.wrap.while.statement");
  private static final String DOWHILE_STATEMENT_WRAPPING = ApplicationBundle.message("combobox.wrap.dowhile.statement");
  private static final String TRY_STATEMENT_WRAPPING = ApplicationBundle.message("combobox.wrap.try.statement");
  private static final String BINARY_OPERATION_WRAPPING = ApplicationBundle.message("combobox.wrap.binary.operations");
  private static final String EXTENDS_LIST_WRAPPING = ApplicationBundle.message("combobox.wrap.extends.implements.list");
  private static final String EXTENDS_KEYWORD_WRAPPING = ApplicationBundle.message("combobox.wrap.extends.implements.keyword");
  private static final String THROWS_LIST_WRAPPING = ApplicationBundle.message("combobox.wrap.throws.list");
  private static final String THROWS_KEYWORD_WRAPPING = ApplicationBundle.message("combobox.wrap.throws.keyword");
  private static final String TERNARY_OPERATION_WRAPPING = ApplicationBundle.message("combobox.wrap.ternary.operation");
  private static final String ASSIGNMENT_WRAPPING = ApplicationBundle.message("combobox.wrap.assignment.statement");
  private static final String FIELDS_VARIABLES_GROUPS = ApplicationBundle.message("combobox.wrap.assignment.statement");
  private static final String ARRAY_INITIALIZER_WRAPPING = ApplicationBundle.message("combobox.wrap.array.initializer");
  private static final String LABELED_STATEMENT_WRAPPING = ApplicationBundle.message("combobox.wrap.label.declaration");
  private static final String MODIFIER_LIST_WRAPPING = ApplicationBundle.message("combobox.wrap.modifier.list");
  private static final String ASSERT_STATEMENT_WRAPPING = ApplicationBundle.message("combobox.wrap.assert.statement");

  public WrappingPanel(CodeStyleSettings settings) {
    super(settings);
    init();
  }

  @Override
  protected LanguageCodeStyleSettingsProvider.SettingsType getSettingsType() {
    return LanguageCodeStyleSettingsProvider.SettingsType.WRAPPING_SETTINGS;
  }

  protected void initTables() {
    initSelectionGroup("EXTENDS_LIST_WRAP", EXTENDS_LIST_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initBooleanField("ALIGN_MULTILINE_EXTENDS_LIST", ApplicationBundle.message("title.align.when.multiline"), EXTENDS_LIST_WRAPPING);

    initSelectionGroup("EXTENDS_KEYWORD_WRAP", EXTENDS_KEYWORD_WRAPPING, SINGLE_ITEM_WRAP_OPTIONS, SINGLE_ITEM_WRAP_VALUES);

    initSelectionGroup("METHOD_PARAMETERS_WRAP", METHOD_PARAMETERS_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initBooleanField("ALIGN_MULTILINE_PARAMETERS", ApplicationBundle.message("title.align.when.multiline"), METHOD_PARAMETERS_WRAPPING);
    initBooleanField("METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE", ApplicationBundle.message("checkbox.wrap.new.line.after.lpar"), METHOD_PARAMETERS_WRAPPING);
    initBooleanField("METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE", ApplicationBundle.message("checkbox.wrap.place.rpar.on.new.line"), METHOD_PARAMETERS_WRAPPING);

    initBooleanField("ALIGN_MULTILINE_METHOD_BRACKETS", ApplicationBundle.message("title.align.when.multiline"), METHOD_PARENTHESES);

    initSelectionGroup("THROWS_LIST_WRAP", THROWS_LIST_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initBooleanField("ALIGN_MULTILINE_THROWS_LIST", ApplicationBundle.message("title.align.when.multiline"), THROWS_LIST_WRAPPING);
    initSelectionGroup("THROWS_KEYWORD_WRAP", THROWS_KEYWORD_WRAPPING, SINGLE_ITEM_WRAP_OPTIONS, SINGLE_ITEM_WRAP_VALUES);

    initSelectionGroup("CALL_PARAMETERS_WRAP", CALL_PARAMETERS_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initBooleanField("ALIGN_MULTILINE_PARAMETERS_IN_CALLS", ApplicationBundle.message("title.align.when.multiline"), CALL_PARAMETERS_WRAPPING);
    initBooleanField("PREFER_PARAMETERS_WRAP", ApplicationBundle.message("checkbox.wrap.take.priority.over.call.chain.wrapping"), CALL_PARAMETERS_WRAPPING);
    initBooleanField("CALL_PARAMETERS_LPAREN_ON_NEXT_LINE", ApplicationBundle.message("checkbox.wrap.new.line.after.lpar"), CALL_PARAMETERS_WRAPPING);
    initBooleanField("CALL_PARAMETERS_RPAREN_ON_NEXT_LINE", ApplicationBundle.message("checkbox.wrap.place.rpar.on.new.line"), CALL_PARAMETERS_WRAPPING);

    initSelectionGroup("METHOD_CALL_CHAIN_WRAP", CALL_CHAIN_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initBooleanField("ALIGN_MULTILINE_CHAINED_METHODS", ApplicationBundle.message("title.align.when.multiline"), CALL_CHAIN_WRAPPING);

    initSelectionField("IF_BRACE_FORCE", ApplicationBundle.message("title.force.braces"), IF_STATEMENT_WRAPPING, BRACE_FORCE_OPTIONS, BRACE_FORCE_VALUES);

    initSelectionGroup("FOR_STATEMENT_WRAP", FOR_STATEMENT_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initBooleanField("ALIGN_MULTILINE_FOR", ApplicationBundle.message("title.align.when.multiline"), FOR_STATEMENT_WRAPPING);
    initBooleanField("FOR_STATEMENT_LPAREN_ON_NEXT_LINE", ApplicationBundle.message("checkbox.wrap.new.line.after.lpar"), FOR_STATEMENT_WRAPPING);
    initBooleanField("FOR_STATEMENT_RPAREN_ON_NEXT_LINE", ApplicationBundle.message("checkbox.wrap.place.rpar.on.new.line"), FOR_STATEMENT_WRAPPING);
    initSelectionField("FOR_BRACE_FORCE", ApplicationBundle.message("title.force.braces"), FOR_STATEMENT_WRAPPING, BRACE_FORCE_OPTIONS, BRACE_FORCE_VALUES);

    initSelectionField("WHILE_BRACE_FORCE", ApplicationBundle.message("title.force.braces"), WHILE_STATEMENT_WRAPPING, BRACE_FORCE_OPTIONS, BRACE_FORCE_VALUES);
    initSelectionField("DOWHILE_BRACE_FORCE", ApplicationBundle.message("title.force.braces"), DOWHILE_STATEMENT_WRAPPING, BRACE_FORCE_OPTIONS, BRACE_FORCE_VALUES);

    initBooleanField("ALIGN_GROUP_FIELDS_VARIABLES", ApplicationBundle.message("title.align.when.multiline"), FIELDS_VARIABLES_GROUPS);

    initSelectionGroup("BINARY_OPERATION_WRAP", BINARY_OPERATION_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initBooleanField("ALIGN_MULTILINE_BINARY_OPERATION", ApplicationBundle.message("title.align.when.multiline"), BINARY_OPERATION_WRAPPING);
    initBooleanField("BINARY_OPERATION_SIGN_ON_NEXT_LINE", ApplicationBundle.message("checkbox.wrap.operation.sign.on.next.line"), BINARY_OPERATION_WRAPPING);
    initBooleanField("ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION", ApplicationBundle.message("title.align.parenthesised.when.multiline"), BINARY_OPERATION_WRAPPING);
    initBooleanField("PARENTHESES_EXPRESSION_LPAREN_WRAP", ApplicationBundle.message("checkbox.wrap.new.line.after.lpar"), BINARY_OPERATION_WRAPPING);
    initBooleanField("PARENTHESES_EXPRESSION_RPAREN_WRAP", ApplicationBundle.message("checkbox.wrap.place.rpar.on.new.line"), BINARY_OPERATION_WRAPPING);

    initSelectionGroup("ASSIGNMENT_WRAP", ASSIGNMENT_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initBooleanField("ALIGN_MULTILINE_ASSIGNMENT", ApplicationBundle.message("title.align.when.multiline"), ASSIGNMENT_WRAPPING);
    initBooleanField("PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE", ApplicationBundle.message("checkbox.wrap.assignment.sign.on.next.line"), ASSIGNMENT_WRAPPING);

    initSelectionGroup("TERNARY_OPERATION_WRAP", TERNARY_OPERATION_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initBooleanField("ALIGN_MULTILINE_TERNARY_OPERATION", ApplicationBundle.message("title.align.when.multiline"), TERNARY_OPERATION_WRAPPING);
    initBooleanField("TERNARY_OPERATION_SIGNS_ON_NEXT_LINE", ApplicationBundle.message("checkbox.wrap.quest.and.colon.signs.on.next.line"), TERNARY_OPERATION_WRAPPING);

    initSelectionGroup("ARRAY_INITIALIZER_WRAP", ARRAY_INITIALIZER_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initBooleanField("ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION", ApplicationBundle.message("title.align.when.multiline"), ARRAY_INITIALIZER_WRAPPING);
    initBooleanField("ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE", ApplicationBundle.message("checkbox.wrap.new.line.after.lbrace"), ARRAY_INITIALIZER_WRAPPING);
    initBooleanField("ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE", ApplicationBundle.message("checkbox.wrap.place.rbrace.on.new.line"), ARRAY_INITIALIZER_WRAPPING);

    initSelectionGroup("LABELED_STATEMENT_WRAP", LABELED_STATEMENT_WRAPPING, SINGLE_ITEM_WRAP_OPTIONS, SINGLE_ITEM_WRAP_VALUES);

    initBooleanField("MODIFIER_LIST_WRAP", ApplicationBundle.message("checkbox.wrap.after.modifier.list"), MODIFIER_LIST_WRAPPING);

    initSelectionGroup("ASSERT_STATEMENT_WRAP", ASSERT_STATEMENT_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initBooleanField("ASSERT_STATEMENT_COLON_ON_NEXT_LINE", ApplicationBundle.message("checkbox.wrap.colon.signs.on.next.line"), ASSERT_STATEMENT_WRAPPING);

    initSelectionGroup("CLASS_ANNOTATION_WRAP", ApplicationBundle.message("checkbox.wrap.classes.annotation"), FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initSelectionGroup("METHOD_ANNOTATION_WRAP", ApplicationBundle.message("checkbox.wrap.methods.annotation"), FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initSelectionGroup("FIELD_ANNOTATION_WRAP", ApplicationBundle.message("checkbox.wrap.fields.annotation"), FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initSelectionGroup("PARAMETER_ANNOTATION_WRAP", ApplicationBundle.message("checkbox.wrap.parameters.annotation"), FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initSelectionGroup("VARIABLE_ANNOTATION_WRAP", ApplicationBundle.message("checkbox.wrap.local.variables.annotation"), FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initSelectionGroup("ENUM_CONSTANTS_WRAP", ApplicationBundle.message("checkbox.wrap.enum.constants"), FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
  }

  protected int getRightMargin() {
    return 37;
  }

}