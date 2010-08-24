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

public class WrappingAndBracesPanel extends OptionTableWithPreviewPanel {
  private static final String[] FULL_WRAP_OPTIONS = new String[] {
    ApplicationBundle.message("wrapping.do.not.wrap"),
    ApplicationBundle.message("wrapping.wrap.if.long"),
    ApplicationBundle.message("wrapping.chop.down.if.long"),
    ApplicationBundle.message("wrapping.wrap.always")
  };
  private static final String[] SINGLE_ITEM_WRAP_OPTIONS = new String[]{
    ApplicationBundle.message("wrapping.do.not.wrap"),
    ApplicationBundle.message("wrapping.wrap.if.long"),
    ApplicationBundle.message("wrapping.wrap.always")
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
    ApplicationBundle.message("wrapping.force.braces.do.not.force"),
    ApplicationBundle.message("wrapping.force.braces.when.multiline"),
    ApplicationBundle.message("wrapping.force.braces.always")
  };

  private static final int[] BRACE_FORCE_VALUES = new int[]{
    CodeStyleSettings.DO_NOT_FORCE,
    CodeStyleSettings.FORCE_BRACES_IF_MULTILINE,
    CodeStyleSettings.FORCE_BRACES_ALWAYS
  };

  private static final String[] BRACE_PLACEMENT_OPTIONS = new String[]{
    ApplicationBundle.message("wrapping.brace.placement.end.of.line"),
    ApplicationBundle.message("wrapping.brace.placement.next.line.if.wrapped"),
    ApplicationBundle.message("wrapping.brace.placement.next.line"),
    ApplicationBundle.message("wrapping.brace.placement.next.line.shifted"),
    ApplicationBundle.message("wrapping.brace.placement.next.line.each.shifted")
  };

  private static final int[] BRACE_PLACEMENT_VALUES = new int[] {
    CodeStyleSettings.END_OF_LINE,
    CodeStyleSettings.NEXT_LINE_IF_WRAPPED,
    CodeStyleSettings.NEXT_LINE,
    CodeStyleSettings.NEXT_LINE_SHIFTED,
    CodeStyleSettings.NEXT_LINE_SHIFTED2
  };

  private static final String KEEP = ApplicationBundle.message("wrapping.keep.when.reformatting");
  private static final String BRACES = ApplicationBundle.message("wrapping.brace.placement");
  private static final String METHOD_PARAMETERS = ApplicationBundle.message("wrapping.method.parameters");
  private static final String METHOD_PARENTHESES = ApplicationBundle.message("wrapping.method.parentheses");
  private static final String METHOD_ARGUMENTS_WRAPPING = ApplicationBundle.message("wrapping.method.arguments");
  private static final String CALL_CHAIN = ApplicationBundle.message("wrapping.chained.method.calls");
  private static final String IF_STATEMENT = ApplicationBundle.message("wrapping.if.statement");
  private static final String FOR_STATEMENT = ApplicationBundle.message("wrapping.for.statement");
  private static final String WHILE_STATEMENT = ApplicationBundle.message("wrapping.while.statement");
  private static final String DOWHILE_STATEMENT = ApplicationBundle.message("wrapping.dowhile.statement");
  private static final String SWITCH_STATEMENT = ApplicationBundle.message("wrapping.switch.statement");
  private static final String TRY_STATEMENT = ApplicationBundle.message("wrapping.try.statement");
  private static final String BINARY_OPERATION = ApplicationBundle.message("wrapping.binary.operations");
  private static final String EXTENDS_LIST = ApplicationBundle.message("wrapping.extends.implements.list");
  private static final String EXTENDS_KEYWORD = ApplicationBundle.message("wrapping.extends.implements.keyword");
  private static final String THROWS_LIST = ApplicationBundle.message("wrapping.throws.list");
  private static final String THROWS_KEYWORD = ApplicationBundle.message("wrapping.throws.keyword");
  private static final String TERNARY_OPERATION = ApplicationBundle.message("wrapping.ternary.operation");
  private static final String ASSIGNMENT = ApplicationBundle.message("wrapping.assignment.statement");
  private static final String FIELDS_VARIABLES_GROUPS = ApplicationBundle.message("wrapping.assignment.statement");
  private static final String ARRAY_INITIALIZER = ApplicationBundle.message("wrapping.array.initializer");
  private static final String MODIFIER_LIST = ApplicationBundle.message("wrapping.modifier.list");
  private static final String ASSERT_STATEMENT = ApplicationBundle.message("wrapping.assert.statement");

  public WrappingAndBracesPanel(CodeStyleSettings settings) {
    super(settings);
    init();
  }

  @Override
  protected LanguageCodeStyleSettingsProvider.SettingsType getSettingsType() {
    return LanguageCodeStyleSettingsProvider.SettingsType.WRAPPING_SETTINGS;
  }

