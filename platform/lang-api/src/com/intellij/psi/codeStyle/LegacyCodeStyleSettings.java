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
@Deprecated
public class LegacyCodeStyleSettings extends CommonCodeStyleSettings {
  /**
   * @deprecated Use {@link CommonCodeStyleSettings#DO_NOT_WRAP}
   */
  @Deprecated public static final int DO_NOT_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
  /**
   * @deprecated Use {@link CommonCodeStyleSettings#WRAP_AS_NEEDED}
   */
  @Deprecated public static final int WRAP_AS_NEEDED = CommonCodeStyleSettings.WRAP_AS_NEEDED;
  /**
   * @deprecated Use {@link CommonCodeStyleSettings#WRAP_ALWAYS}
   */
  @Deprecated public static final int WRAP_ALWAYS = CommonCodeStyleSettings.WRAP_ALWAYS;
  /**
   * @deprecated Use {@link CommonCodeStyleSettings#WRAP_ON_EVERY_ITEM}
   */
  @Deprecated public static final int WRAP_ON_EVERY_ITEM = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;

  /**
   * @deprecated Use {@link CommonCodeStyleSettings#DO_NOT_FORCE}
   */
  @Deprecated public static final int DO_NOT_FORCE = CommonCodeStyleSettings.DO_NOT_FORCE;
  /**
   * @deprecated Use {@link CommonCodeStyleSettings#FORCE_BRACES_IF_MULTILINE}
   */
  @Deprecated public static final int FORCE_BRACES_IF_MULTILINE = CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE;
  /**
   * @deprecated Use {@link CommonCodeStyleSettings#FORCE_BRACES_ALWAYS}
   */
  @Deprecated public static final int FORCE_BRACES_ALWAYS = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
  /**
   * @deprecated Use {@link CommonCodeStyleSettings#END_OF_LINE}
   */
  @Deprecated public static final int END_OF_LINE = 1;
  /**
   * @deprecated Use {@link CommonCodeStyleSettings#NEXT_LINE}
   */
  @Deprecated public static final int NEXT_LINE = 2;
  /**
   * @deprecated Use {@link CommonCodeStyleSettings#NEXT_LINE_SHIFTED}
   */
  @Deprecated public static final int NEXT_LINE_SHIFTED = 3;
  /**
   * @deprecated Use {@link CommonCodeStyleSettings#NEXT_LINE_SHIFTED2}
   */
  @Deprecated public static final int NEXT_LINE_SHIFTED2 = 4;
  /**
   * @deprecated Use {@link CommonCodeStyleSettings#NEXT_LINE_IF_WRAPPED}
   */
  @Deprecated public static final int NEXT_LINE_IF_WRAPPED = 5;

  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public int RIGHT_MARGIN = -1;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean KEEP_LINE_BREAKS = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean KEEP_FIRST_COLUMN_COMMENT = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean KEEP_CONTROL_STATEMENT_IN_ONE_LINE = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public int KEEP_BLANK_LINES_IN_DECLARATIONS = 2;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public int KEEP_BLANK_LINES_IN_CODE = 2;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public int KEEP_BLANK_LINES_BEFORE_RBRACE = 2;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public int BLANK_LINES_AROUND_CLASS = 1;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public int BLANK_LINES_AROUND_METHOD = 1;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean INDENT_CASE_FROM_SWITCH = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean ALIGN_MULTILINE_PARAMETERS_IN_CALLS = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean ALIGN_MULTILINE_BINARY_OPERATION = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean ALIGN_GROUP_FIELD_DECLARATIONS = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_AROUND_ASSIGNMENT_OPERATORS = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_AROUND_LOGICAL_OPERATORS = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_AROUND_EQUALITY_OPERATORS = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_AROUND_RELATIONAL_OPERATORS = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_AROUND_BITWISE_OPERATORS = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_AROUND_ADDITIVE_OPERATORS = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_AROUND_MULTIPLICATIVE_OPERATORS = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_AROUND_SHIFT_OPERATORS = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_AFTER_COMMA = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_BEFORE_COMMA = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_AFTER_SEMICOLON = true; // in for-statement
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_BEFORE_SEMICOLON = false; // in for-statement
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_WITHIN_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_WITHIN_METHOD_CALL_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_WITHIN_METHOD_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_WITHIN_IF_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_WITHIN_WHILE_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_WITHIN_CAST_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_WITHIN_BRACKETS = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_AFTER_TYPE_CAST = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_BEFORE_METHOD_CALL_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_BEFORE_METHOD_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_BEFORE_IF_PARENTHESES = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_BEFORE_WHILE_PARENTHESES = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_BEFORE_CLASS_LBRACE = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_BEFORE_METHOD_LBRACE = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_BEFORE_IF_LBRACE = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_BEFORE_WHILE_LBRACE = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_BEFORE_QUEST = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_AFTER_QUEST = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_BEFORE_COLON = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean SPACE_AFTER_COLON = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public int ARRAY_INITIALIZER_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public boolean ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public int BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated public int CLASS_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;

  public LegacyCodeStyleSettings() {
    super(null);
  }
}
