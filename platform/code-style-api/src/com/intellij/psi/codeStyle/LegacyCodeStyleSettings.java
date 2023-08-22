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
@SuppressWarnings("FieldNameHidesFieldInSuperclass")
public class LegacyCodeStyleSettings extends CommonCodeStyleSettings {
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean KEEP_LINE_BREAKS = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public int KEEP_BLANK_LINES_IN_DECLARATIONS = 2;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public int KEEP_BLANK_LINES_IN_CODE = 2;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public int BLANK_LINES_AROUND_CLASS = 1;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean INDENT_CASE_FROM_SWITCH = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated
  public boolean SPACE_AROUND_ASSIGNMENT_OPERATORS = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean SPACE_AROUND_LOGICAL_OPERATORS = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean SPACE_AROUND_EQUALITY_OPERATORS = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean SPACE_AROUND_RELATIONAL_OPERATORS = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean SPACE_AROUND_BITWISE_OPERATORS = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean SPACE_AROUND_ADDITIVE_OPERATORS = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean SPACE_AROUND_MULTIPLICATIVE_OPERATORS = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean SPACE_AROUND_SHIFT_OPERATORS = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean SPACE_AFTER_COMMA = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean SPACE_BEFORE_COMMA = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean SPACE_AFTER_SEMICOLON = true; // in for-statement
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean SPACE_BEFORE_SEMICOLON = false; // in for-statement
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean SPACE_WITHIN_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean SPACE_WITHIN_METHOD_CALL_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean SPACE_WITHIN_METHOD_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean SPACE_WITHIN_IF_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean SPACE_WITHIN_WHILE_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean SPACE_WITHIN_BRACKETS = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean SPACE_AFTER_TYPE_CAST = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean SPACE_BEFORE_METHOD_CALL_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean SPACE_BEFORE_METHOD_PARENTHESES = false;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean SPACE_BEFORE_IF_PARENTHESES = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean SPACE_BEFORE_WHILE_PARENTHESES = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean SPACE_BEFORE_CLASS_LBRACE = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean SPACE_BEFORE_METHOD_LBRACE = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean SPACE_BEFORE_IF_LBRACE = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean SPACE_BEFORE_WHILE_LBRACE = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean SPACE_BEFORE_QUEST = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean SPACE_AFTER_QUEST = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean SPACE_BEFORE_COLON = true;
  /**
   * @deprecated See {@link LegacyCodeStyleSettings}
   */
  @Deprecated(forRemoval = true)
  public boolean SPACE_AFTER_COLON = true;

  public LegacyCodeStyleSettings() {
    super(null);
  }
}
