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
package com.intellij.psi.codeStyle.presentation;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable.*;

/**
 * @author Roman.Shein
 * @since 15.09.2015.
 */
public class CodeStyleSettingPresentation {

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

  public CodeStyleSettingPresentation(@NotNull String fieldName, @NotNull String uiName) {
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
    return (o instanceof CodeStyleSettingPresentation) && ((CodeStyleSettingPresentation)o).getFieldName().equals(getFieldName());
  }

  @Override
  public int hashCode() {
    return myFieldName.hashCode();
  }

  protected static void putGroupTop(@NotNull Map<CodeStyleSettingPresentation.SettingsGroup, List<CodeStyleSettingPresentation>> result,
                                    @NotNull String fieldName,
                                    @NotNull String uiName, int[] values, String[] valueUiNames) {
    result.put(new SettingsGroup(null), ContainerUtil.immutableList(
      new CodeStyleSelectSettingPresentation(fieldName, uiName, values, valueUiNames)
    ));
  }

  protected static final Map<SettingsGroup, List<CodeStyleSettingPresentation>> BLANK_LINES_STANDARD_SETTINGS;
  protected static final Map<SettingsGroup, List<CodeStyleSettingPresentation>> SPACING_STANDARD_SETTINGS;
  protected static final Map<SettingsGroup, List<CodeStyleSettingPresentation>> WRAPPING_AND_BRACES_STANDARD_SETTINGS;
  protected static final Map<SettingsGroup, List<CodeStyleSettingPresentation>> INDENT_STANDARD_SETTINGS;
  static {

    //-----------------------------------BLANK_LINES_SETTINGS-----------------------------------------------------

    Map<SettingsGroup, List<CodeStyleSettingPresentation>> result = ContainerUtil.newLinkedHashMap();
    result.put(new SettingsGroup(BLANK_LINES_KEEP), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("KEEP_BLANK_LINES_IN_DECLARATIONS",
                                       ApplicationBundle.message("editbox.keep.blanklines.in.declarations")),
      new CodeStyleSettingPresentation("KEEP_BLANK_LINES_IN_CODE", ApplicationBundle.message("editbox.keep.blanklines.in.code")),
      new CodeStyleSettingPresentation("KEEP_BLANK_LINES_BEFORE_RBRACE",
                                       ApplicationBundle.message("editbox.keep.blanklines.before.rbrace"))
    ));

