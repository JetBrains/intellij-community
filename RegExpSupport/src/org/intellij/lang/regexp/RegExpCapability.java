// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp;

import java.util.EnumSet;


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
   * supports properties with name and value like \p{name=value}
   */
  PROPERTY_VALUES,

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

  /**
   * \g{[integer]} \g[unsigned integer]
   */
  PCRE_BACK_REFERENCES,

  /**
   * (?group_id)
   */
  PCRE_NUMBERED_GROUP_REF,

  /**
   * Allow PCRE conditions DEFINE and VERSION[>]?=n.m in conditional groups
   */
  PCRE_CONDITIONS,
  ;
  static final EnumSet<RegExpCapability> DEFAULT_CAPABILITIES = EnumSet.of(NESTED_CHARACTER_CLASSES,
                                                                           ALLOW_HORIZONTAL_WHITESPACE_CLASS,
                                                                           UNICODE_CATEGORY_SHORTHAND,
                                                                           EXTENDED_UNICODE_CHARACTER,
                                                                           PROPERTY_VALUES);
}
