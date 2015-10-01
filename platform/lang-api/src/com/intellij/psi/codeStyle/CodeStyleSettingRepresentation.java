/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import static com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable.*;

import java.util.List;
import java.util.Map;

/**
 * @author Roman.Shein
 * @since 15.09.2015.
 */
public class CodeStyleSettingRepresentation {

  public static class SettingsGroup {
    @Nullable
    public final String name;

    public SettingsGroup(@Nullable String name) {
      this.name = name;
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof SettingsGroup) {
        SettingsGroup other = (SettingsGroup) o;
        return name != null && name.equals(other.name);
      } else {
        return false;
      }
    }

    @Override
    public int hashCode() {
      return name == null ? 0 : name.hashCode();
    }

    public boolean isNull() {
      return name == null;
    }
  }

  @NotNull
  protected String myFieldName;

  @NotNull
  protected String myUiName;

  public CodeStyleSettingRepresentation(@NotNull String fieldName, @NotNull String uiName) {
    myFieldName = fieldName;
    myUiName = uiName;
  }

  @NotNull
  public String getFieldName() {
    return myFieldName;
  }

  @NotNull
  public String getUiName() {
    return myUiName;
  }

  public void setUiName(@NotNull String newName) {
    myUiName = newName;
  }

  @NotNull
  public String getValueUiName(@NotNull Object value) {
    return value.toString();
  }

  @Override
  public boolean equals(Object o) {
    return (o instanceof CodeStyleSettingRepresentation) && ((CodeStyleSettingRepresentation)o).getFieldName().equals(getFieldName());
  }

  @Override
  public int hashCode() {
    return myFieldName.hashCode();
  }

  protected static void putGroupTop(@NotNull Map<SettingsGroup, List<CodeStyleSettingRepresentation>> result, @NotNull String fieldName,
                                    @NotNull String uiName, int[] values, String[] valueUiNames) {
    result.put(new SettingsGroup(null), ContainerUtil.<CodeStyleSettingRepresentation>newLinkedList(
      new CodeStyleSelectSettingRepresentation(fieldName, uiName, values, valueUiNames)
    ));
  }

  @NotNull
  public static Map<SettingsGroup, List<CodeStyleSettingRepresentation>> getStandardSettings(LanguageCodeStyleSettingsProvider.SettingsType settingsType) {
    Map<SettingsGroup, List<CodeStyleSettingRepresentation>> result = ContainerUtil.newLinkedHashMap();
    switch (settingsType) {
      case BLANK_LINES_SETTINGS:
        result.put(new SettingsGroup(BLANK_LINES_KEEP), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("KEEP_BLANK_LINES_IN_DECLARATIONS",
                                             ApplicationBundle.message("editbox.keep.blanklines.in.declarations")),
          new CodeStyleSettingRepresentation("KEEP_BLANK_LINES_IN_CODE", ApplicationBundle.message("editbox.keep.blanklines.in.code")),
          new CodeStyleSettingRepresentation("KEEP_BLANK_LINES_BEFORE_RBRACE",
                                             ApplicationBundle.message("editbox.keep.blanklines.before.rbrace"))
        ));
        
        result.put(new SettingsGroup(BLANK_LINES), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("BLANK_LINES_BEFORE_PACKAGE",
                                             ApplicationBundle.message("editbox.blanklines.before.package.statement")),
          new CodeStyleSettingRepresentation("BLANK_LINES_AFTER_PACKAGE",
                                             ApplicationBundle.message("editbox.blanklines.after.package.statement")),
          new CodeStyleSettingRepresentation("BLANK_LINES_BEFORE_IMPORTS", ApplicationBundle.message("editbox.blanklines.before.imports")),
          new CodeStyleSettingRepresentation("BLANK_LINES_AFTER_IMPORTS", ApplicationBundle.message("editbox.blanklines.after.imports")),
          new CodeStyleSettingRepresentation("BLANK_LINES_AROUND_CLASS", ApplicationBundle.message("editbox.blanklines.around.class")),
          new CodeStyleSettingRepresentation("BLANK_LINES_AFTER_CLASS_HEADER",
                                             ApplicationBundle.message("editbox.blanklines.after.class.header")),
          new CodeStyleSettingRepresentation("BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER",
                                             ApplicationBundle.message("editbox.blanklines.after.anonymous.class.header")),
          new CodeStyleSettingRepresentation("BLANK_LINES_AROUND_FIELD_IN_INTERFACE", "Around field in interface:"),
          //TODO why is thi not loaded from bundle??
          new CodeStyleSettingRepresentation("BLANK_LINES_AROUND_FIELD", ApplicationBundle.message("editbox.blanklines.around.field")),
          new CodeStyleSettingRepresentation("BLANK_LINES_AROUND_METHOD_IN_INTERFACE", "Around method in interface:"),
          //TODO why is thi not loaded from bundle??
          new CodeStyleSettingRepresentation("BLANK_LINES_AROUND_METHOD", ApplicationBundle.message("editbox.blanklines.around.method")),
          new CodeStyleSettingRepresentation("BLANK_LINES_BEFORE_METHOD_BODY",
                                             ApplicationBundle.message("editbox.blanklines.before.method.body"))
        ));
        break;
      case SPACING_SETTINGS:
        result.put(new SettingsGroup(SPACES_BEFORE_PARENTHESES), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("SPACE_BEFORE_METHOD_PARENTHESES",
                                             ApplicationBundle.message("checkbox.spaces.method.declaration.parentheses")),
          new CodeStyleSettingRepresentation("SPACE_BEFORE_METHOD_CALL_PARENTHESES",
                                             ApplicationBundle.message("checkbox.spaces.method.call.parentheses")),
          new CodeStyleSettingRepresentation("SPACE_BEFORE_IF_PARENTHESES", ApplicationBundle.message("checkbox.spaces.if.parentheses")),
          new CodeStyleSettingRepresentation("SPACE_BEFORE_FOR_PARENTHESES", ApplicationBundle.message("checkbox.spaces.for.parentheses")),
          new CodeStyleSettingRepresentation("SPACE_BEFORE_WHILE_PARENTHESES",
                                             ApplicationBundle.message("checkbox.spaces.while.parentheses")),
          new CodeStyleSettingRepresentation("SPACE_BEFORE_SWITCH_PARENTHESES",
                                             ApplicationBundle.message("checkbox.spaces.switch.parentheses")),
          new CodeStyleSettingRepresentation("SPACE_BEFORE_TRY_PARENTHESES", ApplicationBundle.message("checkbox.spaces.try.parentheses")),
          new CodeStyleSettingRepresentation("SPACE_BEFORE_CATCH_PARENTHESES",
                                             ApplicationBundle.message("checkbox.spaces.catch.parentheses")),
          new CodeStyleSettingRepresentation("SPACE_BEFORE_SYNCHRONIZED_PARENTHESES",
                                             ApplicationBundle.message("checkbox.spaces.synchronized.parentheses")),
          new CodeStyleSettingRepresentation("SPACE_BEFORE_ANOTATION_PARAMETER_LIST",
                                             ApplicationBundle.message("checkbox.spaces.annotation.parameters"))
        ));

        result.put(new SettingsGroup(SPACES_AROUND_OPERATORS), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("SPACE_AROUND_ASSIGNMENT_OPERATORS",
                                             ApplicationBundle.message("checkbox.spaces.assignment.operators")),
          new CodeStyleSettingRepresentation("SPACE_AROUND_LOGICAL_OPERATORS",
                                             ApplicationBundle.message("checkbox.spaces.logical.operators")),
          new CodeStyleSettingRepresentation("SPACE_AROUND_EQUALITY_OPERATORS",
                                             ApplicationBundle.message("checkbox.spaces.equality.operators")),
          new CodeStyleSettingRepresentation("SPACE_AROUND_RELATIONAL_OPERATORS",
                                             ApplicationBundle.message("checkbox.spaces.relational.operators")),
          new CodeStyleSettingRepresentation("SPACE_AROUND_BITWISE_OPERATORS",
                                             ApplicationBundle.message("checkbox.spaces.bitwise.operators")),
          new CodeStyleSettingRepresentation("SPACE_AROUND_ADDITIVE_OPERATORS",
                                             ApplicationBundle.message("checkbox.spaces.additive.operators")),
          new CodeStyleSettingRepresentation("SPACE_AROUND_MULTIPLICATIVE_OPERATORS",
                                             ApplicationBundle.message("checkbox.spaces.multiplicative.operators")),
          new CodeStyleSettingRepresentation("SPACE_AROUND_SHIFT_OPERATORS", ApplicationBundle.message("checkbox.spaces.shift.operators")),
          new CodeStyleSettingRepresentation("SPACE_AROUND_UNARY_OPERATOR",
                                             ApplicationBundle.message("checkbox.spaces.around.unary.operator")),
          new CodeStyleSettingRepresentation("SPACE_AROUND_LAMBDA_ARROW", ApplicationBundle.message("checkbox.spaces.around.lambda.arrow")),
          new CodeStyleSettingRepresentation("SPACE_AROUND_METHOD_REF_DBL_COLON",
                                             ApplicationBundle.message("checkbox.spaces.around.method.ref.dbl.colon.arrow"))
        ));

        result.put(new SettingsGroup(SPACES_BEFORE_LEFT_BRACE), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("SPACE_BEFORE_CLASS_LBRACE", ApplicationBundle.message("checkbox.spaces.class.left.brace")),
          new CodeStyleSettingRepresentation("SPACE_BEFORE_METHOD_LBRACE", ApplicationBundle.message("checkbox.spaces.method.left.brace")),
          new CodeStyleSettingRepresentation("SPACE_BEFORE_IF_LBRACE", ApplicationBundle.message("checkbox.spaces.if.left.brace")),
          new CodeStyleSettingRepresentation("SPACE_BEFORE_ELSE_LBRACE", ApplicationBundle.message("checkbox.spaces.else.left.brace")),
          new CodeStyleSettingRepresentation("SPACE_BEFORE_FOR_LBRACE", ApplicationBundle.message("checkbox.spaces.for.left.brace")),
          new CodeStyleSettingRepresentation("SPACE_BEFORE_WHILE_LBRACE", ApplicationBundle.message("checkbox.spaces.while.left.brace")),
          new CodeStyleSettingRepresentation("SPACE_BEFORE_DO_LBRACE", ApplicationBundle.message("checkbox.spaces.do.left.brace")),
          new CodeStyleSettingRepresentation("SPACE_BEFORE_SWITCH_LBRACE", ApplicationBundle.message("checkbox.spaces.switch.left.brace")),
          new CodeStyleSettingRepresentation("SPACE_BEFORE_TRY_LBRACE", ApplicationBundle.message("checkbox.spaces.try.left.brace")),
          new CodeStyleSettingRepresentation("SPACE_BEFORE_CATCH_LBRACE", ApplicationBundle.message("checkbox.spaces.catch.left.brace")),
          new CodeStyleSettingRepresentation("SPACE_BEFORE_FINALLY_LBRACE",
                                             ApplicationBundle.message("checkbox.spaces.finally.left.brace")),
          new CodeStyleSettingRepresentation("SPACE_BEFORE_SYNCHRONIZED_LBRACE",
                                             ApplicationBundle.message("checkbox.spaces.synchronized.left.brace")),
          new CodeStyleSettingRepresentation("SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE",
                                             ApplicationBundle.message("checkbox.spaces.array.initializer.left.brace")),
          new CodeStyleSettingRepresentation("SPACE_BEFORE_ANNOTATION_ARRAY_INITIALIZER_LBRACE",
                                             ApplicationBundle.message("checkbox.spaces.annotation.array.initializer.left.brace"))
        ));

        result.put(new SettingsGroup(SPACES_BEFORE_KEYWORD), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("SPACE_BEFORE_ELSE_KEYWORD", ApplicationBundle.message("checkbox.spaces.else.keyword")),
          new CodeStyleSettingRepresentation("SPACE_BEFORE_WHILE_KEYWORD", ApplicationBundle.message("checkbox.spaces.while.keyword")),
          new CodeStyleSettingRepresentation("SPACE_BEFORE_CATCH_KEYWORD", ApplicationBundle.message("checkbox.spaces.catch.keyword")),
          new CodeStyleSettingRepresentation("SPACE_BEFORE_FINALLY_KEYWORD", ApplicationBundle.message("checkbox.spaces.finally.keyword"))
        ));

        result.put(new SettingsGroup(SPACES_WITHIN), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("SPACE_WITHIN_BRACES", ApplicationBundle.message("checkbox.spaces.within.braces")),
          new CodeStyleSettingRepresentation("SPACE_WITHIN_BRACKETS", ApplicationBundle.message("checkbox.spaces.within.brackets")),
          new CodeStyleSettingRepresentation("SPACE_WITHIN_ARRAY_INITIALIZER_BRACES",
                                             ApplicationBundle.message("checkbox.spaces.within.array.initializer.braces")),
          new CodeStyleSettingRepresentation("SPACE_WITHIN_EMPTY_ARRAY_INITIALIZER_BRACES",
                                             ApplicationBundle.message("checkbox.spaces.within.empty.array.initializer.braces")),
          new CodeStyleSettingRepresentation("SPACE_WITHIN_PARENTHESES", ApplicationBundle.message("checkbox.spaces.within.parentheses")),
          new CodeStyleSettingRepresentation("SPACE_WITHIN_METHOD_CALL_PARENTHESES",
                                             ApplicationBundle.message("checkbox.spaces.checkbox.spaces.method.call.parentheses")),
          new CodeStyleSettingRepresentation("SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES",
                                             ApplicationBundle.message("checkbox.spaces.checkbox.spaces.empty.method.call.parentheses")),
          new CodeStyleSettingRepresentation("SPACE_WITHIN_METHOD_PARENTHESES",
                                             ApplicationBundle.message("checkbox.spaces.checkbox.spaces.method.declaration.parentheses")),
          new CodeStyleSettingRepresentation("SPACE_WITHIN_EMPTY_METHOD_PARENTHESES", ApplicationBundle
            .message("checkbox.spaces.checkbox.spaces.empty.method.declaration.parentheses")),
          new CodeStyleSettingRepresentation("SPACE_WITHIN_IF_PARENTHESES", ApplicationBundle.message("checkbox.spaces.if.parentheses")),
          new CodeStyleSettingRepresentation("SPACE_WITHIN_FOR_PARENTHESES", ApplicationBundle.message("checkbox.spaces.for.parentheses")),
          new CodeStyleSettingRepresentation("SPACE_WITHIN_WHILE_PARENTHESES",
                                             ApplicationBundle.message("checkbox.spaces.while.parentheses")),
          new CodeStyleSettingRepresentation("SPACE_WITHIN_SWITCH_PARENTHESES",
                                             ApplicationBundle.message("checkbox.spaces.switch.parentheses")),
          new CodeStyleSettingRepresentation("SPACE_WITHIN_TRY_PARENTHESES", ApplicationBundle.message("checkbox.spaces.try.parentheses")),
          new CodeStyleSettingRepresentation("SPACE_WITHIN_CATCH_PARENTHESES",
                                             ApplicationBundle.message("checkbox.spaces.catch.parentheses")),
          new CodeStyleSettingRepresentation("SPACE_WITHIN_SYNCHRONIZED_PARENTHESES",
                                             ApplicationBundle.message("checkbox.spaces.synchronized.parentheses")),
          new CodeStyleSettingRepresentation("SPACE_WITHIN_CAST_PARENTHESES",
                                             ApplicationBundle.message("checkbox.spaces.type.cast.parentheses")),
          new CodeStyleSettingRepresentation("SPACE_WITHIN_ANNOTATION_PARENTHESES",
                                             ApplicationBundle.message("checkbox.spaces.annotation.parentheses"))
        ));

        result.put(new SettingsGroup(SPACES_IN_TERNARY_OPERATOR), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("SPACE_BEFORE_QUEST", ApplicationBundle.message("checkbox.spaces.before.question")),
          new CodeStyleSettingRepresentation("SPACE_AFTER_QUEST", ApplicationBundle.message("checkbox.spaces.after.question")),
          new CodeStyleSettingRepresentation("SPACE_BEFORE_COLON", ApplicationBundle.message("checkbox.spaces.before.colon")),
          new CodeStyleSettingRepresentation("SPACE_AFTER_COLON", ApplicationBundle.message("checkbox.spaces.after.colon"))
        ));

        result.put(new SettingsGroup(SPACES_WITHIN_TYPE_ARGUMENTS), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS",
                                             ApplicationBundle.message("checkbox.spaces.after.comma"))
        ));

        result.put(new SettingsGroup(SPACES_IN_TYPE_ARGUMENTS), ContainerUtil.<CodeStyleSettingRepresentation>newLinkedList());

        result.put(new SettingsGroup(SPACES_IN_TYPE_PARAMETERS), ContainerUtil.<CodeStyleSettingRepresentation>newLinkedList());
        
        result.put(new SettingsGroup(SPACES_OTHER), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("SPACE_BEFORE_COMMA", ApplicationBundle.message("checkbox.spaces.before.comma")),
          new CodeStyleSettingRepresentation("SPACE_AFTER_COMMA", ApplicationBundle.message("checkbox.spaces.after.comma")),
          new CodeStyleSettingRepresentation("SPACE_BEFORE_SEMICOLON", ApplicationBundle.message("checkbox.spaces.before.semicolon")),
          new CodeStyleSettingRepresentation("SPACE_AFTER_SEMICOLON", ApplicationBundle.message("checkbox.spaces.after.semicolon")),
          new CodeStyleSettingRepresentation("SPACE_AFTER_TYPE_CAST", ApplicationBundle.message("checkbox.spaces.after.type.cast"))
        ));
        break;
      case WRAPPING_AND_BRACES_SETTINGS:
        result.put(new SettingsGroup(null), ContainerUtil.<CodeStyleSettingRepresentation>newLinkedList(
          new CodeStyleIntegerSettingRepresentation("RIGHT_MARGIN", ApplicationBundle.message("editbox.right.margin.columns"), 0, 999, -1,
                                                    ApplicationBundle.message("settings.code.style.default.general"))
        ));

        putGroupTop(result, "WRAP_ON_TYPING", ApplicationBundle.message("wrapping.wrap.on.typing"), WRAP_ON_TYPING_VALUES,
                    WRAP_ON_TYPING_OPTIONS);

        result.put(new SettingsGroup(WRAPPING_KEEP), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("KEEP_LINE_BREAKS", ApplicationBundle.message("wrapping.keep.line.breaks")),
          new CodeStyleSettingRepresentation("KEEP_FIRST_COLUMN_COMMENT",
                                             ApplicationBundle.message("wrapping.keep.comment.at.first.column")),
          new CodeStyleSettingRepresentation("KEEP_CONTROL_STATEMENT_IN_ONE_LINE",
                                             ApplicationBundle.message("checkbox.keep.when.reformatting.control.statement.in.one.line")),
          new CodeStyleSettingRepresentation("KEEP_MULTIPLE_EXPRESSIONS_IN_ONE_LINE",
                                             ApplicationBundle.message("wrapping.keep.multiple.expressions.in.one.line")),
          new CodeStyleSettingRepresentation("KEEP_SIMPLE_BLOCKS_IN_ONE_LINE",
                                             ApplicationBundle.message("wrapping.keep.simple.blocks.in.one.line")),
          new CodeStyleSettingRepresentation("KEEP_SIMPLE_METHODS_IN_ONE_LINE",
                                             ApplicationBundle.message("wrapping.keep.simple.methods.in.one.line")),
          new CodeStyleSettingRepresentation("KEEP_SIMPLE_CLASSES_IN_ONE_LINE",
                                             ApplicationBundle.message("wrapping.keep.simple.classes.in.one.line"))
        ));

        result.put(new SettingsGroup(null), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("WRAP_LONG_LINES", ApplicationBundle.message("wrapping.long.lines"))
        ));

        result.put(new SettingsGroup(WRAPPING_COMMENTS), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("WRAP_COMMENTS", ApplicationBundle.message("wrapping.comments.wrap.at.right.margin"))
        ));

        result.put(new SettingsGroup(WRAPPING_BRACES), ContainerUtil.<CodeStyleSettingRepresentation>newLinkedList(
          new CodeStyleSelectSettingRepresentation("CLASS_BRACE_STYLE",
                                                   ApplicationBundle.message("wrapping.brace.placement.class.declaration"),
                                                   BRACE_PLACEMENT_VALUES, BRACE_PLACEMENT_OPTIONS),
          new CodeStyleSelectSettingRepresentation("METHOD_BRACE_STYLE",
                                                   ApplicationBundle.message("wrapping.brace.placement.method.declaration"),
                                                   BRACE_PLACEMENT_VALUES, BRACE_PLACEMENT_OPTIONS),
          new CodeStyleSelectSettingRepresentation("BRACE_STYLE", ApplicationBundle.message("wrapping.brace.placement.other"),
                                                   BRACE_PLACEMENT_VALUES, BRACE_PLACEMENT_OPTIONS)

        ));

        putGroupTop(result, "EXTENDS_LIST_WRAP", WRAPPING_EXTENDS_LIST, WRAP_VALUES, WRAP_OPTIONS);
        result.put(new SettingsGroup(WRAPPING_EXTENDS_LIST), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("ALIGN_MULTILINE_EXTENDS_LIST", ApplicationBundle.message("wrapping.align.when.multiline"))
        ));

        putGroupTop(result, "EXTENDS_KEYWORD_WRAP", WRAPPING_EXTENDS_KEYWORD, WRAP_VALUES_FOR_SINGLETON, WRAP_OPTIONS_FOR_SINGLETON);

        putGroupTop(result, "THROWS_LIST_WRAP", WRAPPING_THROWS_LIST, WRAP_VALUES, WRAP_OPTIONS);
        result.put(new SettingsGroup(WRAPPING_THROWS_LIST), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("ALIGN_MULTILINE_THROWS_LIST", ApplicationBundle.message("wrapping.align.when.multiline")),
          new CodeStyleSettingRepresentation("ALIGN_THROWS_KEYWORD", ApplicationBundle.message("wrapping.align.throws.keyword"))
        ));

        putGroupTop(result, "THROWS_KEYWORD_WRAP", WRAPPING_THROWS_KEYWORD, WRAP_VALUES_FOR_SINGLETON, WRAP_OPTIONS_FOR_SINGLETON);

        putGroupTop(result, "METHOD_PARAMETERS_WRAP", WRAPPING_METHOD_PARAMETERS, WRAP_VALUES, WRAP_OPTIONS);
        result.put(new SettingsGroup(WRAPPING_METHOD_PARAMETERS), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("ALIGN_MULTILINE_PARAMETERS", ApplicationBundle.message("wrapping.align.when.multiline")),
          new CodeStyleSettingRepresentation("METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE",
                                             ApplicationBundle.message("wrapping.new.line.after.lpar")),
          new CodeStyleSettingRepresentation("METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE",
                                             ApplicationBundle.message("wrapping.rpar.on.new.line"))
        ));

        putGroupTop(result, "CALL_PARAMETERS_WRAP", WRAPPING_METHOD_ARGUMENTS_WRAPPING, WRAP_VALUES, WRAP_OPTIONS);
        result.put(new SettingsGroup(WRAPPING_METHOD_ARGUMENTS_WRAPPING), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("ALIGN_MULTILINE_PARAMETERS_IN_CALLS",
                                             ApplicationBundle.message("wrapping.align.when.multiline")),
          new CodeStyleSettingRepresentation("PREFER_PARAMETERS_WRAP",
                                             ApplicationBundle.message("wrapping.take.priority.over.call.chain.wrapping")),
          new CodeStyleSettingRepresentation("CALL_PARAMETERS_LPAREN_ON_NEXT_LINE",
                                             ApplicationBundle.message("wrapping.new.line.after.lpar")),
          new CodeStyleSettingRepresentation("CALL_PARAMETERS_RPAREN_ON_NEXT_LINE", ApplicationBundle.message("wrapping.rpar.on.new.line"))
        ));

        result.put(new SettingsGroup(WRAPPING_METHOD_PARENTHESES), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("ALIGN_MULTILINE_METHOD_BRACKETS", ApplicationBundle.message("wrapping.align.when.multiline"))
        ));

        putGroupTop(result, "METHOD_CALL_CHAIN_WRAP", WRAPPING_CALL_CHAIN, WRAP_VALUES, WRAP_OPTIONS);
        result.put(new SettingsGroup(WRAPPING_CALL_CHAIN), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("WRAP_FIRST_METHOD_IN_CALL_CHAIN",
                                             ApplicationBundle.message("wrapping.chained.method.call.first.on.new.line")),
          new CodeStyleSettingRepresentation("ALIGN_MULTILINE_CHAINED_METHODS", ApplicationBundle.message("wrapping.align.when.multiline"))
        ));

        result.put(new SettingsGroup(WRAPPING_IF_STATEMENT), ContainerUtil.newLinkedList(
          new CodeStyleSelectSettingRepresentation("IF_BRACE_FORCE", ApplicationBundle.message("wrapping.force.braces"), BRACE_VALUES,
                                                   BRACE_OPTIONS),
          new CodeStyleSettingRepresentation("ELSE_ON_NEW_LINE", ApplicationBundle.message("wrapping.else.on.new.line")),
          new CodeStyleSettingRepresentation("SPECIAL_ELSE_IF_TREATMENT",
                                             ApplicationBundle.message("wrapping.special.else.if.braces.treatment"))
        ));

        putGroupTop(result, "FOR_STATEMENT_WRAP", WRAPPING_FOR_STATEMENT, WRAP_VALUES, WRAP_OPTIONS);
        result.put(new SettingsGroup(WRAPPING_FOR_STATEMENT), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("ALIGN_MULTILINE_FOR", ApplicationBundle.message("wrapping.align.when.multiline")),
          new CodeStyleSettingRepresentation("FOR_STATEMENT_LPAREN_ON_NEXT_LINE",
                                             ApplicationBundle.message("wrapping.new.line.after.lpar")),
          new CodeStyleSettingRepresentation("FOR_STATEMENT_RPAREN_ON_NEXT_LINE", ApplicationBundle.message("wrapping.rpar.on.new.line")),
          new CodeStyleSelectSettingRepresentation("FOR_BRACE_FORCE", ApplicationBundle.message("wrapping.force.braces"), BRACE_VALUES,
                                                   BRACE_OPTIONS)
        ));

        result.put(new SettingsGroup(WRAPPING_WHILE_STATEMENT), ContainerUtil.<CodeStyleSettingRepresentation>newLinkedList(
          new CodeStyleSelectSettingRepresentation("WHILE_BRACE_FORCE", ApplicationBundle.message("wrapping.force.braces"), BRACE_VALUES,
                                                   BRACE_OPTIONS)
        ));

        result.put(new SettingsGroup(WRAPPING_DOWHILE_STATEMENT), ContainerUtil.newLinkedList(
          new CodeStyleSelectSettingRepresentation("DOWHILE_BRACE_FORCE", ApplicationBundle.message("wrapping.force.braces"), BRACE_VALUES,
                                                   BRACE_OPTIONS),
          new CodeStyleSettingRepresentation("WHILE_ON_NEW_LINE", ApplicationBundle.message("wrapping.while.on.new.line"))
        ));

        result.put(new SettingsGroup(WRAPPING_SWITCH_STATEMENT), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("INDENT_CASE_FROM_SWITCH", ApplicationBundle.message("wrapping.indent.case.from.switch")),
          new CodeStyleSettingRepresentation("INDENT_BREAK_FROM_CASE", ApplicationBundle.message("wrapping.indent.break.from.case"))
        ));

        putGroupTop(result, "RESOURCE_LIST_WRAP", WRAPPING_TRY_RESOURCE_LIST, WRAP_VALUES, WRAP_OPTIONS);
        result.put(new SettingsGroup(WRAPPING_TRY_RESOURCE_LIST), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("ALIGN_MULTILINE_RESOURCES", ApplicationBundle.message("wrapping.align.when.multiline")),
          new CodeStyleSettingRepresentation("RESOURCE_LIST_LPAREN_ON_NEXT_LINE",
                                             ApplicationBundle.message("wrapping.new.line.after.lpar")),
          new CodeStyleSettingRepresentation("RESOURCE_LIST_RPAREN_ON_NEXT_LINE", ApplicationBundle.message("wrapping.rpar.on.new.line"))
        ));

        result.put(new SettingsGroup(WRAPPING_TRY_STATEMENT), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("CATCH_ON_NEW_LINE", ApplicationBundle.message("wrapping.catch.on.new.line")),
          new CodeStyleSettingRepresentation("FINALLY_ON_NEW_LINE", ApplicationBundle.message("wrapping.finally.on.new.line"))
        ));

        putGroupTop(result, "BINARY_OPERATION_WRAP", WRAPPING_BINARY_OPERATION, WRAP_VALUES, WRAP_OPTIONS);
        result.put(new SettingsGroup(WRAPPING_BINARY_OPERATION), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("ALIGN_MULTILINE_BINARY_OPERATION",
                                             ApplicationBundle.message("wrapping.align.when.multiline")),
          new CodeStyleSettingRepresentation("BINARY_OPERATION_SIGN_ON_NEXT_LINE",
                                             ApplicationBundle.message("wrapping.operation.sign.on.next.line")),
          new CodeStyleSettingRepresentation("ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION",
                                             ApplicationBundle.message("wrapping.align.parenthesised.when.multiline")),
          new CodeStyleSettingRepresentation("PARENTHESES_EXPRESSION_LPAREN_WRAP",
                                             ApplicationBundle.message("wrapping.new.line.after.lpar")),
          new CodeStyleSettingRepresentation("PARENTHESES_EXPRESSION_RPAREN_WRAP", ApplicationBundle.message("wrapping.rpar.on.new.line"))
        ));

        putGroupTop(result, "ASSIGNMENT_WRAP", WRAPPING_ASSIGNMENT, WRAP_VALUES, WRAP_OPTIONS);
        result.put(new SettingsGroup(WRAPPING_ASSIGNMENT), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("ALIGN_MULTILINE_ASSIGNMENT", ApplicationBundle.message("wrapping.align.when.multiline")),
          new CodeStyleSettingRepresentation("PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE",
                                             ApplicationBundle.message("wrapping.assignment.sign.on.next.line"))
        ));

        result.put(new SettingsGroup(WRAPPING_FIELDS_VARIABLES_GROUPS), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("ALIGN_GROUP_FIELD_DECLARATIONS",
                                             ApplicationBundle.message("wrapping.align.fields.in.columns")),
          new CodeStyleSettingRepresentation("ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS",
                                             ApplicationBundle.message("wrapping.align.variables.in.columns"))
        ));

        putGroupTop(result, "TERNARY_OPERATION_WRAP", WRAPPING_TERNARY_OPERATION, WRAP_VALUES, WRAP_OPTIONS);
        result.put(new SettingsGroup(WRAPPING_TERNARY_OPERATION), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("ALIGN_MULTILINE_TERNARY_OPERATION",
                                             ApplicationBundle.message("wrapping.align.when.multiline")),
          new CodeStyleSettingRepresentation("TERNARY_OPERATION_SIGNS_ON_NEXT_LINE",
                                             ApplicationBundle.message("wrapping.quest.and.colon.signs.on.next.line"))
        ));

        putGroupTop(result, "ARRAY_INITIALIZER_WRAP", WRAPPING_ARRAY_INITIALIZER, WRAP_VALUES, WRAP_OPTIONS);
        result.put(new SettingsGroup(WRAPPING_ARRAY_INITIALIZER), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION",
                                             ApplicationBundle.message("wrapping.align.when.multiline")),
          new CodeStyleSettingRepresentation("ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE",
                                             ApplicationBundle.message("wrapping.new.line.after.lbrace")),
          new CodeStyleSettingRepresentation("ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE",
                                             ApplicationBundle.message("wrapping.rbrace.on.new.line"))
        ));

        result.put(new SettingsGroup(WRAPPING_MODIFIER_LIST), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("MODIFIER_LIST_WRAP", ApplicationBundle.message("wrapping.after.modifier.list"))
        ));

        putGroupTop(result, "ASSERT_STATEMENT_WRAP", WRAPPING_ASSERT_STATEMENT, WRAP_VALUES, WRAP_OPTIONS);
        result.put(new SettingsGroup(WRAPPING_ASSERT_STATEMENT), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("ASSERT_STATEMENT_COLON_ON_NEXT_LINE",
                                             ApplicationBundle.message("wrapping.colon.signs.on.next.line"))
        ));

        putGroupTop(result, "ENUM_CONSTANTS_WRAP", ApplicationBundle.message("wrapping.enum.constants"), WRAP_VALUES, WRAP_OPTIONS);
        putGroupTop(result, "CLASS_ANNOTATION_WRAP", ApplicationBundle.message("wrapping.classes.annotation"), WRAP_VALUES, WRAP_OPTIONS);
        putGroupTop(result, "METHOD_ANNOTATION_WRAP", ApplicationBundle.message("wrapping.methods.annotation"), WRAP_VALUES, WRAP_OPTIONS);
        putGroupTop(result, "FIELD_ANNOTATION_WRAP", ApplicationBundle.message("wrapping.fields.annotation"), WRAP_VALUES, WRAP_OPTIONS);
        putGroupTop(result, "PARAMETER_ANNOTATION_WRAP", ApplicationBundle.message("wrapping.parameters.annotation"), WRAP_VALUES, WRAP_OPTIONS);
        putGroupTop(result, "VARIABLE_ANNOTATION_WRAP", ApplicationBundle.message("wrapping.local.variables.annotation"), WRAP_VALUES,
                    WRAP_OPTIONS);
        break;
      case INDENT_SETTINGS:
        result.put(new SettingsGroup(null), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("INDENT_SIZE", ApplicationBundle.message("editbox.indent.indent"))
        ));
        result.put(new SettingsGroup(null), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("CONTINUATION_INDENT_SIZE", ApplicationBundle.message("editbox.indent.continuation.indent"))
        ));
        result.put(new SettingsGroup(null), ContainerUtil.newLinkedList(
          new CodeStyleSettingRepresentation("TAB_SIZE", ApplicationBundle.message("editbox.indent.tab.size"))
        ));
        break;
      case LANGUAGE_SPECIFIC:
    }
    return result;
  }
}