    result.put(new SettingsGroup(BLANK_LINES), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("BLANK_LINES_BEFORE_PACKAGE",
                                       ApplicationBundle.message("editbox.blanklines.before.package.statement")),
      new CodeStyleSettingPresentation("BLANK_LINES_AFTER_PACKAGE",
                                       ApplicationBundle.message("editbox.blanklines.after.package.statement")),
      new CodeStyleSettingPresentation("BLANK_LINES_BEFORE_IMPORTS", ApplicationBundle.message("editbox.blanklines.before.imports")),
      new CodeStyleSettingPresentation("BLANK_LINES_AFTER_IMPORTS", ApplicationBundle.message("editbox.blanklines.after.imports")),
      new CodeStyleSettingPresentation("BLANK_LINES_AROUND_CLASS", ApplicationBundle.message("editbox.blanklines.around.class")),
      new CodeStyleSettingPresentation("BLANK_LINES_AFTER_CLASS_HEADER",
                                       ApplicationBundle.message("editbox.blanklines.after.class.header")),
      new CodeStyleSettingPresentation("BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER",
                                       ApplicationBundle.message("editbox.blanklines.after.anonymous.class.header")),
      new CodeStyleSettingPresentation("BLANK_LINES_AROUND_FIELD_IN_INTERFACE", "Around field in interface:"),
      //TODO why is this not loaded from bundle??
      new CodeStyleSettingPresentation("BLANK_LINES_AROUND_FIELD", ApplicationBundle.message("editbox.blanklines.around.field")),
      new CodeStyleSettingPresentation("BLANK_LINES_AROUND_METHOD_IN_INTERFACE", "Around method in interface:"),
      //TODO why is this not loaded from bundle??
      new CodeStyleSettingPresentation("BLANK_LINES_AROUND_METHOD", ApplicationBundle.message("editbox.blanklines.around.method")),
      new CodeStyleSettingPresentation("BLANK_LINES_BEFORE_METHOD_BODY",
                                       ApplicationBundle.message("editbox.blanklines.before.method.body"))
    ));
    BLANK_LINES_STANDARD_SETTINGS = Collections.unmodifiableMap(result);

    //-----------------------------------SPACING_SETTINGS-----------------------------------------------------

    result = ContainerUtil.newLinkedHashMap();
    result.put(new SettingsGroup(SPACES_BEFORE_PARENTHESES), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("SPACE_BEFORE_METHOD_PARENTHESES",
                                       ApplicationBundle.message("checkbox.spaces.method.declaration.parentheses")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_METHOD_CALL_PARENTHESES",
                                       ApplicationBundle.message("checkbox.spaces.method.call.parentheses")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_IF_PARENTHESES", ApplicationBundle.message("checkbox.spaces.if.parentheses")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_FOR_PARENTHESES", ApplicationBundle.message("checkbox.spaces.for.parentheses")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_WHILE_PARENTHESES",
                                       ApplicationBundle.message("checkbox.spaces.while.parentheses")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_SWITCH_PARENTHESES",
                                       ApplicationBundle.message("checkbox.spaces.switch.parentheses")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_TRY_PARENTHESES", ApplicationBundle.message("checkbox.spaces.try.parentheses")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_CATCH_PARENTHESES",
                                       ApplicationBundle.message("checkbox.spaces.catch.parentheses")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_SYNCHRONIZED_PARENTHESES",
                                       ApplicationBundle.message("checkbox.spaces.synchronized.parentheses")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_ANOTATION_PARAMETER_LIST",
                                       ApplicationBundle.message("checkbox.spaces.annotation.parameters"))
    ));

    result.put(new SettingsGroup(SPACES_AROUND_OPERATORS), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("SPACE_AROUND_ASSIGNMENT_OPERATORS",
                                       ApplicationBundle.message("checkbox.spaces.assignment.operators")),
      new CodeStyleSettingPresentation("SPACE_AROUND_LOGICAL_OPERATORS",
                                       ApplicationBundle.message("checkbox.spaces.logical.operators")),
      new CodeStyleSettingPresentation("SPACE_AROUND_EQUALITY_OPERATORS",
                                       ApplicationBundle.message("checkbox.spaces.equality.operators")),
      new CodeStyleSettingPresentation("SPACE_AROUND_RELATIONAL_OPERATORS",
                                       ApplicationBundle.message("checkbox.spaces.relational.operators")),
      new CodeStyleSettingPresentation("SPACE_AROUND_BITWISE_OPERATORS",
                                       ApplicationBundle.message("checkbox.spaces.bitwise.operators")),
      new CodeStyleSettingPresentation("SPACE_AROUND_ADDITIVE_OPERATORS",
                                       ApplicationBundle.message("checkbox.spaces.additive.operators")),
      new CodeStyleSettingPresentation("SPACE_AROUND_MULTIPLICATIVE_OPERATORS",
                                       ApplicationBundle.message("checkbox.spaces.multiplicative.operators")),
      new CodeStyleSettingPresentation("SPACE_AROUND_SHIFT_OPERATORS", ApplicationBundle.message("checkbox.spaces.shift.operators")),
      new CodeStyleSettingPresentation("SPACE_AROUND_UNARY_OPERATOR",
                                       ApplicationBundle.message("checkbox.spaces.around.unary.operator")),
      new CodeStyleSettingPresentation("SPACE_AROUND_LAMBDA_ARROW", ApplicationBundle.message("checkbox.spaces.around.lambda.arrow")),
      new CodeStyleSettingPresentation("SPACE_AROUND_METHOD_REF_DBL_COLON",
                                       ApplicationBundle.message("checkbox.spaces.around.method.ref.dbl.colon.arrow"))
    ));

    result.put(new SettingsGroup(SPACES_BEFORE_LEFT_BRACE), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("SPACE_BEFORE_CLASS_LBRACE", ApplicationBundle.message("checkbox.spaces.class.left.brace")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_METHOD_LBRACE", ApplicationBundle.message("checkbox.spaces.method.left.brace")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_IF_LBRACE", ApplicationBundle.message("checkbox.spaces.if.left.brace")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_ELSE_LBRACE", ApplicationBundle.message("checkbox.spaces.else.left.brace")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_FOR_LBRACE", ApplicationBundle.message("checkbox.spaces.for.left.brace")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_WHILE_LBRACE", ApplicationBundle.message("checkbox.spaces.while.left.brace")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_DO_LBRACE", ApplicationBundle.message("checkbox.spaces.do.left.brace")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_SWITCH_LBRACE", ApplicationBundle.message("checkbox.spaces.switch.left.brace")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_TRY_LBRACE", ApplicationBundle.message("checkbox.spaces.try.left.brace")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_CATCH_LBRACE", ApplicationBundle.message("checkbox.spaces.catch.left.brace")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_FINALLY_LBRACE",
                                       ApplicationBundle.message("checkbox.spaces.finally.left.brace")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_SYNCHRONIZED_LBRACE",
                                       ApplicationBundle.message("checkbox.spaces.synchronized.left.brace")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE",
                                       ApplicationBundle.message("checkbox.spaces.array.initializer.left.brace")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_ANNOTATION_ARRAY_INITIALIZER_LBRACE",
                                       ApplicationBundle.message("checkbox.spaces.annotation.array.initializer.left.brace"))
    ));

    result.put(new SettingsGroup(SPACES_BEFORE_KEYWORD), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("SPACE_BEFORE_ELSE_KEYWORD", ApplicationBundle.message("checkbox.spaces.else.keyword")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_WHILE_KEYWORD", ApplicationBundle.message("checkbox.spaces.while.keyword")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_CATCH_KEYWORD", ApplicationBundle.message("checkbox.spaces.catch.keyword")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_FINALLY_KEYWORD", ApplicationBundle.message("checkbox.spaces.finally.keyword"))
    ));

    result.put(new SettingsGroup(SPACES_WITHIN), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("SPACE_WITHIN_BRACES", ApplicationBundle.message("checkbox.spaces.within.braces")),
      new CodeStyleSettingPresentation("SPACE_WITHIN_BRACKETS", ApplicationBundle.message("checkbox.spaces.within.brackets")),
      new CodeStyleSettingPresentation("SPACE_WITHIN_ARRAY_INITIALIZER_BRACES",
                                       ApplicationBundle.message("checkbox.spaces.within.array.initializer.braces")),
      new CodeStyleSettingPresentation("SPACE_WITHIN_EMPTY_ARRAY_INITIALIZER_BRACES",
                                       ApplicationBundle.message("checkbox.spaces.within.empty.array.initializer.braces")),
      new CodeStyleSettingPresentation("SPACE_WITHIN_PARENTHESES", ApplicationBundle.message("checkbox.spaces.within.parentheses")),
      new CodeStyleSettingPresentation("SPACE_WITHIN_METHOD_PARENTHESES",
                                       ApplicationBundle.message("checkbox.spaces.checkbox.spaces.method.declaration.parentheses")),
      new CodeStyleSettingPresentation("SPACE_WITHIN_EMPTY_METHOD_PARENTHESES", 
                                       ApplicationBundle.message("checkbox.spaces.checkbox.spaces.empty.method.declaration.parentheses")),
      new CodeStyleSettingPresentation("SPACE_WITHIN_METHOD_CALL_PARENTHESES",
                                       ApplicationBundle.message("checkbox.spaces.checkbox.spaces.method.call.parentheses")),
      new CodeStyleSettingPresentation("SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES",
                                       ApplicationBundle.message("checkbox.spaces.checkbox.spaces.empty.method.call.parentheses")),
      new CodeStyleSettingPresentation("SPACE_WITHIN_IF_PARENTHESES", ApplicationBundle.message("checkbox.spaces.if.parentheses")),
      new CodeStyleSettingPresentation("SPACE_WITHIN_FOR_PARENTHESES", ApplicationBundle.message("checkbox.spaces.for.parentheses")),
      new CodeStyleSettingPresentation("SPACE_WITHIN_WHILE_PARENTHESES",
                                       ApplicationBundle.message("checkbox.spaces.while.parentheses")),
      new CodeStyleSettingPresentation("SPACE_WITHIN_SWITCH_PARENTHESES",
                                       ApplicationBundle.message("checkbox.spaces.switch.parentheses")),
      new CodeStyleSettingPresentation("SPACE_WITHIN_TRY_PARENTHESES", ApplicationBundle.message("checkbox.spaces.try.parentheses")),
      new CodeStyleSettingPresentation("SPACE_WITHIN_CATCH_PARENTHESES",
                                       ApplicationBundle.message("checkbox.spaces.catch.parentheses")),
      new CodeStyleSettingPresentation("SPACE_WITHIN_SYNCHRONIZED_PARENTHESES",
                                       ApplicationBundle.message("checkbox.spaces.synchronized.parentheses")),
      new CodeStyleSettingPresentation("SPACE_WITHIN_CAST_PARENTHESES",
                                       ApplicationBundle.message("checkbox.spaces.type.cast.parentheses")),
      new CodeStyleSettingPresentation("SPACE_WITHIN_ANNOTATION_PARENTHESES",
                                       ApplicationBundle.message("checkbox.spaces.annotation.parentheses"))
    ));

    result.put(new SettingsGroup(SPACES_IN_TERNARY_OPERATOR), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("SPACE_BEFORE_QUEST", ApplicationBundle.message("checkbox.spaces.before.question")),
      new CodeStyleSettingPresentation("SPACE_AFTER_QUEST", ApplicationBundle.message("checkbox.spaces.after.question")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_COLON", ApplicationBundle.message("checkbox.spaces.before.colon")),
      new CodeStyleSettingPresentation("SPACE_AFTER_COLON", ApplicationBundle.message("checkbox.spaces.after.colon"))
    ));

    result.put(new SettingsGroup(SPACES_WITHIN_TYPE_ARGUMENTS), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS",
                                       ApplicationBundle.message("checkbox.spaces.after.comma"))
    ));

    result.put(new SettingsGroup(SPACES_IN_TYPE_ARGUMENTS), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("SPACE_BEFORE_TYPE_PARAMETER_LIST", ApplicationBundle.message("checkbox.spaces.before.opening.angle.bracket"))
    ));

    result.put(new SettingsGroup(SPACES_IN_TYPE_PARAMETERS), ContainerUtil.immutableList());

    result.put(new SettingsGroup(SPACES_OTHER), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("SPACE_BEFORE_COMMA", ApplicationBundle.message("checkbox.spaces.before.comma")),
      new CodeStyleSettingPresentation("SPACE_AFTER_COMMA", ApplicationBundle.message("checkbox.spaces.after.comma")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_SEMICOLON", ApplicationBundle.message("checkbox.spaces.before.semicolon")),
      new CodeStyleSettingPresentation("SPACE_AFTER_SEMICOLON", ApplicationBundle.message("checkbox.spaces.after.semicolon")),
      new CodeStyleSettingPresentation("SPACE_AFTER_TYPE_CAST", ApplicationBundle.message("checkbox.spaces.after.type.cast"))
    ));
    SPACING_STANDARD_SETTINGS = Collections.unmodifiableMap(result);

    //-----------------------------------WRAPPING_AND_BRACES_SETTINGS-----------------------------------------------------

    result = ContainerUtil.newLinkedHashMap();
    result.put(new SettingsGroup(null), ContainerUtil.immutableList(
      new CodeStyleBoundedIntegerSettingPresentation("RIGHT_MARGIN", ApplicationBundle.message("editbox.right.margin.columns"), 0, 999,
                                                     -1,
                                                     ApplicationBundle.message("settings.code.style.default.general")),
      new CodeStyleSelectSettingPresentation("WRAP_ON_TYPING", ApplicationBundle.message("wrapping.wrap.on.typing"), WRAP_ON_TYPING_VALUES,
                                             WRAP_ON_TYPING_OPTIONS),
      new CodeStyleSoftMarginsPresentation()
    ));

    result.put(new SettingsGroup(WRAPPING_KEEP), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("KEEP_LINE_BREAKS", ApplicationBundle.message("wrapping.keep.line.breaks")),
      new CodeStyleSettingPresentation("KEEP_FIRST_COLUMN_COMMENT",
                                       ApplicationBundle.message("wrapping.keep.comment.at.first.column")),
      new CodeStyleSettingPresentation("KEEP_CONTROL_STATEMENT_IN_ONE_LINE",
                                       ApplicationBundle.message("checkbox.keep.when.reformatting.control.statement.in.one.line")),
      new CodeStyleSettingPresentation("KEEP_MULTIPLE_EXPRESSIONS_IN_ONE_LINE",
                                       ApplicationBundle.message("wrapping.keep.multiple.expressions.in.one.line")),
      new CodeStyleSettingPresentation("KEEP_SIMPLE_BLOCKS_IN_ONE_LINE",
                                       ApplicationBundle.message("wrapping.keep.simple.blocks.in.one.line")),
      new CodeStyleSettingPresentation("KEEP_SIMPLE_METHODS_IN_ONE_LINE",
                                       ApplicationBundle.message("wrapping.keep.simple.methods.in.one.line")),
      new CodeStyleSettingPresentation("KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE",
                                       ApplicationBundle.message("wrapping.keep.simple.lambdas.in.one.line")),
      new CodeStyleSettingPresentation("KEEP_SIMPLE_CLASSES_IN_ONE_LINE",
                                       ApplicationBundle.message("wrapping.keep.simple.classes.in.one.line"))
    ));

    result.put(new SettingsGroup(null), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("WRAP_LONG_LINES", ApplicationBundle.message("wrapping.long.lines"))
    ));

    result.put(new SettingsGroup(WRAPPING_COMMENTS), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("WRAP_COMMENTS", ApplicationBundle.message("wrapping.comments.wrap.at.right.margin"))
    ));

    result.put(new SettingsGroup(WRAPPING_BRACES), ContainerUtil.immutableList(
      new CodeStyleSelectSettingPresentation("CLASS_BRACE_STYLE",
                                             ApplicationBundle.message("wrapping.brace.placement.class.declaration"),
                                             BRACE_PLACEMENT_VALUES, BRACE_PLACEMENT_OPTIONS),
      new CodeStyleSelectSettingPresentation("METHOD_BRACE_STYLE",
                                             ApplicationBundle.message("wrapping.brace.placement.method.declaration"),
                                             BRACE_PLACEMENT_VALUES, BRACE_PLACEMENT_OPTIONS),
      new CodeStyleSelectSettingPresentation("LAMBDA_BRACE_STYLE", ApplicationBundle.message("wrapping.brace.placement.lambda"),
                                             BRACE_PLACEMENT_VALUES, BRACE_PLACEMENT_OPTIONS),
      new CodeStyleSelectSettingPresentation("BRACE_STYLE", ApplicationBundle.message("wrapping.brace.placement.other"),
                                             BRACE_PLACEMENT_VALUES, BRACE_PLACEMENT_OPTIONS)
    ));

    putGroupTop(result, "EXTENDS_LIST_WRAP", WRAPPING_EXTENDS_LIST, WRAP_VALUES, WRAP_OPTIONS);
    result.put(new SettingsGroup(WRAPPING_EXTENDS_LIST), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("ALIGN_MULTILINE_EXTENDS_LIST", ApplicationBundle.message("wrapping.align.when.multiline"))
    ));

    putGroupTop(result, "EXTENDS_KEYWORD_WRAP", WRAPPING_EXTENDS_KEYWORD, WRAP_VALUES_FOR_SINGLETON, WRAP_OPTIONS_FOR_SINGLETON);

    putGroupTop(result, "THROWS_LIST_WRAP", WRAPPING_THROWS_LIST, WRAP_VALUES, WRAP_OPTIONS);
    result.put(new SettingsGroup(WRAPPING_THROWS_LIST), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("ALIGN_MULTILINE_THROWS_LIST", ApplicationBundle.message("wrapping.align.when.multiline")),
      new CodeStyleSettingPresentation("ALIGN_THROWS_KEYWORD", ApplicationBundle.message("wrapping.align.throws.keyword"))
    ));

    putGroupTop(result, "THROWS_KEYWORD_WRAP", WRAPPING_THROWS_KEYWORD, WRAP_VALUES_FOR_SINGLETON, WRAP_OPTIONS_FOR_SINGLETON);

    putGroupTop(result, "METHOD_PARAMETERS_WRAP", WRAPPING_METHOD_PARAMETERS, WRAP_VALUES, WRAP_OPTIONS);
    result.put(new SettingsGroup(WRAPPING_METHOD_PARAMETERS), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("ALIGN_MULTILINE_PARAMETERS", ApplicationBundle.message("wrapping.align.when.multiline")),
      new CodeStyleSettingPresentation("METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE",
                                       ApplicationBundle.message("wrapping.new.line.after.lpar")),
      new CodeStyleSettingPresentation("METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE",
                                       ApplicationBundle.message("wrapping.rpar.on.new.line"))
    ));

    putGroupTop(result, "CALL_PARAMETERS_WRAP", WRAPPING_METHOD_ARGUMENTS_WRAPPING, WRAP_VALUES, WRAP_OPTIONS);
    result.put(new SettingsGroup(WRAPPING_METHOD_ARGUMENTS_WRAPPING), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("ALIGN_MULTILINE_PARAMETERS_IN_CALLS",
                                       ApplicationBundle.message("wrapping.align.when.multiline")),
      new CodeStyleSettingPresentation("PREFER_PARAMETERS_WRAP",
                                       ApplicationBundle.message("wrapping.take.priority.over.call.chain.wrapping")),
      new CodeStyleSettingPresentation("CALL_PARAMETERS_LPAREN_ON_NEXT_LINE",
                                       ApplicationBundle.message("wrapping.new.line.after.lpar")),
      new CodeStyleSettingPresentation("CALL_PARAMETERS_RPAREN_ON_NEXT_LINE", ApplicationBundle.message("wrapping.rpar.on.new.line"))
    ));

    result.put(new SettingsGroup(WRAPPING_METHOD_PARENTHESES), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("ALIGN_MULTILINE_METHOD_BRACKETS", ApplicationBundle.message("wrapping.align.when.multiline"))
    ));

    putGroupTop(result, "METHOD_CALL_CHAIN_WRAP", WRAPPING_CALL_CHAIN, WRAP_VALUES, WRAP_OPTIONS);
    result.put(new SettingsGroup(WRAPPING_CALL_CHAIN), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("WRAP_FIRST_METHOD_IN_CALL_CHAIN",
                                       ApplicationBundle.message("wrapping.chained.method.call.first.on.new.line")),
      new CodeStyleSettingPresentation("ALIGN_MULTILINE_CHAINED_METHODS", ApplicationBundle.message("wrapping.align.when.multiline"))
    ));

    result.put(new SettingsGroup(WRAPPING_IF_STATEMENT), ContainerUtil.immutableList(
      new CodeStyleSelectSettingPresentation("IF_BRACE_FORCE", ApplicationBundle.message("wrapping.force.braces"), BRACE_VALUES,
                                             BRACE_OPTIONS),
      new CodeStyleSettingPresentation("ELSE_ON_NEW_LINE", ApplicationBundle.message("wrapping.else.on.new.line")),
      new CodeStyleSettingPresentation("SPECIAL_ELSE_IF_TREATMENT",
                                       ApplicationBundle.message("wrapping.special.else.if.braces.treatment"))
    ));

    putGroupTop(result, "FOR_STATEMENT_WRAP", WRAPPING_FOR_STATEMENT, WRAP_VALUES, WRAP_OPTIONS);
    result.put(new SettingsGroup(WRAPPING_FOR_STATEMENT), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("ALIGN_MULTILINE_FOR", ApplicationBundle.message("wrapping.align.when.multiline")),
      new CodeStyleSettingPresentation("FOR_STATEMENT_LPAREN_ON_NEXT_LINE",
                                       ApplicationBundle.message("wrapping.new.line.after.lpar")),
      new CodeStyleSettingPresentation("FOR_STATEMENT_RPAREN_ON_NEXT_LINE", ApplicationBundle.message("wrapping.rpar.on.new.line")),
      new CodeStyleSelectSettingPresentation("FOR_BRACE_FORCE", ApplicationBundle.message("wrapping.force.braces"), BRACE_VALUES,
                                             BRACE_OPTIONS)
    ));

    result.put(new SettingsGroup(WRAPPING_WHILE_STATEMENT), ContainerUtil.immutableList(
      new CodeStyleSelectSettingPresentation("WHILE_BRACE_FORCE", ApplicationBundle.message("wrapping.force.braces"), BRACE_VALUES,
                                             BRACE_OPTIONS)
    ));

    result.put(new SettingsGroup(WRAPPING_DOWHILE_STATEMENT), ContainerUtil.immutableList(
      new CodeStyleSelectSettingPresentation("DOWHILE_BRACE_FORCE", ApplicationBundle.message("wrapping.force.braces"), BRACE_VALUES,
                                             BRACE_OPTIONS),
      new CodeStyleSettingPresentation("WHILE_ON_NEW_LINE", ApplicationBundle.message("wrapping.while.on.new.line"))
    ));

    result.put(new SettingsGroup(WRAPPING_SWITCH_STATEMENT), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("INDENT_CASE_FROM_SWITCH", ApplicationBundle.message("wrapping.indent.case.from.switch")),
      new CodeStyleSettingPresentation("INDENT_BREAK_FROM_CASE", ApplicationBundle.message("wrapping.indent.break.from.case")),
      new CodeStyleSettingPresentation("CASE_STATEMENT_ON_NEW_LINE", ApplicationBundle.message("wrapping.case.statements.on.one.line"))
    ));

    putGroupTop(result, "RESOURCE_LIST_WRAP", WRAPPING_TRY_RESOURCE_LIST, WRAP_VALUES, WRAP_OPTIONS);
    result.put(new SettingsGroup(WRAPPING_TRY_RESOURCE_LIST), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("ALIGN_MULTILINE_RESOURCES", ApplicationBundle.message("wrapping.align.when.multiline")),
      new CodeStyleSettingPresentation("RESOURCE_LIST_LPAREN_ON_NEXT_LINE",
                                       ApplicationBundle.message("wrapping.new.line.after.lpar")),
      new CodeStyleSettingPresentation("RESOURCE_LIST_RPAREN_ON_NEXT_LINE", ApplicationBundle.message("wrapping.rpar.on.new.line"))
    ));

    result.put(new SettingsGroup(WRAPPING_TRY_STATEMENT), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("CATCH_ON_NEW_LINE", ApplicationBundle.message("wrapping.catch.on.new.line")),
      new CodeStyleSettingPresentation("FINALLY_ON_NEW_LINE", ApplicationBundle.message("wrapping.finally.on.new.line"))
    ));

    putGroupTop(result, "BINARY_OPERATION_WRAP", WRAPPING_BINARY_OPERATION, WRAP_VALUES, WRAP_OPTIONS);
    result.put(new SettingsGroup(WRAPPING_BINARY_OPERATION), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("ALIGN_MULTILINE_BINARY_OPERATION",
                                       ApplicationBundle.message("wrapping.align.when.multiline")),
      new CodeStyleSettingPresentation("BINARY_OPERATION_SIGN_ON_NEXT_LINE",
                                       ApplicationBundle.message("wrapping.operation.sign.on.next.line")),
      new CodeStyleSettingPresentation("ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION",
                                       ApplicationBundle.message("wrapping.align.parenthesised.when.multiline")),
      new CodeStyleSettingPresentation("PARENTHESES_EXPRESSION_LPAREN_WRAP",
                                       ApplicationBundle.message("wrapping.new.line.after.lpar")),
      new CodeStyleSettingPresentation("PARENTHESES_EXPRESSION_RPAREN_WRAP", ApplicationBundle.message("wrapping.rpar.on.new.line"))
    ));

    putGroupTop(result, "ASSIGNMENT_WRAP", WRAPPING_ASSIGNMENT, WRAP_VALUES, WRAP_OPTIONS);
    result.put(new SettingsGroup(WRAPPING_ASSIGNMENT), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("ALIGN_MULTILINE_ASSIGNMENT", ApplicationBundle.message("wrapping.align.when.multiline")),
      new CodeStyleSettingPresentation("PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE",
                                       ApplicationBundle.message("wrapping.assignment.sign.on.next.line"))
    ));

    result.put(new SettingsGroup(WRAPPING_FIELDS_VARIABLES_GROUPS), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("ALIGN_GROUP_FIELD_DECLARATIONS",
                                       ApplicationBundle.message("wrapping.align.fields.in.columns")),
      new CodeStyleSettingPresentation("ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS",
                                       ApplicationBundle.message("wrapping.align.variables.in.columns")),
      new CodeStyleSettingPresentation("ALIGN_SUBSEQUENT_SIMPLE_METHODS",
                                       ApplicationBundle.message("wrapping.align.simple.methods.in.columns"))
    ));

    putGroupTop(result, "TERNARY_OPERATION_WRAP", WRAPPING_TERNARY_OPERATION, WRAP_VALUES, WRAP_OPTIONS);
    result.put(new SettingsGroup(WRAPPING_TERNARY_OPERATION), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("ALIGN_MULTILINE_TERNARY_OPERATION",
                                       ApplicationBundle.message("wrapping.align.when.multiline")),
      new CodeStyleSettingPresentation("TERNARY_OPERATION_SIGNS_ON_NEXT_LINE",
                                       ApplicationBundle.message("wrapping.quest.and.colon.signs.on.next.line"))
    ));

    putGroupTop(result, "ARRAY_INITIALIZER_WRAP", WRAPPING_ARRAY_INITIALIZER, WRAP_VALUES, WRAP_OPTIONS);
    result.put(new SettingsGroup(WRAPPING_ARRAY_INITIALIZER), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION",
                                       ApplicationBundle.message("wrapping.align.when.multiline")),
      new CodeStyleSettingPresentation("ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE",
                                       ApplicationBundle.message("wrapping.new.line.after.lbrace")),
      new CodeStyleSettingPresentation("ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE",
                                       ApplicationBundle.message("wrapping.rbrace.on.new.line"))
    ));

    result.put(new SettingsGroup(WRAPPING_MODIFIER_LIST), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("MODIFIER_LIST_WRAP", ApplicationBundle.message("wrapping.after.modifier.list"))
    ));

    putGroupTop(result, "ASSERT_STATEMENT_WRAP", WRAPPING_ASSERT_STATEMENT, WRAP_VALUES, WRAP_OPTIONS);
    result.put(new SettingsGroup(WRAPPING_ASSERT_STATEMENT), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("ASSERT_STATEMENT_COLON_ON_NEXT_LINE",
                                       ApplicationBundle.message("wrapping.colon.signs.on.next.line"))
    ));

    putGroupTop(result, "ENUM_CONSTANTS_WRAP", ApplicationBundle.message("wrapping.enum.constants"), WRAP_VALUES, WRAP_OPTIONS);
    putGroupTop(result, "CLASS_ANNOTATION_WRAP", ApplicationBundle.message("wrapping.classes.annotation"), WRAP_VALUES, WRAP_OPTIONS);
    putGroupTop(result, "METHOD_ANNOTATION_WRAP", ApplicationBundle.message("wrapping.methods.annotation"), WRAP_VALUES, WRAP_OPTIONS);
    putGroupTop(result, "FIELD_ANNOTATION_WRAP", ApplicationBundle.message("wrapping.fields.annotation"), WRAP_VALUES, WRAP_OPTIONS);
    putGroupTop(result, "PARAMETER_ANNOTATION_WRAP", ApplicationBundle.message("wrapping.parameters.annotation"), WRAP_VALUES, WRAP_OPTIONS);
    putGroupTop(result, "VARIABLE_ANNOTATION_WRAP", ApplicationBundle.message("wrapping.local.variables.annotation"), WRAP_VALUES,
                WRAP_OPTIONS);
    WRAPPING_AND_BRACES_STANDARD_SETTINGS = Collections.unmodifiableMap(result);

    //-----------------------------------INDENT_SETTINGS-----------------------------------------------------

    result = ContainerUtil.newLinkedHashMap();
    result.put(new SettingsGroup(null), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("INDENT_SIZE", ApplicationBundle.message("editbox.indent.indent"))
    ));
    result.put(new SettingsGroup(null), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("CONTINUATION_INDENT_SIZE", ApplicationBundle.message("editbox.indent.continuation.indent"))
    ));
    result.put(new SettingsGroup(null), ContainerUtil.immutableList(
      new CodeStyleSettingPresentation("TAB_SIZE", ApplicationBundle.message("editbox.indent.tab.size"))
    ));
    INDENT_STANDARD_SETTINGS = Collections.unmodifiableMap(result);
  }

  /**
   * Returns an immutable map containing all standard settings in a mapping of type (group -> settings contained in the group).
   * Notice that lists containing settings for a specific group are also immutable. Use copies to make modifications.
   * @param settingsType type to get standard settings for
   * @return mapping setting groups to contained setting presentations
   */
  @NotNull
  public static Map<SettingsGroup, List<CodeStyleSettingPresentation>> getStandardSettings(LanguageCodeStyleSettingsProvider.SettingsType settingsType) {
    switch (settingsType) {
      case BLANK_LINES_SETTINGS:
        return BLANK_LINES_STANDARD_SETTINGS;
      case SPACING_SETTINGS:
        return SPACING_STANDARD_SETTINGS;
      case WRAPPING_AND_BRACES_SETTINGS:
        return WRAPPING_AND_BRACES_STANDARD_SETTINGS;
      case INDENT_SETTINGS:
        return INDENT_STANDARD_SETTINGS;
      case LANGUAGE_SPECIFIC:
    }
    return ContainerUtil.newLinkedHashMap();
  }
}
