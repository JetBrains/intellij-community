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
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class WrappingAndBracesPanel extends OptionTableWithPreviewPanel {
  private MultiMap<String, String> myGroupToFields = new MultiMap<String, String>();
  private Map<String, SettingsGroup> myFieldNameToGroup;

  public WrappingAndBracesPanel(CodeStyleSettings settings) {
    super(settings);
    init();
  }

  @Override
  public LanguageCodeStyleSettingsProvider.SettingsType getSettingsType() {
    return LanguageCodeStyleSettingsProvider.SettingsType.WRAPPING_AND_BRACES_SETTINGS;
  }

  @Override
  protected void addOption(@NotNull String fieldName, @NotNull String title, @Nullable String groupName) {
    super.addOption(fieldName, title, groupName);
    if (groupName != null) {
      myGroupToFields.putValue(groupName, fieldName);
    }
  }

  @Override
  protected void addOption(@NotNull String fieldName, @NotNull String title, @Nullable String groupName,
                           @NotNull String[] options, @NotNull int[] values) {
    super.addOption(fieldName, title, groupName, options, values);
    if (groupName == null) {
      myGroupToFields.putValue(title, fieldName);
    }
  }

  @Override
  protected void initTables() {
    addOption("RIGHT_MARGIN", ApplicationBundle.message("editbox.right.margin.columns"), null, 0, 999, -1, ApplicationBundle.message("settings.code.style.default.general"));
    addOption("WRAP_ON_TYPING", ApplicationBundle.message("wrapping.wrap.on.typing"), null, WRAP_ON_TYPING_OPTIONS, WRAP_ON_TYPING_VALUES);
    
    addOption("KEEP_LINE_BREAKS", ApplicationBundle.message("wrapping.keep.line.breaks"), WRAPPING_KEEP);
    addOption("KEEP_FIRST_COLUMN_COMMENT", ApplicationBundle.message("wrapping.keep.comment.at.first.column"), WRAPPING_KEEP);
    addOption("KEEP_CONTROL_STATEMENT_IN_ONE_LINE", ApplicationBundle.message("checkbox.keep.when.reformatting.control.statement.in.one.line"), WRAPPING_KEEP);
    addOption("KEEP_MULTIPLE_EXPRESSIONS_IN_ONE_LINE", ApplicationBundle.message("wrapping.keep.multiple.expressions.in.one.line"), WRAPPING_KEEP);
    addOption("KEEP_SIMPLE_BLOCKS_IN_ONE_LINE", ApplicationBundle.message("wrapping.keep.simple.blocks.in.one.line"), WRAPPING_KEEP);
    addOption("KEEP_SIMPLE_METHODS_IN_ONE_LINE", ApplicationBundle.message("wrapping.keep.simple.methods.in.one.line"), WRAPPING_KEEP);
    addOption("KEEP_SIMPLE_CLASSES_IN_ONE_LINE", ApplicationBundle.message("wrapping.keep.simple.classes.in.one.line"), WRAPPING_KEEP);

    addOption("WRAP_LONG_LINES", ApplicationBundle.message("wrapping.long.lines"), null);
    addOption("WRAP_COMMENTS", ApplicationBundle.message("wrapping.comments.wrap.at.right.margin"), WRAPPING_COMMENTS);

    addOption("CLASS_BRACE_STYLE", ApplicationBundle.message("wrapping.brace.placement.class.declaration"), WRAPPING_BRACES, BRACE_PLACEMENT_OPTIONS, BRACE_PLACEMENT_VALUES);
    addOption("METHOD_BRACE_STYLE", ApplicationBundle.message("wrapping.brace.placement.method.declaration"), WRAPPING_BRACES, BRACE_PLACEMENT_OPTIONS, BRACE_PLACEMENT_VALUES);
    addOption("BRACE_STYLE", ApplicationBundle.message("wrapping.brace.placement.other"), WRAPPING_BRACES, BRACE_PLACEMENT_OPTIONS, BRACE_PLACEMENT_VALUES);

    addOption("EXTENDS_LIST_WRAP", WRAPPING_EXTENDS_LIST, WRAP_OPTIONS, WRAP_VALUES);
    addOption("ALIGN_MULTILINE_EXTENDS_LIST", ApplicationBundle.message("wrapping.align.when.multiline"), WRAPPING_EXTENDS_LIST);

    addOption("EXTENDS_KEYWORD_WRAP", WRAPPING_EXTENDS_KEYWORD, WRAP_OPTIONS_FOR_SINGLETON, WRAP_VALUES_FOR_SINGLETON);

    addOption("THROWS_LIST_WRAP", WRAPPING_THROWS_LIST, WRAP_OPTIONS, WRAP_VALUES);
    addOption("ALIGN_MULTILINE_THROWS_LIST", ApplicationBundle.message("wrapping.align.when.multiline"), WRAPPING_THROWS_LIST);
    addOption("ALIGN_THROWS_KEYWORD", ApplicationBundle.message("wrapping.align.throws.keyword"), WRAPPING_THROWS_LIST);
    addOption("THROWS_KEYWORD_WRAP", WRAPPING_THROWS_KEYWORD, WRAP_OPTIONS_FOR_SINGLETON, WRAP_VALUES_FOR_SINGLETON);

    addOption("METHOD_PARAMETERS_WRAP", WRAPPING_METHOD_PARAMETERS, WRAP_OPTIONS, WRAP_VALUES);
    addOption("ALIGN_MULTILINE_PARAMETERS", ApplicationBundle.message("wrapping.align.when.multiline"), WRAPPING_METHOD_PARAMETERS);
    addOption("METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE", ApplicationBundle.message("wrapping.new.line.after.lpar"), WRAPPING_METHOD_PARAMETERS);
    addOption("METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE", ApplicationBundle.message("wrapping.rpar.on.new.line"), WRAPPING_METHOD_PARAMETERS);

    addOption("CALL_PARAMETERS_WRAP", WRAPPING_METHOD_ARGUMENTS_WRAPPING, WRAP_OPTIONS, WRAP_VALUES);
    addOption("ALIGN_MULTILINE_PARAMETERS_IN_CALLS", ApplicationBundle.message("wrapping.align.when.multiline"), WRAPPING_METHOD_ARGUMENTS_WRAPPING);
    addOption("PREFER_PARAMETERS_WRAP", ApplicationBundle.message("wrapping.take.priority.over.call.chain.wrapping"), WRAPPING_METHOD_ARGUMENTS_WRAPPING);
    addOption("CALL_PARAMETERS_LPAREN_ON_NEXT_LINE", ApplicationBundle.message("wrapping.new.line.after.lpar"), WRAPPING_METHOD_ARGUMENTS_WRAPPING);
    addOption("CALL_PARAMETERS_RPAREN_ON_NEXT_LINE", ApplicationBundle.message("wrapping.rpar.on.new.line"), WRAPPING_METHOD_ARGUMENTS_WRAPPING);

    addOption("ALIGN_MULTILINE_METHOD_BRACKETS", ApplicationBundle.message("wrapping.align.when.multiline"), WRAPPING_METHOD_PARENTHESES);

    addOption("METHOD_CALL_CHAIN_WRAP", WRAPPING_CALL_CHAIN, WRAP_OPTIONS, WRAP_VALUES);
    addOption("WRAP_FIRST_METHOD_IN_CALL_CHAIN", ApplicationBundle.message("wrapping.chained.method.call.first.on.new.line"), WRAPPING_CALL_CHAIN);
    addOption("ALIGN_MULTILINE_CHAINED_METHODS", ApplicationBundle.message("wrapping.align.when.multiline"), WRAPPING_CALL_CHAIN);

    addOption("IF_BRACE_FORCE", ApplicationBundle.message("wrapping.force.braces"), WRAPPING_IF_STATEMENT, BRACE_OPTIONS, BRACE_VALUES);
    addOption("ELSE_ON_NEW_LINE", ApplicationBundle.message("wrapping.else.on.new.line"), WRAPPING_IF_STATEMENT);
    addOption("SPECIAL_ELSE_IF_TREATMENT", ApplicationBundle.message("wrapping.special.else.if.braces.treatment"), WRAPPING_IF_STATEMENT);

    addOption("FOR_STATEMENT_WRAP", WRAPPING_FOR_STATEMENT, WRAP_OPTIONS, WRAP_VALUES);
    addOption("ALIGN_MULTILINE_FOR", ApplicationBundle.message("wrapping.align.when.multiline"), WRAPPING_FOR_STATEMENT);
    addOption("FOR_STATEMENT_LPAREN_ON_NEXT_LINE", ApplicationBundle.message("wrapping.new.line.after.lpar"), WRAPPING_FOR_STATEMENT);
    addOption("FOR_STATEMENT_RPAREN_ON_NEXT_LINE", ApplicationBundle.message("wrapping.rpar.on.new.line"), WRAPPING_FOR_STATEMENT);
    addOption("FOR_BRACE_FORCE", ApplicationBundle.message("wrapping.force.braces"), WRAPPING_FOR_STATEMENT, BRACE_OPTIONS, BRACE_VALUES);

    addOption("WHILE_BRACE_FORCE", ApplicationBundle.message("wrapping.force.braces"), WRAPPING_WHILE_STATEMENT, BRACE_OPTIONS, BRACE_VALUES);
    addOption("DOWHILE_BRACE_FORCE", ApplicationBundle.message("wrapping.force.braces"), WRAPPING_DOWHILE_STATEMENT, BRACE_OPTIONS, BRACE_VALUES);
    addOption("WHILE_ON_NEW_LINE", ApplicationBundle.message("wrapping.while.on.new.line"), WRAPPING_DOWHILE_STATEMENT);

    addOption("INDENT_CASE_FROM_SWITCH", ApplicationBundle.message("wrapping.indent.case.from.switch"), WRAPPING_SWITCH_STATEMENT);
    addOption("INDENT_BREAK_FROM_CASE", ApplicationBundle.message("wrapping.indent.break.from.case"), WRAPPING_SWITCH_STATEMENT);

    addOption("RESOURCE_LIST_WRAP", WRAPPING_TRY_RESOURCE_LIST, WRAP_OPTIONS, WRAP_VALUES);
    addOption("ALIGN_MULTILINE_RESOURCES", ApplicationBundle.message("wrapping.align.when.multiline"), WRAPPING_TRY_RESOURCE_LIST);
    addOption("RESOURCE_LIST_LPAREN_ON_NEXT_LINE", ApplicationBundle.message("wrapping.new.line.after.lpar"), WRAPPING_TRY_RESOURCE_LIST);
    addOption("RESOURCE_LIST_RPAREN_ON_NEXT_LINE", ApplicationBundle.message("wrapping.rpar.on.new.line"), WRAPPING_TRY_RESOURCE_LIST);

    addOption("CATCH_ON_NEW_LINE", ApplicationBundle.message("wrapping.catch.on.new.line"), WRAPPING_TRY_STATEMENT);
    addOption("FINALLY_ON_NEW_LINE", ApplicationBundle.message("wrapping.finally.on.new.line"), WRAPPING_TRY_STATEMENT);

    addOption("BINARY_OPERATION_WRAP", WRAPPING_BINARY_OPERATION, WRAP_OPTIONS, WRAP_VALUES);
    addOption("ALIGN_MULTILINE_BINARY_OPERATION", ApplicationBundle.message("wrapping.align.when.multiline"), WRAPPING_BINARY_OPERATION);
    addOption("BINARY_OPERATION_SIGN_ON_NEXT_LINE", ApplicationBundle.message("wrapping.operation.sign.on.next.line"), WRAPPING_BINARY_OPERATION);
    addOption("ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION", ApplicationBundle.message("wrapping.align.parenthesised.when.multiline"), WRAPPING_BINARY_OPERATION);
    addOption("PARENTHESES_EXPRESSION_LPAREN_WRAP", ApplicationBundle.message("wrapping.new.line.after.lpar"), WRAPPING_BINARY_OPERATION);
    addOption("PARENTHESES_EXPRESSION_RPAREN_WRAP", ApplicationBundle.message("wrapping.rpar.on.new.line"), WRAPPING_BINARY_OPERATION);

    addOption("ASSIGNMENT_WRAP", WRAPPING_ASSIGNMENT, WRAP_OPTIONS, WRAP_VALUES);
    addOption("ALIGN_MULTILINE_ASSIGNMENT", ApplicationBundle.message("wrapping.align.when.multiline"), WRAPPING_ASSIGNMENT);
    addOption("PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE", ApplicationBundle.message("wrapping.assignment.sign.on.next.line"), WRAPPING_ASSIGNMENT);

    addOption("ALIGN_GROUP_FIELD_DECLARATIONS", ApplicationBundle.message("wrapping.align.fields.in.columns"), WRAPPING_FIELDS_VARIABLES_GROUPS);
    addOption("ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS", ApplicationBundle.message("wrapping.align.variables.in.columns"), WRAPPING_FIELDS_VARIABLES_GROUPS);

    addOption("TERNARY_OPERATION_WRAP", WRAPPING_TERNARY_OPERATION, WRAP_OPTIONS, WRAP_VALUES);
    addOption("ALIGN_MULTILINE_TERNARY_OPERATION", ApplicationBundle.message("wrapping.align.when.multiline"), WRAPPING_TERNARY_OPERATION);
    addOption("TERNARY_OPERATION_SIGNS_ON_NEXT_LINE", ApplicationBundle.message("wrapping.quest.and.colon.signs.on.next.line"), WRAPPING_TERNARY_OPERATION);

    addOption("ARRAY_INITIALIZER_WRAP", WRAPPING_ARRAY_INITIALIZER, WRAP_OPTIONS, WRAP_VALUES);
    addOption("ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION", ApplicationBundle.message("wrapping.align.when.multiline"), WRAPPING_ARRAY_INITIALIZER);
    addOption("ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE", ApplicationBundle.message("wrapping.new.line.after.lbrace"), WRAPPING_ARRAY_INITIALIZER);
    addOption("ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE", ApplicationBundle.message("wrapping.rbrace.on.new.line"), WRAPPING_ARRAY_INITIALIZER);

    addOption("MODIFIER_LIST_WRAP", ApplicationBundle.message("wrapping.after.modifier.list"), WRAPPING_MODIFIER_LIST);

    addOption("ASSERT_STATEMENT_WRAP", WRAPPING_ASSERT_STATEMENT, WRAP_OPTIONS, WRAP_VALUES);
    addOption("ASSERT_STATEMENT_COLON_ON_NEXT_LINE", ApplicationBundle.message("wrapping.colon.signs.on.next.line"), WRAPPING_ASSERT_STATEMENT);

    addOption("ENUM_CONSTANTS_WRAP", ApplicationBundle.message("wrapping.enum.constants"), WRAP_OPTIONS, WRAP_VALUES);
    addOption("CLASS_ANNOTATION_WRAP", ApplicationBundle.message("wrapping.classes.annotation"), WRAP_OPTIONS, WRAP_VALUES);
    addOption("METHOD_ANNOTATION_WRAP", ApplicationBundle.message("wrapping.methods.annotation"), WRAP_OPTIONS, WRAP_VALUES);
    addOption("FIELD_ANNOTATION_WRAP", ApplicationBundle.message("wrapping.fields.annotation"), WRAP_OPTIONS, WRAP_VALUES);
    addOption("PARAMETER_ANNOTATION_WRAP", ApplicationBundle.message("wrapping.parameters.annotation"), WRAP_OPTIONS, WRAP_VALUES);
    addOption("VARIABLE_ANNOTATION_WRAP", ApplicationBundle.message("wrapping.local.variables.annotation"), WRAP_OPTIONS, WRAP_VALUES);
  }

  protected SettingsGroup getAssociatedSettingsGroup(String fieldName) {
    if (myFieldNameToGroup == null) {
      myFieldNameToGroup = ContainerUtil.newHashMap();
      Set<String> groups = myGroupToFields.keySet();
      for (String group : groups) {
        Collection<String> fields = myGroupToFields.get(group);
        SettingsGroup settingsGroup = new SettingsGroup(group, fields);
        for (String field : fields) {
          myFieldNameToGroup.put(field, settingsGroup);
        }
      }
    }
    return myFieldNameToGroup.get(fieldName);
  }

  @Override
  protected String getTabTitle() {
    return ApplicationBundle.message("wrapping.and.braces");
  }

  protected static class SettingsGroup {
    public final String title;
    public final Collection<String> commonCodeStyleSettingFieldNames;

    public SettingsGroup(@NotNull String title,
                         @NotNull Collection<String> commonCodeStyleSettingFieldNames) {
      this.title = title;
      this.commonCodeStyleSettingFieldNames = commonCodeStyleSettingFieldNames;
    }
  }
}