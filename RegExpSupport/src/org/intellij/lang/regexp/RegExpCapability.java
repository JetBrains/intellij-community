/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.intellij.lang.regexp;

import java.util.EnumSet;

/**
 * @author yole
 */
public enum RegExpCapability {
  XML_SCHEMA_MODE,

  /**
   * Normal mode is ']' and '}' allowed as regular character.
   * In this  mode '{' is also allowed as character when not part of quantifier.
   */
  DANGLING_METACHARACTERS,

  /**
   * Normal mode is ']' and '}' allowed as regular character.
   * In this mode ']' and '}' are NOT allowed as regular character.
   * This mode overrides DANGLING_METACHARACTERS.
   */
  NO_DANGLING_METACHARACTERS,
  NESTED_CHARACTER_CLASSES,

  /**
   * supports three-digit octal numbers not started with 0 (e.g. \123), 
   * if false \1 from \123 will be considered as a group back reference
   */
  OCTAL_NO_LEADING_ZERO,

  /**
   * '{,1}' is legal
   */
  OMIT_NUMBERS_IN_QUANTIFIERS,

  /**
   * {,} allowed as quantifier (Python).
   */
  OMIT_BOTH_NUMBERS_IN_QUANTIFIERS,
  COMMENT_MODE,

  /**
   * In comment mode, spaces, tabs, etc in a class are also whitespace. Comments also work inside classes (Java).
   */
  WHITESPACE_IN_CLASS,

  /**
   * '\h'
   */
  ALLOW_HEX_DIGIT_CLASS,
  /**
   * supports [] to be valid character class
   */
  ALLOW_EMPTY_CHARACTER_CLASS,
  ALLOW_HORIZONTAL_WHITESPACE_CLASS,

  /**
   * allows not to wrap one-letter unicode categories with braces: \p{L} -> \pL
   */
  UNICODE_CATEGORY_SHORTHAND,

  /**
   * supports expressions like [[:alpha:]], [^[:alpha:]], [[:^alpha:]]
   */
  POSIX_BRACKET_EXPRESSIONS,

  /**
   * supports for property negations like \p{^Alnum}
   */
  CARET_NEGATED_PROPERTIES,

  /**
   * \\u, \l, \\U, \L, and \E
   */
  TRANSFORMATION_ESCAPES,

  /**
   * \\177 (decimal 127) is maximal octal character
   */
  MAX_OCTAL_177,

  /**
   * \\377 (decimal 255) is maximal octal character
   */
  MAX_OCTAL_377,

  /**
   * At least 2 digits needed in octal escape outside character class to be valid (like regexp under ruby)
   */
  MIN_OCTAL_2_DIGITS,

  /**
   * At least 3 digits needed in octal escape outside character class to be valid (like regexp under python)
   */
  MIN_OCTAL_3_DIGITS,

  /**
   * \\u{1F680} or \\x{1F680}
   */
  EXTENDED_UNICODE_CHARACTER,

  /**
   * Allow \x9 in addition to \x09 (ruby)
   */
  ONE_HEX_CHAR_ESCAPE,

  /**
   * MySQL character classes [=c=] [.class.] [:<:] [:>:]
   */
  MYSQL_BRACKET_EXPRESSIONS,
  ;
  static final EnumSet<RegExpCapability> DEFAULT_CAPABILITIES = EnumSet.of(NESTED_CHARACTER_CLASSES,
                                                                           ALLOW_HORIZONTAL_WHITESPACE_CLASS,
                                                                           UNICODE_CATEGORY_SHORTHAND,
                                                                           EXTENDED_UNICODE_CHARACTER);
}