  protected void initTables() {
    addOption("KEEP_LINE_BREAKS", ApplicationBundle.message("wrapping.keep.line.breaks"), KEEP);
    addOption("KEEP_FIRST_COLUMN_COMMENT", ApplicationBundle.message("wrapping.keep.comment.at.first.column"), KEEP);
    addOption("KEEP_CONTROL_STATEMENT_IN_ONE_LINE", ApplicationBundle.message("checkbox.keep.when.reformatting.control.statement.in.one.line"), KEEP);
    addOption("KEEP_SIMPLE_BLOCKS_IN_ONE_LINE", ApplicationBundle.message("wrapping.keep.simple.blocks.in.one.line"), KEEP);
    addOption("KEEP_SIMPLE_METHODS_IN_ONE_LINE", ApplicationBundle.message("wrapping.keep.simple.methods.in.one.line"), KEEP);

    addOption("CLASS_BRACE_STYLE", ApplicationBundle.message("wrapping.brace.placement.class.declaration"), BRACES, BRACE_PLACEMENT_OPTIONS, BRACE_PLACEMENT_VALUES);
    addOption("METHOD_BRACE_STYLE", ApplicationBundle.message("wrapping.brace.placement.method.declaration"), BRACES, BRACE_PLACEMENT_OPTIONS, BRACE_PLACEMENT_VALUES);
    addOption("BRACE_STYLE", ApplicationBundle.message("wrapping.brace.placement.other"), BRACES, BRACE_PLACEMENT_OPTIONS, BRACE_PLACEMENT_VALUES);

    addOption("EXTENDS_LIST_WRAP", EXTENDS_LIST, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    addOption("ALIGN_MULTILINE_EXTENDS_LIST", ApplicationBundle.message("wrapping.align.when.multiline"), EXTENDS_LIST);

    addOption("EXTENDS_KEYWORD_WRAP", EXTENDS_KEYWORD, SINGLE_ITEM_WRAP_OPTIONS, SINGLE_ITEM_WRAP_VALUES);

    addOption("METHOD_PARAMETERS_WRAP", METHOD_PARAMETERS, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    addOption("ALIGN_MULTILINE_PARAMETERS", ApplicationBundle.message("wrapping.align.when.multiline"), METHOD_PARAMETERS);
    addOption("METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE", ApplicationBundle.message("wrapping.new.line.after.lpar"), METHOD_PARAMETERS);
    addOption("METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE", ApplicationBundle.message("wrapping.rpar.on.new.line"), METHOD_PARAMETERS);

    addOption("ALIGN_MULTILINE_METHOD_BRACKETS", ApplicationBundle.message("wrapping.align.when.multiline"), METHOD_PARENTHESES);

    addOption("THROWS_LIST_WRAP", THROWS_LIST, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    addOption("ALIGN_MULTILINE_THROWS_LIST", ApplicationBundle.message("wrapping.align.when.multiline"), THROWS_LIST);
    addOption("THROWS_KEYWORD_WRAP", THROWS_KEYWORD, SINGLE_ITEM_WRAP_OPTIONS, SINGLE_ITEM_WRAP_VALUES);

    addOption("CALL_PARAMETERS_WRAP", METHOD_ARGUMENTS_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    addOption("ALIGN_MULTILINE_PARAMETERS_IN_CALLS", ApplicationBundle.message("wrapping.align.when.multiline"), METHOD_ARGUMENTS_WRAPPING);
    addOption("PREFER_PARAMETERS_WRAP", ApplicationBundle.message("wrapping.take.priority.over.call.chain.wrapping"), METHOD_ARGUMENTS_WRAPPING);
    addOption("CALL_PARAMETERS_LPAREN_ON_NEXT_LINE", ApplicationBundle.message("wrapping.new.line.after.lpar"), METHOD_ARGUMENTS_WRAPPING);
    addOption("CALL_PARAMETERS_RPAREN_ON_NEXT_LINE", ApplicationBundle.message("wrapping.rpar.on.new.line"), METHOD_ARGUMENTS_WRAPPING);

    addOption("METHOD_CALL_CHAIN_WRAP", CALL_CHAIN, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    addOption("ALIGN_MULTILINE_CHAINED_METHODS", ApplicationBundle.message("wrapping.align.when.multiline"), CALL_CHAIN);

    addOption("IF_BRACE_FORCE", ApplicationBundle.message("wrapping.force.braces"), IF_STATEMENT, BRACE_FORCE_OPTIONS, BRACE_FORCE_VALUES);
    addOption("ELSE_ON_NEW_LINE", ApplicationBundle.message("wrapping.else.on.new.line"), IF_STATEMENT);
    addOption("SPECIAL_ELSE_IF_TREATMENT", ApplicationBundle.message("wrapping.special.else.if.braces.treatment"), IF_STATEMENT);

    addOption("FOR_STATEMENT_WRAP", FOR_STATEMENT, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    addOption("ALIGN_MULTILINE_FOR", ApplicationBundle.message("wrapping.align.when.multiline"), FOR_STATEMENT);
    addOption("FOR_STATEMENT_LPAREN_ON_NEXT_LINE", ApplicationBundle.message("wrapping.new.line.after.lpar"), FOR_STATEMENT);
    addOption("FOR_STATEMENT_RPAREN_ON_NEXT_LINE", ApplicationBundle.message("wrapping.rpar.on.new.line"), FOR_STATEMENT);
    addOption("FOR_BRACE_FORCE", ApplicationBundle.message("wrapping.force.braces"), FOR_STATEMENT, BRACE_FORCE_OPTIONS, BRACE_FORCE_VALUES);

    addOption("WHILE_BRACE_FORCE", ApplicationBundle.message("wrapping.force.braces"), WHILE_STATEMENT, BRACE_FORCE_OPTIONS, BRACE_FORCE_VALUES);
    addOption("DOWHILE_BRACE_FORCE", ApplicationBundle.message("wrapping.force.braces"), DOWHILE_STATEMENT, BRACE_FORCE_OPTIONS, BRACE_FORCE_VALUES);
    addOption("WHILE_ON_NEW_LINE", ApplicationBundle.message("wrapping.while.on.new.line"), DOWHILE_STATEMENT);

    addOption("INDENT_CASE_FROM_SWITCH", ApplicationBundle.message("wrapping.indent.case.from.switch"), SWITCH_STATEMENT);

    addOption("CATCH_ON_NEW_LINE", ApplicationBundle.message("wrapping.catch.on.new.line"), TRY_STATEMENT);
    addOption("FINALLY_ON_NEW_LINE", ApplicationBundle.message("wrapping.finally.on.new.line"), TRY_STATEMENT);

    addOption("ALIGN_GROUP_FIELDS_VARIABLES", ApplicationBundle.message("wrapping.align.when.multiline"), FIELDS_VARIABLES_GROUPS);

    addOption("BINARY_OPERATION_WRAP", BINARY_OPERATION, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    addOption("ALIGN_MULTILINE_BINARY_OPERATION", ApplicationBundle.message("wrapping.align.when.multiline"), BINARY_OPERATION);
    addOption("BINARY_OPERATION_SIGN_ON_NEXT_LINE", ApplicationBundle.message("wrapping.operation.sign.on.next.line"), BINARY_OPERATION);
    addOption("ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION", ApplicationBundle.message("wrapping.align.parenthesised.when.multiline"), BINARY_OPERATION);
    addOption("PARENTHESES_EXPRESSION_LPAREN_WRAP", ApplicationBundle.message("wrapping.new.line.after.lpar"), BINARY_OPERATION);
    addOption("PARENTHESES_EXPRESSION_RPAREN_WRAP", ApplicationBundle.message("wrapping.rpar.on.new.line"), BINARY_OPERATION);

    addOption("ASSIGNMENT_WRAP", ASSIGNMENT, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    addOption("ALIGN_MULTILINE_ASSIGNMENT", ApplicationBundle.message("wrapping.align.when.multiline"), ASSIGNMENT);
    addOption("PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE", ApplicationBundle.message("wrapping.assignment.sign.on.next.line"), ASSIGNMENT);

    addOption("TERNARY_OPERATION_WRAP", TERNARY_OPERATION, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    addOption("ALIGN_MULTILINE_TERNARY_OPERATION", ApplicationBundle.message("wrapping.align.when.multiline"), TERNARY_OPERATION);
    addOption("TERNARY_OPERATION_SIGNS_ON_NEXT_LINE", ApplicationBundle.message("wrapping.quest.and.colon.signs.on.next.line"), TERNARY_OPERATION);

    addOption("ARRAY_INITIALIZER_WRAP", ARRAY_INITIALIZER, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    addOption("ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION", ApplicationBundle.message("wrapping.align.when.multiline"), ARRAY_INITIALIZER);
    addOption("ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE", ApplicationBundle.message("wrapping.new.line.after.lbrace"), ARRAY_INITIALIZER);
    addOption("ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE", ApplicationBundle.message("wrapping.rbrace.on.new.line"), ARRAY_INITIALIZER);

    addOption("MODIFIER_LIST_WRAP", ApplicationBundle.message("wrapping.after.modifier.list"), MODIFIER_LIST);

    addOption("ASSERT_STATEMENT_WRAP", ASSERT_STATEMENT, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    addOption("ASSERT_STATEMENT_COLON_ON_NEXT_LINE", ApplicationBundle.message("wrapping.colon.signs.on.next.line"), ASSERT_STATEMENT);

    addOption("CLASS_ANNOTATION_WRAP", ApplicationBundle.message("wrapping.classes.annotation"), FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    addOption("METHOD_ANNOTATION_WRAP", ApplicationBundle.message("wrapping.methods.annotation"), FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    addOption("FIELD_ANNOTATION_WRAP", ApplicationBundle.message("wrapping.fields.annotation"), FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    addOption("PARAMETER_ANNOTATION_WRAP", ApplicationBundle.message("wrapping.parameters.annotation"), FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    addOption("VARIABLE_ANNOTATION_WRAP", ApplicationBundle.message("wrapping.local.variables.annotation"), FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    addOption("ENUM_CONSTANTS_WRAP", ApplicationBundle.message("wrapping.enum.constants"), FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
  }

  protected int getRightMargin() {
    return 37;
  }
}