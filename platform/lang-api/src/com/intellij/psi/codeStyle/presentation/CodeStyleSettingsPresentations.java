// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.presentation;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizableOptions;
import com.intellij.util.LocaleSensitiveApplicationCacheService;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable.*;

final class CodeStyleSettingsPresentations {
  private final @NotNull Map<CodeStyleSettingPresentation.SettingsGroup, List<CodeStyleSettingPresentation>> myBlankLinesStandardSettings;
  private final @NotNull Map<CodeStyleSettingPresentation.SettingsGroup, List<CodeStyleSettingPresentation>> mySpacingStandardSettings;
  private final @NotNull Map<CodeStyleSettingPresentation.SettingsGroup, List<CodeStyleSettingPresentation>>
    myWrappingAndBracesStandardSettings;
  private final @NotNull Map<CodeStyleSettingPresentation.SettingsGroup, List<CodeStyleSettingPresentation>> myIndentStandardSettings;

  private CodeStyleSettingsPresentations() {

    //-----------------------------------BLANK_LINES_SETTINGS-----------------------------------------------------

    Map<CodeStyleSettingPresentation.SettingsGroup, List<CodeStyleSettingPresentation>> result = new LinkedHashMap<>();
    var customizableOptions = CodeStyleSettingsCustomizableOptions.getInstance();
    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.BLANK_LINES_KEEP), List.of(
      new CodeStyleSettingPresentation("KEEP_BLANK_LINES_IN_DECLARATIONS",
                                       ApplicationBundle.message("editbox.keep.blanklines.in.declarations")),
      new CodeStyleSettingPresentation("KEEP_BLANK_LINES_IN_CODE", ApplicationBundle.message("editbox.keep.blanklines.in.code")),
      new CodeStyleSettingPresentation("KEEP_BLANK_LINES_BEFORE_RBRACE",
                                       ApplicationBundle.message("editbox.keep.blanklines.before.rbrace")),
      new CodeStyleSettingPresentation("KEEP_BLANK_LINES_BETWEEN_PACKAGE_DECLARATION_AND_HEADER",
                                       ApplicationBundle.message("editbox.keep.blanklines.between.header.and.package"))
    ));

    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.BLANK_LINES), List.of(
      new CodeStyleSettingPresentation("BLANK_LINES_BEFORE_PACKAGE",
                                       ApplicationBundle.message("editbox.blanklines.before.package.statement")),
      new CodeStyleSettingPresentation("BLANK_LINES_AFTER_PACKAGE",
                                       ApplicationBundle.message("editbox.blanklines.after.package.statement")),
      new CodeStyleSettingPresentation("BLANK_LINES_BEFORE_IMPORTS", ApplicationBundle.message("editbox.blanklines.before.imports")),
      new CodeStyleSettingPresentation("BLANK_LINES_AFTER_IMPORTS", ApplicationBundle.message("editbox.blanklines.after.imports")),
      new CodeStyleSettingPresentation("BLANK_LINES_AROUND_CLASS", ApplicationBundle.message("editbox.blanklines.around.class")),
      new CodeStyleSettingPresentation("BLANK_LINES_AFTER_CLASS_HEADER",
                                       ApplicationBundle.message("editbox.blanklines.after.class.header")),
      new CodeStyleSettingPresentation("BLANK_LINES_BEFORE_CLASS_END", ApplicationBundle.message("editbox.blanklines.before.class.end")),
      new CodeStyleSettingPresentation("BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER",
                                       ApplicationBundle.message("editbox.blanklines.after.anonymous.class.header")),
      new CodeStyleSettingPresentation("BLANK_LINES_AROUND_FIELD_IN_INTERFACE",
                                       ApplicationBundle.message("editbox.blanklines.around.field.in.interface")),
      //TODO why is this not loaded from bundle??
      new CodeStyleSettingPresentation("BLANK_LINES_AROUND_FIELD", ApplicationBundle.message("editbox.blanklines.around.field")),
      new CodeStyleSettingPresentation("BLANK_LINES_AROUND_METHOD_IN_INTERFACE",
                                       ApplicationBundle.message("editbox.blanklines.around.method.in.interface")),
      //TODO why is this not loaded from bundle??
      new CodeStyleSettingPresentation("BLANK_LINES_AROUND_METHOD", ApplicationBundle.message("editbox.blanklines.around.method")),
      new CodeStyleSettingPresentation("BLANK_LINES_BEFORE_METHOD_BODY",
                                       ApplicationBundle.message("editbox.blanklines.before.method.body"))
    ));
    myBlankLinesStandardSettings = Collections.unmodifiableMap(result);

    //-----------------------------------SPACING_SETTINGS-----------------------------------------------------

    result = new LinkedHashMap<>();
    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.SPACES_BEFORE_PARENTHESES), List.of(
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

    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.SPACES_AROUND_OPERATORS), List.of(
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

    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.SPACES_BEFORE_LEFT_BRACE), List.of(
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

    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.SPACES_BEFORE_KEYWORD), List.of(
      new CodeStyleSettingPresentation("SPACE_BEFORE_ELSE_KEYWORD", ApplicationBundle.message("checkbox.spaces.else.keyword")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_WHILE_KEYWORD", ApplicationBundle.message("checkbox.spaces.while.keyword")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_CATCH_KEYWORD", ApplicationBundle.message("checkbox.spaces.catch.keyword")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_FINALLY_KEYWORD", ApplicationBundle.message("checkbox.spaces.finally.keyword"))
    ));

    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.SPACES_WITHIN), List.of(
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

    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.SPACES_IN_TERNARY_OPERATOR), List.of(
      new CodeStyleSettingPresentation("SPACE_BEFORE_QUEST", ApplicationBundle.message("checkbox.spaces.before.question")),
      new CodeStyleSettingPresentation("SPACE_AFTER_QUEST", ApplicationBundle.message("checkbox.spaces.after.question")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_COLON", ApplicationBundle.message("checkbox.spaces.before.colon")),
      new CodeStyleSettingPresentation("SPACE_AFTER_COLON", ApplicationBundle.message("checkbox.spaces.after.colon"))
    ));

    result
      .put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.SPACES_WITHIN_TYPE_ARGUMENTS), List.of(
        new CodeStyleSettingPresentation("SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS",
                                         ApplicationBundle.message("checkbox.spaces.after.comma"))
      ));

    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.SPACES_IN_TYPE_ARGUMENTS), List.of(
      new CodeStyleSettingPresentation("SPACE_BEFORE_TYPE_PARAMETER_LIST",
                                       ApplicationBundle.message("checkbox.spaces.before.opening.angle.bracket"))
    ));

    result
      .put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.SPACES_IN_TYPE_PARAMETERS), Collections.emptyList());

    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.SPACES_OTHER), List.of(
      new CodeStyleSettingPresentation("SPACE_BEFORE_COMMA", ApplicationBundle.message("checkbox.spaces.before.comma")),
      new CodeStyleSettingPresentation("SPACE_AFTER_COMMA", ApplicationBundle.message("checkbox.spaces.after.comma")),
      new CodeStyleSettingPresentation("SPACE_BEFORE_SEMICOLON", ApplicationBundle.message("checkbox.spaces.before.semicolon")),
      new CodeStyleSettingPresentation("SPACE_AFTER_SEMICOLON", ApplicationBundle.message("checkbox.spaces.after.semicolon")),
      new CodeStyleSettingPresentation("SPACE_AFTER_TYPE_CAST", ApplicationBundle.message("checkbox.spaces.after.type.cast"))
    ));
    mySpacingStandardSettings = Collections.unmodifiableMap(result);

    //-----------------------------------WRAPPING_AND_BRACES_SETTINGS-----------------------------------------------------

    result = new LinkedHashMap<>();
    result.put(new CodeStyleSettingPresentation.SettingsGroup(null), List.of(
      new CodeStyleBoundedIntegerSettingPresentation("RIGHT_MARGIN", ApplicationBundle.message("editbox.right.margin.columns"), 0, 999,
                                                     -1,
                                                     ApplicationBundle.message("settings.code.style.default.general")),
      new CodeStyleSelectSettingPresentation("WRAP_ON_TYPING", ApplicationBundle.message("wrapping.wrap.on.typing"), WRAP_ON_TYPING_VALUES,
                                             customizableOptions.WRAP_ON_TYPING_OPTIONS),
      new CodeStyleSoftMarginsPresentation()
    ));

    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.WRAPPING_KEEP), List.of(
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

    result.put(new CodeStyleSettingPresentation.SettingsGroup(null), List.of(
      new CodeStyleSettingPresentation("WRAP_LONG_LINES", ApplicationBundle.message("wrapping.long.lines"))
    ));

    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.WRAPPING_COMMENTS), List.of(
      new CodeStyleSettingPresentation("WRAP_COMMENTS", ApplicationBundle.message("wrapping.comments.wrap.at.right.margin"))
    ));

    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.WRAPPING_BRACES), List.of(
      new CodeStyleSelectSettingPresentation("CLASS_BRACE_STYLE",
                                             ApplicationBundle.message("wrapping.brace.placement.class.declaration"),
                                             BRACE_PLACEMENT_VALUES, customizableOptions.BRACE_PLACEMENT_OPTIONS),
      new CodeStyleSelectSettingPresentation("METHOD_BRACE_STYLE",
                                             ApplicationBundle.message("wrapping.brace.placement.method.declaration"),
                                             BRACE_PLACEMENT_VALUES, customizableOptions.BRACE_PLACEMENT_OPTIONS),
      new CodeStyleSelectSettingPresentation("LAMBDA_BRACE_STYLE", ApplicationBundle.message("wrapping.brace.placement.lambda"),
                                             BRACE_PLACEMENT_VALUES, customizableOptions.BRACE_PLACEMENT_OPTIONS),
      new CodeStyleSelectSettingPresentation("BRACE_STYLE", ApplicationBundle.message("wrapping.brace.placement.other"),
                                             BRACE_PLACEMENT_VALUES, customizableOptions.BRACE_PLACEMENT_OPTIONS)
    ));

    putGroupTop(result, "EXTENDS_LIST_WRAP", customizableOptions.WRAPPING_EXTENDS_LIST, WRAP_VALUES, customizableOptions.WRAP_OPTIONS);
    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.WRAPPING_EXTENDS_LIST), List.of(
      new CodeStyleSettingPresentation("ALIGN_MULTILINE_EXTENDS_LIST", ApplicationBundle.message("wrapping.align.when.multiline"))
    ));

    putGroupTop(result, "EXTENDS_KEYWORD_WRAP", customizableOptions.WRAPPING_EXTENDS_KEYWORD, WRAP_VALUES_FOR_SINGLETON,
                customizableOptions.WRAP_OPTIONS_FOR_SINGLETON);

    putGroupTop(result, "THROWS_LIST_WRAP", customizableOptions.WRAPPING_THROWS_LIST, WRAP_VALUES, customizableOptions.WRAP_OPTIONS);
    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.WRAPPING_THROWS_LIST), List.of(
      new CodeStyleSettingPresentation("ALIGN_MULTILINE_THROWS_LIST", ApplicationBundle.message("wrapping.align.when.multiline")),
      new CodeStyleSettingPresentation("ALIGN_THROWS_KEYWORD", ApplicationBundle.message("wrapping.align.throws.keyword"))
    ));

    putGroupTop(result, "THROWS_KEYWORD_WRAP", customizableOptions.WRAPPING_THROWS_KEYWORD, WRAP_VALUES_FOR_SINGLETON,
                customizableOptions.WRAP_OPTIONS_FOR_SINGLETON);

    putGroupTop(result, "METHOD_PARAMETERS_WRAP", customizableOptions.WRAPPING_METHOD_PARAMETERS, WRAP_VALUES,
                customizableOptions.WRAP_OPTIONS);
    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.WRAPPING_METHOD_PARAMETERS), List.of(
      new CodeStyleSettingPresentation("ALIGN_MULTILINE_PARAMETERS", ApplicationBundle.message("wrapping.align.when.multiline")),
      new CodeStyleSettingPresentation("METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE",
                                       ApplicationBundle.message("wrapping.new.line.after.lpar")),
      new CodeStyleSettingPresentation("METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE",
                                       ApplicationBundle.message("wrapping.rpar.on.new.line"))
    ));

    putGroupTop(result, "CALL_PARAMETERS_WRAP", customizableOptions.WRAPPING_METHOD_ARGUMENTS_WRAPPING, WRAP_VALUES,
                customizableOptions.WRAP_OPTIONS);
    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.WRAPPING_METHOD_ARGUMENTS_WRAPPING),
               List.of(
                 new CodeStyleSettingPresentation("ALIGN_MULTILINE_PARAMETERS_IN_CALLS",
                                                  ApplicationBundle.message("wrapping.align.when.multiline")),
                 new CodeStyleSettingPresentation("PREFER_PARAMETERS_WRAP",
                                                  ApplicationBundle.message("wrapping.take.priority.over.call.chain.wrapping")),
                 new CodeStyleSettingPresentation("CALL_PARAMETERS_LPAREN_ON_NEXT_LINE",
                                                  ApplicationBundle.message("wrapping.new.line.after.lpar")),
                 new CodeStyleSettingPresentation("CALL_PARAMETERS_RPAREN_ON_NEXT_LINE",
                                                  ApplicationBundle.message("wrapping.rpar.on.new.line"))
               ));

    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.WRAPPING_METHOD_PARENTHESES), List.of(
      new CodeStyleSettingPresentation("ALIGN_MULTILINE_METHOD_BRACKETS", ApplicationBundle.message("wrapping.align.when.multiline"))
    ));

    putGroupTop(result, "METHOD_CALL_CHAIN_WRAP", customizableOptions.WRAPPING_CALL_CHAIN, WRAP_VALUES, customizableOptions.WRAP_OPTIONS);
    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.WRAPPING_CALL_CHAIN), List.of(
      new CodeStyleSettingPresentation("WRAP_FIRST_METHOD_IN_CALL_CHAIN",
                                       ApplicationBundle.message("wrapping.chained.method.call.first.on.new.line")),
      new CodeStyleSettingPresentation("ALIGN_MULTILINE_CHAINED_METHODS", ApplicationBundle.message("wrapping.align.when.multiline")),
      new CodeStyleCommaSeparatedIdentifiersPresentation("BUILDER_METHODS", ApplicationBundle.message("wrapping.builder.methods")),
      new CodeStyleSettingPresentation("KEEP_BUILDER_METHODS_INDENTS", ApplicationBundle.message("wrapping.builder.methods.keep.indents"))
    ));

    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.WRAPPING_IF_STATEMENT), List.of(
      new CodeStyleSelectSettingPresentation("IF_BRACE_FORCE", ApplicationBundle.message("wrapping.force.braces"), BRACE_VALUES,
                                             customizableOptions.BRACE_OPTIONS),
      new CodeStyleSettingPresentation("ELSE_ON_NEW_LINE", ApplicationBundle.message("wrapping.else.on.new.line")),
      new CodeStyleSettingPresentation("SPECIAL_ELSE_IF_TREATMENT",
                                       ApplicationBundle.message("wrapping.special.else.if.braces.treatment"))
    ));

    putGroupTop(result, "FOR_STATEMENT_WRAP", customizableOptions.WRAPPING_FOR_STATEMENT, WRAP_VALUES, customizableOptions.WRAP_OPTIONS);
    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.WRAPPING_FOR_STATEMENT), List.of(
      new CodeStyleSettingPresentation("ALIGN_MULTILINE_FOR", ApplicationBundle.message("wrapping.align.when.multiline")),
      new CodeStyleSettingPresentation("FOR_STATEMENT_LPAREN_ON_NEXT_LINE",
                                       ApplicationBundle.message("wrapping.new.line.after.lpar")),
      new CodeStyleSettingPresentation("FOR_STATEMENT_RPAREN_ON_NEXT_LINE", ApplicationBundle.message("wrapping.rpar.on.new.line")),
      new CodeStyleSelectSettingPresentation("FOR_BRACE_FORCE", ApplicationBundle.message("wrapping.force.braces"), BRACE_VALUES,
                                             customizableOptions.BRACE_OPTIONS)
    ));

    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.WRAPPING_WHILE_STATEMENT), List.of(
      new CodeStyleSelectSettingPresentation("WHILE_BRACE_FORCE", ApplicationBundle.message("wrapping.force.braces"), BRACE_VALUES,
                                             customizableOptions.BRACE_OPTIONS)
    ));

    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.WRAPPING_DOWHILE_STATEMENT), List.of(
      new CodeStyleSelectSettingPresentation("DOWHILE_BRACE_FORCE", ApplicationBundle.message("wrapping.force.braces"), BRACE_VALUES,
                                             customizableOptions.BRACE_OPTIONS),
      new CodeStyleSettingPresentation("WHILE_ON_NEW_LINE", ApplicationBundle.message("wrapping.while.on.new.line"))
    ));

    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.WRAPPING_SWITCH_STATEMENT), List.of(
      new CodeStyleSettingPresentation("INDENT_CASE_FROM_SWITCH", ApplicationBundle.message("wrapping.indent.case.from.switch")),
      new CodeStyleSettingPresentation("INDENT_BREAK_FROM_CASE", ApplicationBundle.message("wrapping.indent.break.from.case")),
      new CodeStyleSettingPresentation("CASE_STATEMENT_ON_NEW_LINE", ApplicationBundle.message("wrapping.case.statements.on.one.line"))
    ));

    putGroupTop(result, "RESOURCE_LIST_WRAP", customizableOptions.WRAPPING_TRY_RESOURCE_LIST, WRAP_VALUES,
                customizableOptions.WRAP_OPTIONS);
    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.WRAPPING_TRY_RESOURCE_LIST), List.of(
      new CodeStyleSettingPresentation("ALIGN_MULTILINE_RESOURCES", ApplicationBundle.message("wrapping.align.when.multiline")),
      new CodeStyleSettingPresentation("RESOURCE_LIST_LPAREN_ON_NEXT_LINE",
                                       ApplicationBundle.message("wrapping.new.line.after.lpar")),
      new CodeStyleSettingPresentation("RESOURCE_LIST_RPAREN_ON_NEXT_LINE", ApplicationBundle.message("wrapping.rpar.on.new.line"))
    ));

    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.WRAPPING_TRY_STATEMENT), List.of(
      new CodeStyleSettingPresentation("CATCH_ON_NEW_LINE", ApplicationBundle.message("wrapping.catch.on.new.line")),
      new CodeStyleSettingPresentation("FINALLY_ON_NEW_LINE", ApplicationBundle.message("wrapping.finally.on.new.line"))
    ));

    putGroupTop(result, "BINARY_OPERATION_WRAP", customizableOptions.WRAPPING_BINARY_OPERATION, WRAP_VALUES,
                customizableOptions.WRAP_OPTIONS);
    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.WRAPPING_BINARY_OPERATION), List.of(
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

    putGroupTop(result, "ASSIGNMENT_WRAP", customizableOptions.WRAPPING_ASSIGNMENT, WRAP_VALUES, customizableOptions.WRAP_OPTIONS);
    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.WRAPPING_ASSIGNMENT), List.of(
      new CodeStyleSettingPresentation("ALIGN_MULTILINE_ASSIGNMENT", ApplicationBundle.message("wrapping.align.when.multiline")),
      new CodeStyleSettingPresentation("PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE",
                                       ApplicationBundle.message("wrapping.assignment.sign.on.next.line"))
    ));

    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.WRAPPING_FIELDS_VARIABLES_GROUPS),
               List.of(
                 new CodeStyleSettingPresentation("ALIGN_GROUP_FIELD_DECLARATIONS",
                                                  ApplicationBundle.message("wrapping.align.fields.in.columns")),
                 new CodeStyleSettingPresentation("ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS",
                                                  ApplicationBundle.message("wrapping.align.variables.in.columns")),
                 new CodeStyleSettingPresentation("ALIGN_CONSECUTIVE_ASSIGNMENTS",
                                                  ApplicationBundle.message("wrapping.align.assignments.in.columns")),
                 new CodeStyleSettingPresentation("ALIGN_SUBSEQUENT_SIMPLE_METHODS",
                                                  ApplicationBundle.message("wrapping.align.simple.methods.in.columns"))
               ));

    putGroupTop(result, "TERNARY_OPERATION_WRAP", customizableOptions.WRAPPING_TERNARY_OPERATION, WRAP_VALUES,
                customizableOptions.WRAP_OPTIONS);
    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.WRAPPING_TERNARY_OPERATION), List.of(
      new CodeStyleSettingPresentation("ALIGN_MULTILINE_TERNARY_OPERATION",
                                       ApplicationBundle.message("wrapping.align.when.multiline")),
      new CodeStyleSettingPresentation("TERNARY_OPERATION_SIGNS_ON_NEXT_LINE",
                                       ApplicationBundle.message("wrapping.quest.and.colon.signs.on.next.line"))
    ));

    putGroupTop(result, "ARRAY_INITIALIZER_WRAP", customizableOptions.WRAPPING_ARRAY_INITIALIZER, WRAP_VALUES,
                customizableOptions.WRAP_OPTIONS);
    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.WRAPPING_ARRAY_INITIALIZER), List.of(
      new CodeStyleSettingPresentation("ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION",
                                       ApplicationBundle.message("wrapping.align.when.multiline")),
      new CodeStyleSettingPresentation("ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE",
                                       ApplicationBundle.message("wrapping.new.line.after.lbrace")),
      new CodeStyleSettingPresentation("ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE",
                                       ApplicationBundle.message("wrapping.rbrace.on.new.line"))
    ));

    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.WRAPPING_MODIFIER_LIST), List.of(
      new CodeStyleSettingPresentation("MODIFIER_LIST_WRAP", ApplicationBundle.message("wrapping.after.modifier.list"))
    ));

    putGroupTop(result, "ASSERT_STATEMENT_WRAP", customizableOptions.WRAPPING_ASSERT_STATEMENT, WRAP_VALUES,
                customizableOptions.WRAP_OPTIONS);
    result.put(new CodeStyleSettingPresentation.SettingsGroup(customizableOptions.WRAPPING_ASSERT_STATEMENT), List.of(
      new CodeStyleSettingPresentation("ASSERT_STATEMENT_COLON_ON_NEXT_LINE",
                                       ApplicationBundle.message("wrapping.colon.signs.on.next.line"))
    ));

    putGroupTop(result, "ENUM_CONSTANTS_WRAP", ApplicationBundle.message("wrapping.enum.constants"), WRAP_VALUES,
                customizableOptions.WRAP_OPTIONS);
    putGroupTop(result, "CLASS_ANNOTATION_WRAP", ApplicationBundle.message("wrapping.classes.annotation"), WRAP_VALUES,
                customizableOptions.WRAP_OPTIONS);
    putGroupTop(result, "METHOD_ANNOTATION_WRAP", ApplicationBundle.message("wrapping.methods.annotation"), WRAP_VALUES,
                customizableOptions.WRAP_OPTIONS);
    putGroupTop(result, "FIELD_ANNOTATION_WRAP", ApplicationBundle.message("wrapping.fields.annotation"), WRAP_VALUES,
                customizableOptions.WRAP_OPTIONS);
    putGroupTop(result, "PARAMETER_ANNOTATION_WRAP", ApplicationBundle.message("wrapping.parameters.annotation"), WRAP_VALUES,
                customizableOptions.WRAP_OPTIONS);
    putGroupTop(result, "VARIABLE_ANNOTATION_WRAP", ApplicationBundle.message("wrapping.local.variables.annotation"), WRAP_VALUES,
                customizableOptions.WRAP_OPTIONS);
    myWrappingAndBracesStandardSettings = Collections.unmodifiableMap(result);

    //-----------------------------------INDENT_SETTINGS-----------------------------------------------------

    result = new LinkedHashMap<>();
    result.put(new CodeStyleSettingPresentation.SettingsGroup(null), List.of(
      new CodeStyleSettingPresentation("INDENT_SIZE", ApplicationBundle.message("editbox.indent.indent"))
    ));
    result.put(new CodeStyleSettingPresentation.SettingsGroup(null), List.of(
      new CodeStyleSettingPresentation("CONTINUATION_INDENT_SIZE", ApplicationBundle.message("editbox.indent.continuation.indent"))
    ));
    result.put(new CodeStyleSettingPresentation.SettingsGroup(null), List.of(
      new CodeStyleSettingPresentation("TAB_SIZE", ApplicationBundle.message("editbox.indent.tab.size"))
    ));
    myIndentStandardSettings = Collections.unmodifiableMap(result);
  }

  private static void putGroupTop(@NotNull Map<? super CodeStyleSettingPresentation.SettingsGroup, ? super List<CodeStyleSettingPresentation>> result,
                                  @NotNull String fieldName,
                                  @NlsContexts.Label @NotNull String uiName, int[] values, String[] valueUiNames) {
    result.put(new CodeStyleSettingPresentation.SettingsGroup(null), List.of(
      new CodeStyleSelectSettingPresentation(fieldName, uiName, values, valueUiNames)
    ));
  }

  @NotNull Map<CodeStyleSettingPresentation.SettingsGroup, List<CodeStyleSettingPresentation>> getBlankLinesStandardSettings() {
    return myBlankLinesStandardSettings;
  }

  @NotNull Map<CodeStyleSettingPresentation.SettingsGroup, List<CodeStyleSettingPresentation>> getSpacingStandardSettings() {
    return mySpacingStandardSettings;
  }

  @NotNull Map<CodeStyleSettingPresentation.SettingsGroup, List<CodeStyleSettingPresentation>> getWrappingAndBracesStandardSettings() {
    return myWrappingAndBracesStandardSettings;
  }

  @NotNull Map<CodeStyleSettingPresentation.SettingsGroup, List<CodeStyleSettingPresentation>> getIndentStandardSettings() {
    return myIndentStandardSettings;
  }

  static @NotNull CodeStyleSettingsPresentations getInstance() {
    return LocaleSensitiveApplicationCacheService.getInstance()
      .getData(CodeStyleSettingsPresentations.class, CodeStyleSettingsPresentations::new);
  }
}
