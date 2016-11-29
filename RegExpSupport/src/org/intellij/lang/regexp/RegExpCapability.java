/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

/**
 * @author yole
 */
public enum RegExpCapability {
  XML_SCHEMA_MODE,
  DANGLING_METACHARACTERS,
  NESTED_CHARACTER_CLASSES,

  /**
   * supports three-digit octal numbers not started with 0 (e.g. \123), 
   * if false \1 from \123 will be considered as a group back reference
   */
  OCTAL_NO_LEADING_ZERO,

  OMIT_NUMBERS_IN_QUANTIFIERS,
  COMMENT_MODE,
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
  CARET_NEGATED_PROPERTIES
}
