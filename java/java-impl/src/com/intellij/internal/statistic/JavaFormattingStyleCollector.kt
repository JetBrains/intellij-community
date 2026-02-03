// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic

import com.intellij.application.options.CodeStyle
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.addMetricIfDiffers
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.StringEventField
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.JavaCodeStyleSettings

/**
 * PLEASE DON'T EDIT MANUALLY,
 * USE com.intellij.psi.codeStyle.GenerateJavaFormattingStyleCollector
 */
public class JavaFormattingStyleCollector : ProjectUsagesCollector() {
  private val GROUP = EventLogGroup("java.code.style", 3)

  override fun getGroup(): EventLogGroup {
    return GROUP
  }

  private val NAME_FIELD = EventFields.String("name", ALLOWED_NAMES)

  private val VALUE_FIELD = object : StringEventField("value") {
    override val validationRule: List<String>
      get() = listOf("{regexp#integer}", "{enum#boolean}")
  }

  private val NOT_DEFAULT_EVENT: VarargEventId = GROUP.registerVarargEvent("not.default", NAME_FIELD, VALUE_FIELD)

  private inline fun <T, V> addMetricIfDiffersCustom(set: MutableSet<in MetricEvent>, settingsBean: T, defaultSettingsBean: T,
                                                     crossinline valueFunction: (T) -> V, key: String) {
    addMetricIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction) { value ->
      NOT_DEFAULT_EVENT.metric(NAME_FIELD.with(key), VALUE_FIELD.with(value.toString()))
    }
  }

  override fun getMetrics(project: Project): Set<MetricEvent> {
    val result = mutableSetOf<MetricEvent>()
    val commonSettings = CodeStyle.getSettings(project).getCommonSettings(JavaLanguage.INSTANCE)
    val defaultCommonSettings = CommonCodeStyleSettings(JavaLanguage.INSTANCE)

    val javaSettings = CodeStyle.getSettings(project).getCustomSettings(JavaCodeStyleSettings::class.java)
    val defaultJavaSettings = JavaCodeStyleSettings(javaSettings.container)

    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.RIGHT_MARGIN }, "COMMON_RIGHT_MARGIN")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.LINE_COMMENT_AT_FIRST_COLUMN }, "COMMON_LINE_COMMENT_AT_FIRST_COLUMN")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.BLOCK_COMMENT_AT_FIRST_COLUMN }, "COMMON_BLOCK_COMMENT_AT_FIRST_COLUMN")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.LINE_COMMENT_ADD_SPACE }, "COMMON_LINE_COMMENT_ADD_SPACE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.BLOCK_COMMENT_ADD_SPACE }, "COMMON_BLOCK_COMMENT_ADD_SPACE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.LINE_COMMENT_ADD_SPACE_ON_REFORMAT }, "COMMON_LINE_COMMENT_ADD_SPACE_ON_REFORMAT")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.LINE_COMMENT_ADD_SPACE_IN_SUPPRESSION }, "COMMON_LINE_COMMENT_ADD_SPACE_IN_SUPPRESSION")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.KEEP_LINE_BREAKS }, "COMMON_KEEP_LINE_BREAKS")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.KEEP_FIRST_COLUMN_COMMENT }, "COMMON_KEEP_FIRST_COLUMN_COMMENT")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.KEEP_CONTROL_STATEMENT_IN_ONE_LINE }, "COMMON_KEEP_CONTROL_STATEMENT_IN_ONE_LINE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.KEEP_BLANK_LINES_IN_DECLARATIONS }, "COMMON_KEEP_BLANK_LINES_IN_DECLARATIONS")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.KEEP_BLANK_LINES_IN_CODE }, "COMMON_KEEP_BLANK_LINES_IN_CODE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.KEEP_BLANK_LINES_BETWEEN_PACKAGE_DECLARATION_AND_HEADER }, "COMMON_KEEP_BLANK_LINES_BETWEEN_PACKAGE_DECLARATION_AND_HEADER")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.KEEP_BLANK_LINES_BEFORE_RBRACE }, "COMMON_KEEP_BLANK_LINES_BEFORE_RBRACE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.BLANK_LINES_BEFORE_PACKAGE }, "COMMON_BLANK_LINES_BEFORE_PACKAGE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.BLANK_LINES_AFTER_PACKAGE }, "COMMON_BLANK_LINES_AFTER_PACKAGE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.BLANK_LINES_BEFORE_IMPORTS }, "COMMON_BLANK_LINES_BEFORE_IMPORTS")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.BLANK_LINES_AFTER_IMPORTS }, "COMMON_BLANK_LINES_AFTER_IMPORTS")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.BLANK_LINES_AROUND_CLASS }, "COMMON_BLANK_LINES_AROUND_CLASS")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.BLANK_LINES_AROUND_FIELD }, "COMMON_BLANK_LINES_AROUND_FIELD")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.BLANK_LINES_AROUND_METHOD }, "COMMON_BLANK_LINES_AROUND_METHOD")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.BLANK_LINES_BEFORE_METHOD_BODY }, "COMMON_BLANK_LINES_BEFORE_METHOD_BODY")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.BLANK_LINES_AROUND_FIELD_IN_INTERFACE }, "COMMON_BLANK_LINES_AROUND_FIELD_IN_INTERFACE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.BLANK_LINES_AROUND_METHOD_IN_INTERFACE }, "COMMON_BLANK_LINES_AROUND_METHOD_IN_INTERFACE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.BLANK_LINES_AFTER_CLASS_HEADER }, "COMMON_BLANK_LINES_AFTER_CLASS_HEADER")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER }, "COMMON_BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.BLANK_LINES_BEFORE_CLASS_END }, "COMMON_BLANK_LINES_BEFORE_CLASS_END")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.BRACE_STYLE }, "COMMON_BRACE_STYLE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.CLASS_BRACE_STYLE }, "COMMON_CLASS_BRACE_STYLE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.METHOD_BRACE_STYLE }, "COMMON_METHOD_BRACE_STYLE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.LAMBDA_BRACE_STYLE }, "COMMON_LAMBDA_BRACE_STYLE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS }, "COMMON_DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.ELSE_ON_NEW_LINE }, "COMMON_ELSE_ON_NEW_LINE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.WHILE_ON_NEW_LINE }, "COMMON_WHILE_ON_NEW_LINE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.CATCH_ON_NEW_LINE }, "COMMON_CATCH_ON_NEW_LINE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.FINALLY_ON_NEW_LINE }, "COMMON_FINALLY_ON_NEW_LINE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.INDENT_CASE_FROM_SWITCH }, "COMMON_INDENT_CASE_FROM_SWITCH")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.CASE_STATEMENT_ON_NEW_LINE }, "COMMON_CASE_STATEMENT_ON_NEW_LINE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.INDENT_BREAK_FROM_CASE }, "COMMON_INDENT_BREAK_FROM_CASE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPECIAL_ELSE_IF_TREATMENT }, "COMMON_SPECIAL_ELSE_IF_TREATMENT")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.ALIGN_MULTILINE_CHAINED_METHODS }, "COMMON_ALIGN_MULTILINE_CHAINED_METHODS")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.ALIGN_MULTILINE_PARAMETERS }, "COMMON_ALIGN_MULTILINE_PARAMETERS")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.ALIGN_MULTILINE_PARAMETERS_IN_CALLS }, "COMMON_ALIGN_MULTILINE_PARAMETERS_IN_CALLS")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.ALIGN_MULTILINE_RESOURCES }, "COMMON_ALIGN_MULTILINE_RESOURCES")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.ALIGN_MULTILINE_FOR }, "COMMON_ALIGN_MULTILINE_FOR")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.ALIGN_MULTILINE_BINARY_OPERATION }, "COMMON_ALIGN_MULTILINE_BINARY_OPERATION")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.ALIGN_MULTILINE_ASSIGNMENT }, "COMMON_ALIGN_MULTILINE_ASSIGNMENT")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.ALIGN_MULTILINE_TERNARY_OPERATION }, "COMMON_ALIGN_MULTILINE_TERNARY_OPERATION")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.ALIGN_MULTILINE_THROWS_LIST }, "COMMON_ALIGN_MULTILINE_THROWS_LIST")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.ALIGN_THROWS_KEYWORD }, "COMMON_ALIGN_THROWS_KEYWORD")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.ALIGN_MULTILINE_EXTENDS_LIST }, "COMMON_ALIGN_MULTILINE_EXTENDS_LIST")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.ALIGN_MULTILINE_METHOD_BRACKETS }, "COMMON_ALIGN_MULTILINE_METHOD_BRACKETS")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION }, "COMMON_ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION }, "COMMON_ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.ALIGN_GROUP_FIELD_DECLARATIONS }, "COMMON_ALIGN_GROUP_FIELD_DECLARATIONS")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS }, "COMMON_ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.ALIGN_CONSECUTIVE_ASSIGNMENTS }, "COMMON_ALIGN_CONSECUTIVE_ASSIGNMENTS")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.ALIGN_SUBSEQUENT_SIMPLE_METHODS }, "COMMON_ALIGN_SUBSEQUENT_SIMPLE_METHODS")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_AROUND_ASSIGNMENT_OPERATORS }, "COMMON_SPACE_AROUND_ASSIGNMENT_OPERATORS")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_AROUND_LOGICAL_OPERATORS }, "COMMON_SPACE_AROUND_LOGICAL_OPERATORS")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_AROUND_EQUALITY_OPERATORS }, "COMMON_SPACE_AROUND_EQUALITY_OPERATORS")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_AROUND_RELATIONAL_OPERATORS }, "COMMON_SPACE_AROUND_RELATIONAL_OPERATORS")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_AROUND_BITWISE_OPERATORS }, "COMMON_SPACE_AROUND_BITWISE_OPERATORS")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_AROUND_ADDITIVE_OPERATORS }, "COMMON_SPACE_AROUND_ADDITIVE_OPERATORS")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_AROUND_MULTIPLICATIVE_OPERATORS }, "COMMON_SPACE_AROUND_MULTIPLICATIVE_OPERATORS")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_AROUND_SHIFT_OPERATORS }, "COMMON_SPACE_AROUND_SHIFT_OPERATORS")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_AROUND_UNARY_OPERATOR }, "COMMON_SPACE_AROUND_UNARY_OPERATOR")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_AROUND_LAMBDA_ARROW }, "COMMON_SPACE_AROUND_LAMBDA_ARROW")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_AROUND_METHOD_REF_DBL_COLON }, "COMMON_SPACE_AROUND_METHOD_REF_DBL_COLON")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_AFTER_COMMA }, "COMMON_SPACE_AFTER_COMMA")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS }, "COMMON_SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_COMMA }, "COMMON_SPACE_BEFORE_COMMA")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_AFTER_SEMICOLON }, "COMMON_SPACE_AFTER_SEMICOLON")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_SEMICOLON }, "COMMON_SPACE_BEFORE_SEMICOLON")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_WITHIN_PARENTHESES }, "COMMON_SPACE_WITHIN_PARENTHESES")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_WITHIN_METHOD_CALL_PARENTHESES }, "COMMON_SPACE_WITHIN_METHOD_CALL_PARENTHESES")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES }, "COMMON_SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_WITHIN_METHOD_PARENTHESES }, "COMMON_SPACE_WITHIN_METHOD_PARENTHESES")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_WITHIN_EMPTY_METHOD_PARENTHESES }, "COMMON_SPACE_WITHIN_EMPTY_METHOD_PARENTHESES")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_WITHIN_IF_PARENTHESES }, "COMMON_SPACE_WITHIN_IF_PARENTHESES")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_WITHIN_WHILE_PARENTHESES }, "COMMON_SPACE_WITHIN_WHILE_PARENTHESES")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_WITHIN_FOR_PARENTHESES }, "COMMON_SPACE_WITHIN_FOR_PARENTHESES")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_WITHIN_TRY_PARENTHESES }, "COMMON_SPACE_WITHIN_TRY_PARENTHESES")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_WITHIN_CATCH_PARENTHESES }, "COMMON_SPACE_WITHIN_CATCH_PARENTHESES")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_WITHIN_SWITCH_PARENTHESES }, "COMMON_SPACE_WITHIN_SWITCH_PARENTHESES")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_WITHIN_SYNCHRONIZED_PARENTHESES }, "COMMON_SPACE_WITHIN_SYNCHRONIZED_PARENTHESES")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_WITHIN_CAST_PARENTHESES }, "COMMON_SPACE_WITHIN_CAST_PARENTHESES")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_WITHIN_BRACKETS }, "COMMON_SPACE_WITHIN_BRACKETS")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_WITHIN_BRACES }, "COMMON_SPACE_WITHIN_BRACES")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_WITHIN_ARRAY_INITIALIZER_BRACES }, "COMMON_SPACE_WITHIN_ARRAY_INITIALIZER_BRACES")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_WITHIN_EMPTY_ARRAY_INITIALIZER_BRACES }, "COMMON_SPACE_WITHIN_EMPTY_ARRAY_INITIALIZER_BRACES")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_AFTER_TYPE_CAST }, "COMMON_SPACE_AFTER_TYPE_CAST")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_METHOD_CALL_PARENTHESES }, "COMMON_SPACE_BEFORE_METHOD_CALL_PARENTHESES")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_METHOD_PARENTHESES }, "COMMON_SPACE_BEFORE_METHOD_PARENTHESES")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_IF_PARENTHESES }, "COMMON_SPACE_BEFORE_IF_PARENTHESES")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_WHILE_PARENTHESES }, "COMMON_SPACE_BEFORE_WHILE_PARENTHESES")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_FOR_PARENTHESES }, "COMMON_SPACE_BEFORE_FOR_PARENTHESES")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_TRY_PARENTHESES }, "COMMON_SPACE_BEFORE_TRY_PARENTHESES")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_CATCH_PARENTHESES }, "COMMON_SPACE_BEFORE_CATCH_PARENTHESES")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_SWITCH_PARENTHESES }, "COMMON_SPACE_BEFORE_SWITCH_PARENTHESES")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_SYNCHRONIZED_PARENTHESES }, "COMMON_SPACE_BEFORE_SYNCHRONIZED_PARENTHESES")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_CLASS_LBRACE }, "COMMON_SPACE_BEFORE_CLASS_LBRACE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_METHOD_LBRACE }, "COMMON_SPACE_BEFORE_METHOD_LBRACE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_IF_LBRACE }, "COMMON_SPACE_BEFORE_IF_LBRACE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_ELSE_LBRACE }, "COMMON_SPACE_BEFORE_ELSE_LBRACE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_WHILE_LBRACE }, "COMMON_SPACE_BEFORE_WHILE_LBRACE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_FOR_LBRACE }, "COMMON_SPACE_BEFORE_FOR_LBRACE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_DO_LBRACE }, "COMMON_SPACE_BEFORE_DO_LBRACE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_SWITCH_LBRACE }, "COMMON_SPACE_BEFORE_SWITCH_LBRACE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_TRY_LBRACE }, "COMMON_SPACE_BEFORE_TRY_LBRACE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_CATCH_LBRACE }, "COMMON_SPACE_BEFORE_CATCH_LBRACE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_FINALLY_LBRACE }, "COMMON_SPACE_BEFORE_FINALLY_LBRACE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_SYNCHRONIZED_LBRACE }, "COMMON_SPACE_BEFORE_SYNCHRONIZED_LBRACE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE }, "COMMON_SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_ANNOTATION_ARRAY_INITIALIZER_LBRACE }, "COMMON_SPACE_BEFORE_ANNOTATION_ARRAY_INITIALIZER_LBRACE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_ELSE_KEYWORD }, "COMMON_SPACE_BEFORE_ELSE_KEYWORD")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_WHILE_KEYWORD }, "COMMON_SPACE_BEFORE_WHILE_KEYWORD")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_CATCH_KEYWORD }, "COMMON_SPACE_BEFORE_CATCH_KEYWORD")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_FINALLY_KEYWORD }, "COMMON_SPACE_BEFORE_FINALLY_KEYWORD")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_QUEST }, "COMMON_SPACE_BEFORE_QUEST")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_AFTER_QUEST }, "COMMON_SPACE_AFTER_QUEST")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_COLON }, "COMMON_SPACE_BEFORE_COLON")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_AFTER_COLON }, "COMMON_SPACE_AFTER_COLON")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_TYPE_PARAMETER_LIST }, "COMMON_SPACE_BEFORE_TYPE_PARAMETER_LIST")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.CALL_PARAMETERS_WRAP }, "COMMON_CALL_PARAMETERS_WRAP")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.PREFER_PARAMETERS_WRAP }, "COMMON_PREFER_PARAMETERS_WRAP")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE }, "COMMON_CALL_PARAMETERS_LPAREN_ON_NEXT_LINE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE }, "COMMON_CALL_PARAMETERS_RPAREN_ON_NEXT_LINE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.METHOD_PARAMETERS_WRAP }, "COMMON_METHOD_PARAMETERS_WRAP")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE }, "COMMON_METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE }, "COMMON_METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.RESOURCE_LIST_WRAP }, "COMMON_RESOURCE_LIST_WRAP")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.RESOURCE_LIST_LPAREN_ON_NEXT_LINE }, "COMMON_RESOURCE_LIST_LPAREN_ON_NEXT_LINE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.RESOURCE_LIST_RPAREN_ON_NEXT_LINE }, "COMMON_RESOURCE_LIST_RPAREN_ON_NEXT_LINE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.EXTENDS_LIST_WRAP }, "COMMON_EXTENDS_LIST_WRAP")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.THROWS_LIST_WRAP }, "COMMON_THROWS_LIST_WRAP")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.EXTENDS_KEYWORD_WRAP }, "COMMON_EXTENDS_KEYWORD_WRAP")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.THROWS_KEYWORD_WRAP }, "COMMON_THROWS_KEYWORD_WRAP")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.METHOD_CALL_CHAIN_WRAP }, "COMMON_METHOD_CALL_CHAIN_WRAP")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.WRAP_FIRST_METHOD_IN_CALL_CHAIN }, "COMMON_WRAP_FIRST_METHOD_IN_CALL_CHAIN")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.PARENTHESES_EXPRESSION_LPAREN_WRAP }, "COMMON_PARENTHESES_EXPRESSION_LPAREN_WRAP")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.PARENTHESES_EXPRESSION_RPAREN_WRAP }, "COMMON_PARENTHESES_EXPRESSION_RPAREN_WRAP")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.BINARY_OPERATION_WRAP }, "COMMON_BINARY_OPERATION_WRAP")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.BINARY_OPERATION_SIGN_ON_NEXT_LINE }, "COMMON_BINARY_OPERATION_SIGN_ON_NEXT_LINE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.TERNARY_OPERATION_WRAP }, "COMMON_TERNARY_OPERATION_WRAP")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.TERNARY_OPERATION_SIGNS_ON_NEXT_LINE }, "COMMON_TERNARY_OPERATION_SIGNS_ON_NEXT_LINE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.MODIFIER_LIST_WRAP }, "COMMON_MODIFIER_LIST_WRAP")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE }, "COMMON_KEEP_SIMPLE_BLOCKS_IN_ONE_LINE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.KEEP_SIMPLE_METHODS_IN_ONE_LINE }, "COMMON_KEEP_SIMPLE_METHODS_IN_ONE_LINE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE }, "COMMON_KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.KEEP_SIMPLE_CLASSES_IN_ONE_LINE }, "COMMON_KEEP_SIMPLE_CLASSES_IN_ONE_LINE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.KEEP_MULTIPLE_EXPRESSIONS_IN_ONE_LINE }, "COMMON_KEEP_MULTIPLE_EXPRESSIONS_IN_ONE_LINE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.FOR_STATEMENT_WRAP }, "COMMON_FOR_STATEMENT_WRAP")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.FOR_STATEMENT_LPAREN_ON_NEXT_LINE }, "COMMON_FOR_STATEMENT_LPAREN_ON_NEXT_LINE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.FOR_STATEMENT_RPAREN_ON_NEXT_LINE }, "COMMON_FOR_STATEMENT_RPAREN_ON_NEXT_LINE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.ARRAY_INITIALIZER_WRAP }, "COMMON_ARRAY_INITIALIZER_WRAP")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE }, "COMMON_ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE }, "COMMON_ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.ASSIGNMENT_WRAP }, "COMMON_ASSIGNMENT_WRAP")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE }, "COMMON_PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.WRAP_COMMENTS }, "COMMON_WRAP_COMMENTS")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.ASSERT_STATEMENT_WRAP }, "COMMON_ASSERT_STATEMENT_WRAP")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SWITCH_EXPRESSIONS_WRAP }, "COMMON_SWITCH_EXPRESSIONS_WRAP")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.ASSERT_STATEMENT_COLON_ON_NEXT_LINE }, "COMMON_ASSERT_STATEMENT_COLON_ON_NEXT_LINE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.IF_BRACE_FORCE }, "COMMON_IF_BRACE_FORCE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.DOWHILE_BRACE_FORCE }, "COMMON_DOWHILE_BRACE_FORCE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.WHILE_BRACE_FORCE }, "COMMON_WHILE_BRACE_FORCE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.FOR_BRACE_FORCE }, "COMMON_FOR_BRACE_FORCE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.WRAP_LONG_LINES }, "COMMON_WRAP_LONG_LINES")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.METHOD_ANNOTATION_WRAP }, "COMMON_METHOD_ANNOTATION_WRAP")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.CLASS_ANNOTATION_WRAP }, "COMMON_CLASS_ANNOTATION_WRAP")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.FIELD_ANNOTATION_WRAP }, "COMMON_FIELD_ANNOTATION_WRAP")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.PARAMETER_ANNOTATION_WRAP }, "COMMON_PARAMETER_ANNOTATION_WRAP")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.VARIABLE_ANNOTATION_WRAP }, "COMMON_VARIABLE_ANNOTATION_WRAP")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_BEFORE_ANOTATION_PARAMETER_LIST }, "COMMON_SPACE_BEFORE_ANOTATION_PARAMETER_LIST")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.SPACE_WITHIN_ANNOTATION_PARENTHESES }, "COMMON_SPACE_WITHIN_ANNOTATION_PARENTHESES")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.ENUM_CONSTANTS_WRAP }, "COMMON_ENUM_CONSTANTS_WRAP")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.KEEP_BUILDER_METHODS_INDENTS }, "COMMON_KEEP_BUILDER_METHODS_INDENTS")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.FORCE_REARRANGE_MODE }, "COMMON_FORCE_REARRANGE_MODE")
    addMetricIfDiffersCustom(result, commonSettings, defaultCommonSettings, { s -> s.WRAP_ON_TYPING }, "COMMON_WRAP_ON_TYPING")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.PREFER_LONGER_NAMES }, "JAVA_PREFER_LONGER_NAMES")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.GENERATE_FINAL_LOCALS }, "JAVA_GENERATE_FINAL_LOCALS")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.GENERATE_FINAL_PARAMETERS }, "JAVA_GENERATE_FINAL_PARAMETERS")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.USE_EXTERNAL_ANNOTATIONS }, "JAVA_USE_EXTERNAL_ANNOTATIONS")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.GENERATE_USE_TYPE_ANNOTATION_BEFORE_TYPE }, "JAVA_GENERATE_USE_TYPE_ANNOTATION_BEFORE_TYPE")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.INSERT_OVERRIDE_ANNOTATION }, "JAVA_INSERT_OVERRIDE_ANNOTATION")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.REPEAT_SYNCHRONIZED }, "JAVA_REPEAT_SYNCHRONIZED")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.REPLACE_INSTANCEOF_AND_CAST }, "JAVA_REPLACE_INSTANCEOF_AND_CAST")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.REPLACE_NULL_CHECK }, "JAVA_REPLACE_NULL_CHECK")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.REPLACE_SUM }, "JAVA_REPLACE_SUM")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.SPACES_WITHIN_ANGLE_BRACKETS }, "JAVA_SPACES_WITHIN_ANGLE_BRACKETS")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.SPACE_AFTER_CLOSING_ANGLE_BRACKET_IN_TYPE_ARGUMENT }, "JAVA_SPACE_AFTER_CLOSING_ANGLE_BRACKET_IN_TYPE_ARGUMENT")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.SPACE_BEFORE_OPENING_ANGLE_BRACKET_IN_TYPE_PARAMETER }, "JAVA_SPACE_BEFORE_OPENING_ANGLE_BRACKET_IN_TYPE_PARAMETER")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.SPACE_AROUND_TYPE_BOUNDS_IN_TYPE_PARAMETERS }, "JAVA_SPACE_AROUND_TYPE_BOUNDS_IN_TYPE_PARAMETERS")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.DO_NOT_WRAP_AFTER_SINGLE_ANNOTATION }, "JAVA_DO_NOT_WRAP_AFTER_SINGLE_ANNOTATION")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.DO_NOT_WRAP_AFTER_SINGLE_ANNOTATION_IN_PARAMETER }, "JAVA_DO_NOT_WRAP_AFTER_SINGLE_ANNOTATION_IN_PARAMETER")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.ANNOTATION_NEW_LINE_IN_RECORD_COMPONENT }, "JAVA_ANNOTATION_NEW_LINE_IN_RECORD_COMPONENT")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.ANNOTATION_PARAMETER_WRAP }, "JAVA_ANNOTATION_PARAMETER_WRAP")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.ENUM_FIELD_ANNOTATION_WRAP }, "JAVA_ENUM_FIELD_ANNOTATION_WRAP")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.ALIGN_MULTILINE_ANNOTATION_PARAMETERS }, "JAVA_ALIGN_MULTILINE_ANNOTATION_PARAMETERS")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.NEW_LINE_AFTER_LPAREN_IN_ANNOTATION }, "JAVA_NEW_LINE_AFTER_LPAREN_IN_ANNOTATION")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.RPAREN_ON_NEW_LINE_IN_ANNOTATION }, "JAVA_RPAREN_ON_NEW_LINE_IN_ANNOTATION")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.SPACE_AROUND_ANNOTATION_EQ }, "JAVA_SPACE_AROUND_ANNOTATION_EQ")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.ALIGN_MULTILINE_TEXT_BLOCKS }, "JAVA_ALIGN_MULTILINE_TEXT_BLOCKS")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.BLANK_LINES_AROUND_INITIALIZER }, "JAVA_BLANK_LINES_AROUND_INITIALIZER")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.BLANK_LINES_AROUND_FIELD_WITH_ANNOTATIONS }, "JAVA_BLANK_LINES_AROUND_FIELD_WITH_ANNOTATIONS")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.BLANK_LINES_BETWEEN_RECORD_COMPONENTS }, "JAVA_BLANK_LINES_BETWEEN_RECORD_COMPONENTS")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.CLASS_NAMES_IN_JAVADOC }, "JAVA_CLASS_NAMES_IN_JAVADOC")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.SPACE_BEFORE_COLON_IN_FOREACH }, "JAVA_SPACE_BEFORE_COLON_IN_FOREACH")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.SPACE_INSIDE_ONE_LINE_ENUM_BRACES }, "JAVA_SPACE_INSIDE_ONE_LINE_ENUM_BRACES")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.SPACES_INSIDE_BLOCK_BRACES_WHEN_BODY_IS_PRESENT }, "JAVA_SPACES_INSIDE_BLOCK_BRACES_WHEN_BODY_IS_PRESENT")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.NEW_LINE_WHEN_BODY_IS_PRESENTED }, "JAVA_NEW_LINE_WHEN_BODY_IS_PRESENTED")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.LAYOUT_STATIC_IMPORTS_SEPARATELY }, "JAVA_LAYOUT_STATIC_IMPORTS_SEPARATELY")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.LAYOUT_ON_DEMAND_IMPORT_FROM_SAME_PACKAGE_FIRST }, "JAVA_LAYOUT_ON_DEMAND_IMPORT_FROM_SAME_PACKAGE_FIRST")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.USE_FQ_CLASS_NAMES }, "JAVA_USE_FQ_CLASS_NAMES")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.USE_SINGLE_CLASS_IMPORTS }, "JAVA_USE_SINGLE_CLASS_IMPORTS")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.INSERT_INNER_CLASS_IMPORTS }, "JAVA_INSERT_INNER_CLASS_IMPORTS")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND }, "JAVA_CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND }, "JAVA_NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.WRAP_SEMICOLON_AFTER_CALL_CHAIN }, "JAVA_WRAP_SEMICOLON_AFTER_CALL_CHAIN")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.RECORD_COMPONENTS_WRAP }, "JAVA_RECORD_COMPONENTS_WRAP")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.ALIGN_MULTILINE_RECORDS }, "JAVA_ALIGN_MULTILINE_RECORDS")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.NEW_LINE_AFTER_LPAREN_IN_RECORD_HEADER }, "JAVA_NEW_LINE_AFTER_LPAREN_IN_RECORD_HEADER")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.RPAREN_ON_NEW_LINE_IN_RECORD_HEADER }, "JAVA_RPAREN_ON_NEW_LINE_IN_RECORD_HEADER")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.SPACE_WITHIN_RECORD_HEADER }, "JAVA_SPACE_WITHIN_RECORD_HEADER")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.DECONSTRUCTION_LIST_WRAP }, "JAVA_DECONSTRUCTION_LIST_WRAP")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.ALIGN_MULTILINE_DECONSTRUCTION_LIST_COMPONENTS }, "JAVA_ALIGN_MULTILINE_DECONSTRUCTION_LIST_COMPONENTS")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.NEW_LINE_AFTER_LPAREN_IN_DECONSTRUCTION_PATTERN }, "JAVA_NEW_LINE_AFTER_LPAREN_IN_DECONSTRUCTION_PATTERN")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.RPAREN_ON_NEW_LINE_IN_DECONSTRUCTION_PATTERN }, "JAVA_RPAREN_ON_NEW_LINE_IN_DECONSTRUCTION_PATTERN")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.SPACE_WITHIN_DECONSTRUCTION_LIST }, "JAVA_SPACE_WITHIN_DECONSTRUCTION_LIST")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.SPACE_BEFORE_DECONSTRUCTION_LIST }, "JAVA_SPACE_BEFORE_DECONSTRUCTION_LIST")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.MULTI_CATCH_TYPES_WRAP }, "JAVA_MULTI_CATCH_TYPES_WRAP")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.ALIGN_TYPES_IN_MULTI_CATCH }, "JAVA_ALIGN_TYPES_IN_MULTI_CATCH")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.ENABLE_JAVADOC_FORMATTING }, "JAVA_ENABLE_JAVADOC_FORMATTING")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.JD_ALIGN_PARAM_COMMENTS }, "JAVA_JD_ALIGN_PARAM_COMMENTS")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.JD_ALIGN_EXCEPTION_COMMENTS }, "JAVA_JD_ALIGN_EXCEPTION_COMMENTS")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.JD_ADD_BLANK_AFTER_PARM_COMMENTS }, "JAVA_JD_ADD_BLANK_AFTER_PARM_COMMENTS")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.JD_ADD_BLANK_AFTER_RETURN }, "JAVA_JD_ADD_BLANK_AFTER_RETURN")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.JD_ADD_BLANK_AFTER_DESCRIPTION }, "JAVA_JD_ADD_BLANK_AFTER_DESCRIPTION")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.JD_P_AT_EMPTY_LINES }, "JAVA_JD_P_AT_EMPTY_LINES")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.JD_KEEP_INVALID_TAGS }, "JAVA_JD_KEEP_INVALID_TAGS")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.JD_KEEP_EMPTY_LINES }, "JAVA_JD_KEEP_EMPTY_LINES")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.JD_DO_NOT_WRAP_ONE_LINE_COMMENTS }, "JAVA_JD_DO_NOT_WRAP_ONE_LINE_COMMENTS")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.JD_USE_THROWS_NOT_EXCEPTION }, "JAVA_JD_USE_THROWS_NOT_EXCEPTION")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.JD_KEEP_EMPTY_PARAMETER }, "JAVA_JD_KEEP_EMPTY_PARAMETER")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.JD_KEEP_EMPTY_EXCEPTION }, "JAVA_JD_KEEP_EMPTY_EXCEPTION")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.JD_KEEP_EMPTY_RETURN }, "JAVA_JD_KEEP_EMPTY_RETURN")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.JD_LEADING_ASTERISKS_ARE_ENABLED }, "JAVA_JD_LEADING_ASTERISKS_ARE_ENABLED")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.JD_PRESERVE_LINE_FEEDS }, "JAVA_JD_PRESERVE_LINE_FEEDS")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.JD_PARAM_DESCRIPTION_ON_NEW_LINE }, "JAVA_JD_PARAM_DESCRIPTION_ON_NEW_LINE")
    addMetricIfDiffersCustom(result, javaSettings, defaultJavaSettings, { s -> s.JD_INDENT_ON_CONTINUATION }, "JAVA_JD_INDENT_ON_CONTINUATION")
    return result
  }
}

