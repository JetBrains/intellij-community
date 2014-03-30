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

public class CodeStyleSpacesPanel extends OptionTreeWithPreviewPanel {
  public CodeStyleSpacesPanel(CodeStyleSettings settings) {
    super(settings);
    init();
  }

  @Override
  public LanguageCodeStyleSettingsProvider.SettingsType getSettingsType() {
    return LanguageCodeStyleSettingsProvider.SettingsType.SPACING_SETTINGS;
  }

  @Override
  protected void initTables() {
    initBooleanField("SPACE_BEFORE_METHOD_PARENTHESES", ApplicationBundle.message("checkbox.spaces.method.declaration.parentheses"), SPACES_BEFORE_PARENTHESES);
    initBooleanField("SPACE_BEFORE_METHOD_CALL_PARENTHESES", ApplicationBundle.message("checkbox.spaces.method.call.parentheses"), SPACES_BEFORE_PARENTHESES);
    initBooleanField("SPACE_BEFORE_IF_PARENTHESES", ApplicationBundle.message("checkbox.spaces.if.parentheses"), SPACES_BEFORE_PARENTHESES);
    initBooleanField("SPACE_BEFORE_FOR_PARENTHESES", ApplicationBundle.message("checkbox.spaces.for.parentheses"), SPACES_BEFORE_PARENTHESES);
    initBooleanField("SPACE_BEFORE_WHILE_PARENTHESES", ApplicationBundle.message("checkbox.spaces.while.parentheses"), SPACES_BEFORE_PARENTHESES);
    initBooleanField("SPACE_BEFORE_SWITCH_PARENTHESES", ApplicationBundle.message("checkbox.spaces.switch.parentheses"), SPACES_BEFORE_PARENTHESES);
    initBooleanField("SPACE_BEFORE_TRY_PARENTHESES", ApplicationBundle.message("checkbox.spaces.try.parentheses"), SPACES_BEFORE_PARENTHESES);
    initBooleanField("SPACE_BEFORE_CATCH_PARENTHESES", ApplicationBundle.message("checkbox.spaces.catch.parentheses"), SPACES_BEFORE_PARENTHESES);
    initBooleanField("SPACE_BEFORE_SYNCHRONIZED_PARENTHESES", ApplicationBundle.message("checkbox.spaces.synchronized.parentheses"), SPACES_BEFORE_PARENTHESES);
    initBooleanField("SPACE_BEFORE_ANOTATION_PARAMETER_LIST", ApplicationBundle.message("checkbox.spaces.annotation.parameters"), SPACES_BEFORE_PARENTHESES);
    initCustomOptions(SPACES_BEFORE_PARENTHESES);

    initBooleanField("SPACE_AROUND_ASSIGNMENT_OPERATORS", ApplicationBundle.message("checkbox.spaces.assignment.operators"), SPACES_AROUND_OPERATORS);
    initBooleanField("SPACE_AROUND_LOGICAL_OPERATORS", ApplicationBundle.message("checkbox.spaces.logical.operators"), SPACES_AROUND_OPERATORS);
    initBooleanField("SPACE_AROUND_EQUALITY_OPERATORS", ApplicationBundle.message("checkbox.spaces.equality.operators"), SPACES_AROUND_OPERATORS);
    initBooleanField("SPACE_AROUND_RELATIONAL_OPERATORS", ApplicationBundle.message("checkbox.spaces.relational.operators"), SPACES_AROUND_OPERATORS);
    initBooleanField("SPACE_AROUND_BITWISE_OPERATORS", ApplicationBundle.message("checkbox.spaces.bitwise.operators"), SPACES_AROUND_OPERATORS);
    initBooleanField("SPACE_AROUND_ADDITIVE_OPERATORS", ApplicationBundle.message("checkbox.spaces.additive.operators"), SPACES_AROUND_OPERATORS);
    initBooleanField("SPACE_AROUND_MULTIPLICATIVE_OPERATORS", ApplicationBundle.message("checkbox.spaces.multiplicative.operators"), SPACES_AROUND_OPERATORS);
    initBooleanField("SPACE_AROUND_SHIFT_OPERATORS", ApplicationBundle.message("checkbox.spaces.shift.operators"), SPACES_AROUND_OPERATORS);
    initBooleanField("SPACE_AROUND_UNARY_OPERATOR", ApplicationBundle.message("checkbox.spaces.around.unary.operator"), SPACES_AROUND_OPERATORS);
    initBooleanField("SPACE_AROUND_LAMBDA_ARROW", ApplicationBundle.message("checkbox.spaces.around.lambda.arrow"), SPACES_AROUND_OPERATORS);
    initBooleanField("SPACE_AROUND_METHOD_REF_DBL_COLON", ApplicationBundle.message("checkbox.spaces.around.method.ref.dbl.colon.arrow"), SPACES_AROUND_OPERATORS);
    initCustomOptions(SPACES_AROUND_OPERATORS);

    initBooleanField("SPACE_BEFORE_CLASS_LBRACE", ApplicationBundle.message("checkbox.spaces.class.left.brace"), SPACES_BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_METHOD_LBRACE", ApplicationBundle.message("checkbox.spaces.method.left.brace"), SPACES_BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_IF_LBRACE", ApplicationBundle.message("checkbox.spaces.if.left.brace"), SPACES_BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_ELSE_LBRACE", ApplicationBundle.message("checkbox.spaces.else.left.brace"), SPACES_BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_FOR_LBRACE", ApplicationBundle.message("checkbox.spaces.for.left.brace"), SPACES_BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_WHILE_LBRACE", ApplicationBundle.message("checkbox.spaces.while.left.brace"), SPACES_BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_DO_LBRACE", ApplicationBundle.message("checkbox.spaces.do.left.brace"), SPACES_BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_SWITCH_LBRACE", ApplicationBundle.message("checkbox.spaces.switch.left.brace"), SPACES_BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_TRY_LBRACE", ApplicationBundle.message("checkbox.spaces.try.left.brace"), SPACES_BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_CATCH_LBRACE", ApplicationBundle.message("checkbox.spaces.catch.left.brace"), SPACES_BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_FINALLY_LBRACE", ApplicationBundle.message("checkbox.spaces.finally.left.brace"), SPACES_BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_SYNCHRONIZED_LBRACE", ApplicationBundle.message("checkbox.spaces.synchronized.left.brace"), SPACES_BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE", ApplicationBundle.message("checkbox.spaces.array.initializer.left.brace"), SPACES_BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_ANNOTATION_ARRAY_INITIALIZER_LBRACE", ApplicationBundle.message("checkbox.spaces.annotation.array.initializer.left.brace"), SPACES_BEFORE_LEFT_BRACE);
    initCustomOptions(SPACES_BEFORE_LEFT_BRACE);

    initBooleanField("SPACE_BEFORE_ELSE_KEYWORD", ApplicationBundle.message("checkbox.spaces.else.keyword"), SPACES_BEFORE_KEYWORD);
    initBooleanField("SPACE_BEFORE_WHILE_KEYWORD", ApplicationBundle.message("checkbox.spaces.while.keyword"), SPACES_BEFORE_KEYWORD);
    initBooleanField("SPACE_BEFORE_CATCH_KEYWORD", ApplicationBundle.message("checkbox.spaces.catch.keyword"), SPACES_BEFORE_KEYWORD);
    initBooleanField("SPACE_BEFORE_FINALLY_KEYWORD", ApplicationBundle.message("checkbox.spaces.finally.keyword"), SPACES_BEFORE_KEYWORD);
    initCustomOptions(SPACES_BEFORE_KEYWORD);

    initBooleanField("SPACE_WITHIN_BRACES", ApplicationBundle.message("checkbox.spaces.within.braces"), SPACES_WITHIN);
    initBooleanField("SPACE_WITHIN_BRACKETS", ApplicationBundle.message("checkbox.spaces.within.brackets"), SPACES_WITHIN);
    initBooleanField("SPACE_WITHIN_ARRAY_INITIALIZER_BRACES", ApplicationBundle.message("checkbox.spaces.within.array.initializer.braces"), SPACES_WITHIN);
    initBooleanField("SPACE_WITHIN_PARENTHESES", ApplicationBundle.message("checkbox.spaces.within.parentheses"), SPACES_WITHIN);
    initBooleanField("SPACE_WITHIN_METHOD_CALL_PARENTHESES", ApplicationBundle.message("checkbox.spaces.checkbox.spaces.method.call.parentheses"), SPACES_WITHIN);
    initBooleanField(
      "SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES",
      ApplicationBundle.message("checkbox.spaces.checkbox.spaces.empty.method.call.parentheses"),
      SPACES_WITHIN
    );
    initBooleanField("SPACE_WITHIN_METHOD_PARENTHESES", ApplicationBundle.message("checkbox.spaces.checkbox.spaces.method.declaration.parentheses"), SPACES_WITHIN);
    initBooleanField(
      "SPACE_WITHIN_EMPTY_METHOD_PARENTHESES",
      ApplicationBundle.message("checkbox.spaces.checkbox.spaces.empty.method.declaration.parentheses"),
      SPACES_WITHIN
    );
    initBooleanField("SPACE_WITHIN_IF_PARENTHESES", ApplicationBundle.message("checkbox.spaces.if.parentheses"), SPACES_WITHIN);
    initBooleanField("SPACE_WITHIN_FOR_PARENTHESES", ApplicationBundle.message("checkbox.spaces.for.parentheses"), SPACES_WITHIN);
    initBooleanField("SPACE_WITHIN_WHILE_PARENTHESES", ApplicationBundle.message("checkbox.spaces.while.parentheses"), SPACES_WITHIN);
    initBooleanField("SPACE_WITHIN_SWITCH_PARENTHESES", ApplicationBundle.message("checkbox.spaces.switch.parentheses"), SPACES_WITHIN);
    initBooleanField("SPACE_WITHIN_TRY_PARENTHESES", ApplicationBundle.message("checkbox.spaces.try.parentheses"), SPACES_WITHIN);
    initBooleanField("SPACE_WITHIN_CATCH_PARENTHESES", ApplicationBundle.message("checkbox.spaces.catch.parentheses"), SPACES_WITHIN);
    initBooleanField("SPACE_WITHIN_SYNCHRONIZED_PARENTHESES", ApplicationBundle.message("checkbox.spaces.synchronized.parentheses"), SPACES_WITHIN);
    initBooleanField("SPACE_WITHIN_CAST_PARENTHESES", ApplicationBundle.message("checkbox.spaces.type.cast.parentheses"), SPACES_WITHIN);
    initBooleanField("SPACE_WITHIN_ANNOTATION_PARENTHESES", ApplicationBundle.message("checkbox.spaces.annotation.parentheses"), SPACES_WITHIN);
    initCustomOptions(SPACES_WITHIN);

    initBooleanField("SPACE_BEFORE_QUEST", ApplicationBundle.message("checkbox.spaces.before.question"), SPACES_IN_TERNARY_OPERATOR);
    initBooleanField("SPACE_AFTER_QUEST", ApplicationBundle.message("checkbox.spaces.after.question"), SPACES_IN_TERNARY_OPERATOR);
    initBooleanField("SPACE_BEFORE_COLON", ApplicationBundle.message("checkbox.spaces.before.colon"), SPACES_IN_TERNARY_OPERATOR);
    initBooleanField("SPACE_AFTER_COLON", ApplicationBundle.message("checkbox.spaces.after.colon"), SPACES_IN_TERNARY_OPERATOR);
    initCustomOptions(SPACES_IN_TERNARY_OPERATOR);

    initBooleanField("SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS", ApplicationBundle.message("checkbox.spaces.after.comma"), SPACES_WITHIN_TYPE_ARGUMENTS);
    initCustomOptions(SPACES_WITHIN_TYPE_ARGUMENTS);

    initCustomOptions(SPACES_IN_TYPE_ARGUMENTS);
    initCustomOptions(SPACES_IN_TYPE_PARAMETERS);

    initBooleanField("SPACE_BEFORE_COMMA", ApplicationBundle.message("checkbox.spaces.before.comma"), SPACES_OTHER);
    initBooleanField("SPACE_AFTER_COMMA", ApplicationBundle.message("checkbox.spaces.after.comma"), SPACES_OTHER);
    initBooleanField("SPACE_BEFORE_SEMICOLON", ApplicationBundle.message("checkbox.spaces.before.semicolon"), SPACES_OTHER);
    initBooleanField("SPACE_AFTER_SEMICOLON", ApplicationBundle.message("checkbox.spaces.after.semicolon"), SPACES_OTHER);
    initBooleanField("SPACE_AFTER_TYPE_CAST", ApplicationBundle.message("checkbox.spaces.after.type.cast"), SPACES_OTHER);
    initCustomOptions(SPACES_OTHER);
  }

  @Override
  protected String getTabTitle() {
    return ApplicationBundle.message("title.spaces");
  }
}
