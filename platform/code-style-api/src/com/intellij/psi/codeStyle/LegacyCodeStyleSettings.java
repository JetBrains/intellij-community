// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.psi.codeStyle;

import com.intellij.lang.Language;
import com.intellij.psi.PsiFile;

/**
 * Contains fields which are left for compatibility with earlier versions. These fields shouldn't be used anymore. Every language must have
 * its own settings which can be retrieved using {@link CodeStyleSettings#getCommonSettings(Language)} or
 * {@link com.intellij.application.options.CodeStyle#getLanguageSettings(PsiFile)}.
 *
 * @see LanguageCodeStyleSettingsProvider
 */
public class LegacyCodeStyleSettings extends CommonCodeStyleSettings {
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int RIGHT_MARGIN = -1;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean LINE_COMMENT_AT_FIRST_COLUMN = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean BLOCK_COMMENT_AT_FIRST_COLUMN = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean LINE_COMMENT_ADD_SPACE = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean KEEP_LINE_BREAKS = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean KEEP_FIRST_COLUMN_COMMENT = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean KEEP_CONTROL_STATEMENT_IN_ONE_LINE = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int KEEP_BLANK_LINES_IN_DECLARATIONS = 2;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int KEEP_BLANK_LINES_IN_CODE = 2;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int KEEP_BLANK_LINES_BEFORE_RBRACE = 2;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int BLANK_LINES_BEFORE_PACKAGE = 0;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int BLANK_LINES_AFTER_PACKAGE = 1;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int BLANK_LINES_BEFORE_IMPORTS = 1;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int BLANK_LINES_AFTER_IMPORTS = 1;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int BLANK_LINES_AROUND_CLASS = 1;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int BLANK_LINES_AROUND_FIELD = 0;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int BLANK_LINES_AROUND_METHOD = 1;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int BLANK_LINES_BEFORE_METHOD_BODY = 0;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int BLANK_LINES_AROUND_FIELD_IN_INTERFACE = 0;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int BLANK_LINES_AROUND_METHOD_IN_INTERFACE = 1;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int BLANK_LINES_AFTER_CLASS_HEADER = 0;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int BLANK_LINES_AFTER_ANONYMOUS_CLASS_HEADER = 0;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean USE_FLYING_GEESE_BRACES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean ELSE_ON_NEW_LINE = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean WHILE_ON_NEW_LINE = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean CATCH_ON_NEW_LINE = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean FINALLY_ON_NEW_LINE = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean INDENT_CASE_FROM_SWITCH = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean CASE_STATEMENT_ON_NEW_LINE = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean INDENT_BREAK_FROM_CASE = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPECIAL_ELSE_IF_TREATMENT = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean ALIGN_MULTILINE_CHAINED_METHODS = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean ALIGN_MULTILINE_PARAMETERS = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean ALIGN_MULTILINE_PARAMETERS_IN_CALLS = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean ALIGN_MULTILINE_RESOURCES = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean ALIGN_MULTILINE_FOR = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean ALIGN_MULTILINE_BINARY_OPERATION = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean ALIGN_MULTILINE_ASSIGNMENT = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean ALIGN_MULTILINE_TERNARY_OPERATION = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean ALIGN_MULTILINE_THROWS_LIST = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean ALIGN_THROWS_KEYWORD = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean ALIGN_MULTILINE_EXTENDS_LIST = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean ALIGN_MULTILINE_METHOD_BRACKETS = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean ALIGN_GROUP_FIELD_DECLARATIONS = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean ALIGN_SUBSEQUENT_SIMPLE_METHODS = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_AROUND_ASSIGNMENT_OPERATORS = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_AROUND_LOGICAL_OPERATORS = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_AROUND_EQUALITY_OPERATORS = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_AROUND_RELATIONAL_OPERATORS = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_AROUND_BITWISE_OPERATORS = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_AROUND_ADDITIVE_OPERATORS = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_AROUND_MULTIPLICATIVE_OPERATORS = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_AROUND_SHIFT_OPERATORS = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_AROUND_UNARY_OPERATOR = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_AROUND_LAMBDA_ARROW = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_AROUND_METHOD_REF_DBL_COLON = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_AFTER_COMMA = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_COMMA = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_AFTER_SEMICOLON = true; // in for-statement
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_SEMICOLON = false; // in for-statement
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_WITHIN_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_WITHIN_METHOD_CALL_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_WITHIN_METHOD_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_WITHIN_EMPTY_METHOD_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_WITHIN_IF_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_WITHIN_WHILE_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_WITHIN_FOR_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_WITHIN_TRY_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_WITHIN_CATCH_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_WITHIN_SWITCH_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_WITHIN_SYNCHRONIZED_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_WITHIN_CAST_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_WITHIN_BRACKETS = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_WITHIN_BRACES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_WITHIN_ARRAY_INITIALIZER_BRACES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_WITHIN_EMPTY_ARRAY_INITIALIZER_BRACES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_AFTER_TYPE_CAST = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_METHOD_CALL_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_METHOD_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_IF_PARENTHESES = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_WHILE_PARENTHESES = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_FOR_PARENTHESES = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_TRY_PARENTHESES = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_CATCH_PARENTHESES = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_SWITCH_PARENTHESES = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_SYNCHRONIZED_PARENTHESES = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_CLASS_LBRACE = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_METHOD_LBRACE = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_IF_LBRACE = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_ELSE_LBRACE = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_WHILE_LBRACE = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_FOR_LBRACE = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_DO_LBRACE = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_SWITCH_LBRACE = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_TRY_LBRACE = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_CATCH_LBRACE = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_FINALLY_LBRACE = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_SYNCHRONIZED_LBRACE = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_ANNOTATION_ARRAY_INITIALIZER_LBRACE = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_ELSE_KEYWORD = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_WHILE_KEYWORD = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_CATCH_KEYWORD = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_FINALLY_KEYWORD = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_QUEST = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_AFTER_QUEST = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_COLON = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_AFTER_COLON = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_TYPE_PARAMETER_LIST = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int CALL_PARAMETERS_WRAP = DO_NOT_WRAP;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean PREFER_PARAMETERS_WRAP = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = false; // misnamed, actually means: wrap AFTER lparen
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int METHOD_PARAMETERS_WRAP = DO_NOT_WRAP;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int RESOURCE_LIST_WRAP = DO_NOT_WRAP;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean RESOURCE_LIST_LPAREN_ON_NEXT_LINE = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean RESOURCE_LIST_RPAREN_ON_NEXT_LINE = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int EXTENDS_LIST_WRAP = DO_NOT_WRAP;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int THROWS_LIST_WRAP = DO_NOT_WRAP;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int EXTENDS_KEYWORD_WRAP = DO_NOT_WRAP;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int THROWS_KEYWORD_WRAP = DO_NOT_WRAP;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int METHOD_CALL_CHAIN_WRAP = DO_NOT_WRAP;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean WRAP_FIRST_METHOD_IN_CALL_CHAIN = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean PARENTHESES_EXPRESSION_LPAREN_WRAP = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean PARENTHESES_EXPRESSION_RPAREN_WRAP = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int BINARY_OPERATION_WRAP = DO_NOT_WRAP;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean BINARY_OPERATION_SIGN_ON_NEXT_LINE = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int TERNARY_OPERATION_WRAP = DO_NOT_WRAP;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean TERNARY_OPERATION_SIGNS_ON_NEXT_LINE = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean MODIFIER_LIST_WRAP = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean KEEP_SIMPLE_METHODS_IN_ONE_LINE = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean KEEP_SIMPLE_CLASSES_IN_ONE_LINE = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean KEEP_MULTIPLE_EXPRESSIONS_IN_ONE_LINE = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int FOR_STATEMENT_WRAP = DO_NOT_WRAP;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean FOR_STATEMENT_LPAREN_ON_NEXT_LINE = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean FOR_STATEMENT_RPAREN_ON_NEXT_LINE = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int ARRAY_INITIALIZER_WRAP = DO_NOT_WRAP;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int ASSIGNMENT_WRAP = DO_NOT_WRAP;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int LABELED_STATEMENT_WRAP = WRAP_ALWAYS;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean WRAP_COMMENTS = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int ASSERT_STATEMENT_WRAP = DO_NOT_WRAP;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean ASSERT_STATEMENT_COLON_ON_NEXT_LINE = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int IF_BRACE_FORCE = DO_NOT_FORCE;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int DOWHILE_BRACE_FORCE = DO_NOT_FORCE;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int WHILE_BRACE_FORCE = DO_NOT_FORCE;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int FOR_BRACE_FORCE = DO_NOT_FORCE;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean WRAP_LONG_LINES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int METHOD_ANNOTATION_WRAP = WRAP_ALWAYS;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int CLASS_ANNOTATION_WRAP = WRAP_ALWAYS;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int FIELD_ANNOTATION_WRAP = WRAP_ALWAYS;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int PARAMETER_ANNOTATION_WRAP = DO_NOT_WRAP;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int VARIABLE_ANNOTATION_WRAP = DO_NOT_WRAP;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_BEFORE_ANOTATION_PARAMETER_LIST = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_WITHIN_ANNOTATION_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int ENUM_CONSTANTS_WRAP = DO_NOT_WRAP;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int FORCE_REARRANGE_MODE = REARRANGE_ACCORDIND_TO_DIALOG;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public int WRAP_ON_TYPING = CommonCodeStyleSettings.WrapOnTyping.DEFAULT.intValue;

  public LegacyCodeStyleSettings() {
    super(null);
  }
}