private val ALLOWED_NAMES = listOf(
  "COMMON_RIGHT_MARGIN",
  "COMMON_LINE_COMMENT_AT_FIRST_COLUMN",
  "COMMON_BLOCK_COMMENT_AT_FIRST_COLUMN",
  "COMMON_LINE_COMMENT_ADD_SPACE",
  "COMMON_BLOCK_COMMENT_ADD_SPACE",
  "COMMON_LINE_COMMENT_ADD_SPACE_ON_REFORMAT",
  "COMMON_LINE_COMMENT_ADD_SPACE_IN_SUPPRESSION",
  "COMMON_KEEP_LINE_BREAKS",
  "COMMON_KEEP_FIRST_COLUMN_COMMENT",
  "COMMON_KEEP_CONTROL_STATEMENT_IN_ONE_LINE",
  "COMMON_KEEP_BLANK_LINES_IN_DECLARATIONS",
  "COMMON_KEEP_BLANK_LINES_IN_CODE",
  "COMMON_KEEP_BLANK_LINES_BETWEEN_PACKAGE_DECLARATION_AND_HEADER",
  "COMMON_KEEP_BLANK_LINES_BEFORE_RBRACE",
  "COMMON_BLANK_LINES_BEFORE_PACKAGE",
  "COMMON_BLANK_LINES_AFTER_PACKAGE",
  "COMMON_BLANK_LINES_BEFORE_IMPORTS",
  "COMMON_BLANK_LINES_AFTER_IMPORTS",
  "COMMON_BLANK_LINES_AROUND_CLASS",
  "COMMON_BLANK_LINES_AROUND_FIELD",
  "COMMON_BLANK_LINES_AROUND_METHOD",
  "COMMON_BLANK_LINES_BEFORE_METHOD_BODY",
  "COMMON_BLANK_LINES_AROUND_FIELD_IN_INTERFACE",
  "COMMON_BLANK_LINES_AROUND_METHOD_IN_INTERFACE",
  "COMMON_BLANK_LINES_AFTER_CLASS_HEADER",
  "COMMON_BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER",
  "COMMON_BLANK_LINES_BEFORE_CLASS_END",
  "COMMON_BRACE_STYLE",
  "COMMON_CLASS_BRACE_STYLE",
  "COMMON_METHOD_BRACE_STYLE",
  "COMMON_LAMBDA_BRACE_STYLE",
  "COMMON_DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS",
  "COMMON_ELSE_ON_NEW_LINE",
  "COMMON_WHILE_ON_NEW_LINE",
  "COMMON_CATCH_ON_NEW_LINE",
  "COMMON_FINALLY_ON_NEW_LINE",
  "COMMON_INDENT_CASE_FROM_SWITCH",
  "COMMON_CASE_STATEMENT_ON_NEW_LINE",
  "COMMON_INDENT_BREAK_FROM_CASE",
  "COMMON_SPECIAL_ELSE_IF_TREATMENT",
  "COMMON_ALIGN_MULTILINE_CHAINED_METHODS",
  "COMMON_ALIGN_MULTILINE_PARAMETERS",
  "COMMON_ALIGN_MULTILINE_PARAMETERS_IN_CALLS",
  "COMMON_ALIGN_MULTILINE_RESOURCES",
  "COMMON_ALIGN_MULTILINE_FOR",
  "COMMON_ALIGN_MULTILINE_BINARY_OPERATION",
  "COMMON_ALIGN_MULTILINE_ASSIGNMENT",
  "COMMON_ALIGN_MULTILINE_TERNARY_OPERATION",
  "COMMON_ALIGN_MULTILINE_THROWS_LIST",
  "COMMON_ALIGN_THROWS_KEYWORD",
  "COMMON_ALIGN_MULTILINE_EXTENDS_LIST",
  "COMMON_ALIGN_MULTILINE_METHOD_BRACKETS",
  "COMMON_ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION",
  "COMMON_ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION",
  "COMMON_ALIGN_GROUP_FIELD_DECLARATIONS",
  "COMMON_ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS",
  "COMMON_ALIGN_CONSECUTIVE_ASSIGNMENTS",
  "COMMON_ALIGN_SUBSEQUENT_SIMPLE_METHODS",
  "COMMON_SPACE_AROUND_ASSIGNMENT_OPERATORS",
  "COMMON_SPACE_AROUND_LOGICAL_OPERATORS",
  "COMMON_SPACE_AROUND_EQUALITY_OPERATORS",
  "COMMON_SPACE_AROUND_RELATIONAL_OPERATORS",
  "COMMON_SPACE_AROUND_BITWISE_OPERATORS",
  "COMMON_SPACE_AROUND_ADDITIVE_OPERATORS",
  "COMMON_SPACE_AROUND_MULTIPLICATIVE_OPERATORS",
  "COMMON_SPACE_AROUND_SHIFT_OPERATORS",
  "COMMON_SPACE_AROUND_UNARY_OPERATOR",
  "COMMON_SPACE_AROUND_LAMBDA_ARROW",
  "COMMON_SPACE_AROUND_METHOD_REF_DBL_COLON",
  "COMMON_SPACE_AFTER_COMMA",
  "COMMON_SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS",
  "COMMON_SPACE_BEFORE_COMMA",
  "COMMON_SPACE_AFTER_SEMICOLON",
  "COMMON_SPACE_BEFORE_SEMICOLON",
  "COMMON_SPACE_WITHIN_PARENTHESES",
  "COMMON_SPACE_WITHIN_METHOD_CALL_PARENTHESES",
  "COMMON_SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES",
  "COMMON_SPACE_WITHIN_METHOD_PARENTHESES",
  "COMMON_SPACE_WITHIN_EMPTY_METHOD_PARENTHESES",
  "COMMON_SPACE_WITHIN_IF_PARENTHESES",
  "COMMON_SPACE_WITHIN_WHILE_PARENTHESES",
  "COMMON_SPACE_WITHIN_FOR_PARENTHESES",
  "COMMON_SPACE_WITHIN_TRY_PARENTHESES",
  "COMMON_SPACE_WITHIN_CATCH_PARENTHESES",
  "COMMON_SPACE_WITHIN_SWITCH_PARENTHESES",
  "COMMON_SPACE_WITHIN_SYNCHRONIZED_PARENTHESES",
  "COMMON_SPACE_WITHIN_CAST_PARENTHESES",
  "COMMON_SPACE_WITHIN_BRACKETS",
  "COMMON_SPACE_WITHIN_BRACES",
  "COMMON_SPACE_WITHIN_ARRAY_INITIALIZER_BRACES",
  "COMMON_SPACE_WITHIN_EMPTY_ARRAY_INITIALIZER_BRACES",
  "COMMON_SPACE_AFTER_TYPE_CAST",
  "COMMON_SPACE_BEFORE_METHOD_CALL_PARENTHESES",
  "COMMON_SPACE_BEFORE_METHOD_PARENTHESES",
  "COMMON_SPACE_BEFORE_IF_PARENTHESES",
  "COMMON_SPACE_BEFORE_WHILE_PARENTHESES",
  "COMMON_SPACE_BEFORE_FOR_PARENTHESES",
  "COMMON_SPACE_BEFORE_TRY_PARENTHESES",
  "COMMON_SPACE_BEFORE_CATCH_PARENTHESES",
  "COMMON_SPACE_BEFORE_SWITCH_PARENTHESES",
  "COMMON_SPACE_BEFORE_SYNCHRONIZED_PARENTHESES",
  "COMMON_SPACE_BEFORE_CLASS_LBRACE",
  "COMMON_SPACE_BEFORE_METHOD_LBRACE",
  "COMMON_SPACE_BEFORE_IF_LBRACE",
  "COMMON_SPACE_BEFORE_ELSE_LBRACE",
  "COMMON_SPACE_BEFORE_WHILE_LBRACE",
  "COMMON_SPACE_BEFORE_FOR_LBRACE",
  "COMMON_SPACE_BEFORE_DO_LBRACE",
  "COMMON_SPACE_BEFORE_SWITCH_LBRACE",
  "COMMON_SPACE_BEFORE_TRY_LBRACE",
  "COMMON_SPACE_BEFORE_CATCH_LBRACE",
  "COMMON_SPACE_BEFORE_FINALLY_LBRACE",
  "COMMON_SPACE_BEFORE_SYNCHRONIZED_LBRACE",
  "COMMON_SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE",
  "COMMON_SPACE_BEFORE_ANNOTATION_ARRAY_INITIALIZER_LBRACE",
  "COMMON_SPACE_BEFORE_ELSE_KEYWORD",
  "COMMON_SPACE_BEFORE_WHILE_KEYWORD",
  "COMMON_SPACE_BEFORE_CATCH_KEYWORD",
  "COMMON_SPACE_BEFORE_FINALLY_KEYWORD",
  "COMMON_SPACE_BEFORE_QUEST",
  "COMMON_SPACE_AFTER_QUEST",
  "COMMON_SPACE_BEFORE_COLON",
  "COMMON_SPACE_AFTER_COLON",
  "COMMON_SPACE_BEFORE_TYPE_PARAMETER_LIST",
  "COMMON_CALL_PARAMETERS_WRAP",
  "COMMON_PREFER_PARAMETERS_WRAP",
  "COMMON_CALL_PARAMETERS_LPAREN_ON_NEXT_LINE",
  "COMMON_CALL_PARAMETERS_RPAREN_ON_NEXT_LINE",
  "COMMON_METHOD_PARAMETERS_WRAP",
  "COMMON_METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE",
  "COMMON_METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE",
  "COMMON_RESOURCE_LIST_WRAP",
  "COMMON_RESOURCE_LIST_LPAREN_ON_NEXT_LINE",
  "COMMON_RESOURCE_LIST_RPAREN_ON_NEXT_LINE",
  "COMMON_EXTENDS_LIST_WRAP",
  "COMMON_THROWS_LIST_WRAP",
  "COMMON_EXTENDS_KEYWORD_WRAP",
  "COMMON_THROWS_KEYWORD_WRAP",
  "COMMON_METHOD_CALL_CHAIN_WRAP",
  "COMMON_WRAP_FIRST_METHOD_IN_CALL_CHAIN",
  "COMMON_PARENTHESES_EXPRESSION_LPAREN_WRAP",
  "COMMON_PARENTHESES_EXPRESSION_RPAREN_WRAP",
  "COMMON_BINARY_OPERATION_WRAP",
  "COMMON_BINARY_OPERATION_SIGN_ON_NEXT_LINE",
  "COMMON_TERNARY_OPERATION_WRAP",
  "COMMON_TERNARY_OPERATION_SIGNS_ON_NEXT_LINE",
  "COMMON_MODIFIER_LIST_WRAP",
  "COMMON_KEEP_SIMPLE_BLOCKS_IN_ONE_LINE",
  "COMMON_KEEP_SIMPLE_METHODS_IN_ONE_LINE",
  "COMMON_KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE",
  "COMMON_KEEP_SIMPLE_CLASSES_IN_ONE_LINE",
  "COMMON_KEEP_MULTIPLE_EXPRESSIONS_IN_ONE_LINE",
  "COMMON_FOR_STATEMENT_WRAP",
  "COMMON_FOR_STATEMENT_LPAREN_ON_NEXT_LINE",
  "COMMON_FOR_STATEMENT_RPAREN_ON_NEXT_LINE",
  "COMMON_ARRAY_INITIALIZER_WRAP",
  "COMMON_ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE",
  "COMMON_ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE",
  "COMMON_ASSIGNMENT_WRAP",
  "COMMON_PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE",
  "COMMON_WRAP_COMMENTS",
  "COMMON_ASSERT_STATEMENT_WRAP",
  "COMMON_SWITCH_EXPRESSIONS_WRAP",
  "COMMON_ASSERT_STATEMENT_COLON_ON_NEXT_LINE",
  "COMMON_IF_BRACE_FORCE",
  "COMMON_DOWHILE_BRACE_FORCE",
  "COMMON_WHILE_BRACE_FORCE",
  "COMMON_FOR_BRACE_FORCE",
  "COMMON_WRAP_LONG_LINES",
  "COMMON_METHOD_ANNOTATION_WRAP",
  "COMMON_CLASS_ANNOTATION_WRAP",
  "COMMON_FIELD_ANNOTATION_WRAP",
  "COMMON_PARAMETER_ANNOTATION_WRAP",
  "COMMON_VARIABLE_ANNOTATION_WRAP",
  "COMMON_SPACE_BEFORE_ANOTATION_PARAMETER_LIST",
  "COMMON_SPACE_WITHIN_ANNOTATION_PARENTHESES",
  "COMMON_ENUM_CONSTANTS_WRAP",
  "COMMON_KEEP_BUILDER_METHODS_INDENTS",
  "COMMON_FORCE_REARRANGE_MODE",
  "COMMON_WRAP_ON_TYPING",
  "JAVA_PREFER_LONGER_NAMES",
  "JAVA_GENERATE_FINAL_LOCALS",
  "JAVA_GENERATE_FINAL_PARAMETERS",
  "JAVA_USE_EXTERNAL_ANNOTATIONS",
  "JAVA_GENERATE_USE_TYPE_ANNOTATION_BEFORE_TYPE",
  "JAVA_INSERT_OVERRIDE_ANNOTATION",
  "JAVA_REPEAT_SYNCHRONIZED",
  "JAVA_REPLACE_INSTANCEOF_AND_CAST",
  "JAVA_REPLACE_NULL_CHECK",
  "JAVA_REPLACE_SUM",
  "JAVA_SPACES_WITHIN_ANGLE_BRACKETS",
  "JAVA_SPACE_AFTER_CLOSING_ANGLE_BRACKET_IN_TYPE_ARGUMENT",
  "JAVA_SPACE_BEFORE_OPENING_ANGLE_BRACKET_IN_TYPE_PARAMETER",
  "JAVA_SPACE_AROUND_TYPE_BOUNDS_IN_TYPE_PARAMETERS",
  "JAVA_DO_NOT_WRAP_AFTER_SINGLE_ANNOTATION",
  "JAVA_DO_NOT_WRAP_AFTER_SINGLE_ANNOTATION_IN_PARAMETER",
  "JAVA_ANNOTATION_NEW_LINE_IN_RECORD_COMPONENT",
  "JAVA_ANNOTATION_PARAMETER_WRAP",
  "JAVA_ENUM_FIELD_ANNOTATION_WRAP",
  "JAVA_ALIGN_MULTILINE_ANNOTATION_PARAMETERS",
  "JAVA_NEW_LINE_AFTER_LPAREN_IN_ANNOTATION",
  "JAVA_RPAREN_ON_NEW_LINE_IN_ANNOTATION",
  "JAVA_SPACE_AROUND_ANNOTATION_EQ",
  "JAVA_ALIGN_MULTILINE_TEXT_BLOCKS",
  "JAVA_BLANK_LINES_AROUND_INITIALIZER",
  "JAVA_BLANK_LINES_AROUND_FIELD_WITH_ANNOTATIONS",
  "JAVA_BLANK_LINES_BETWEEN_RECORD_COMPONENTS",
  "JAVA_CLASS_NAMES_IN_JAVADOC",
  "JAVA_SPACE_BEFORE_COLON_IN_FOREACH",
  "JAVA_SPACE_INSIDE_ONE_LINE_ENUM_BRACES",
  "JAVA_SPACES_INSIDE_BLOCK_BRACES_WHEN_BODY_IS_PRESENT",
  "JAVA_NEW_LINE_WHEN_BODY_IS_PRESENTED",
  "JAVA_LAYOUT_STATIC_IMPORTS_SEPARATELY",
  "JAVA_LAYOUT_ON_DEMAND_IMPORT_FROM_SAME_PACKAGE_FIRST",
  "JAVA_USE_FQ_CLASS_NAMES",
  "JAVA_USE_SINGLE_CLASS_IMPORTS",
  "JAVA_INSERT_INNER_CLASS_IMPORTS",
  "JAVA_CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND",
  "JAVA_NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND",
  "JAVA_WRAP_SEMICOLON_AFTER_CALL_CHAIN",
  "JAVA_RECORD_COMPONENTS_WRAP",
  "JAVA_ALIGN_MULTILINE_RECORDS",
  "JAVA_NEW_LINE_AFTER_LPAREN_IN_RECORD_HEADER",
  "JAVA_RPAREN_ON_NEW_LINE_IN_RECORD_HEADER",
  "JAVA_SPACE_WITHIN_RECORD_HEADER",
  "JAVA_DECONSTRUCTION_LIST_WRAP",
  "JAVA_ALIGN_MULTILINE_DECONSTRUCTION_LIST_COMPONENTS",
  "JAVA_NEW_LINE_AFTER_LPAREN_IN_DECONSTRUCTION_PATTERN",
  "JAVA_RPAREN_ON_NEW_LINE_IN_DECONSTRUCTION_PATTERN",
  "JAVA_SPACE_WITHIN_DECONSTRUCTION_LIST",
  "JAVA_SPACE_BEFORE_DECONSTRUCTION_LIST",
  "JAVA_MULTI_CATCH_TYPES_WRAP",
  "JAVA_ALIGN_TYPES_IN_MULTI_CATCH",
  "JAVA_ENABLE_JAVADOC_FORMATTING",
  "JAVA_JD_ALIGN_PARAM_COMMENTS",
  "JAVA_JD_ALIGN_EXCEPTION_COMMENTS",
  "JAVA_JD_ADD_BLANK_AFTER_PARM_COMMENTS",
  "JAVA_JD_ADD_BLANK_AFTER_RETURN",
  "JAVA_JD_ADD_BLANK_AFTER_DESCRIPTION",
  "JAVA_JD_P_AT_EMPTY_LINES",
  "JAVA_JD_KEEP_INVALID_TAGS",
  "JAVA_JD_KEEP_EMPTY_LINES",
  "JAVA_JD_DO_NOT_WRAP_ONE_LINE_COMMENTS",
  "JAVA_JD_USE_THROWS_NOT_EXCEPTION",
  "JAVA_JD_KEEP_EMPTY_PARAMETER",
  "JAVA_JD_KEEP_EMPTY_EXCEPTION",
  "JAVA_JD_KEEP_EMPTY_RETURN",
  "JAVA_JD_LEADING_ASTERISKS_ARE_ENABLED",
  "JAVA_JD_PRESERVE_LINE_FEEDS",
  "JAVA_JD_PARAM_DESCRIPTION_ON_NEW_LINE",
  "JAVA_JD_INDENT_ON_CONTINUATION"
)