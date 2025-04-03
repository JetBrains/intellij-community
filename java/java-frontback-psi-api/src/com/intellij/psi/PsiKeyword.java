// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.util.NlsSafe;

/**
 * Represents a Java keyword.
 * <p/>
 * For constants representing all keywords and literals of the Java language use {@link com.intellij.java.syntax.parser.JavaKeywords}
 */
public interface PsiKeyword extends PsiJavaToken {
  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String ABSTRACT = "abstract";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String ASSERT = "assert";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String BOOLEAN = "boolean";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String BREAK = "break";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String BYTE = "byte";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String CASE = "case";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String CATCH = "catch";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String CHAR = "char";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String CLASS = "class";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String CONST = "const";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String CONTINUE = "continue";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String DEFAULT = "default";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String DO = "do";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String DOUBLE = "double";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String ELSE = "else";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String ENUM = "enum";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String EXTENDS = "extends";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String FINAL = "final";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String FINALLY = "finally";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String FLOAT = "float";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String FOR = "for";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String GOTO = "goto";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String IF = "if";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String IMPLEMENTS = "implements";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String IMPORT = "import";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String INSTANCEOF = "instanceof";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String INT = "int";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String INTERFACE = "interface";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String LONG = "long";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String NATIVE = "native";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String NEW = "new";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String PACKAGE = "package";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String PRIVATE = "private";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String PROTECTED = "protected";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String PUBLIC = "public";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String RETURN = "return";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String SHORT = "short";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String STATIC = "static";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String STRICTFP = "strictfp";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String SUPER = "super";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String SWITCH = "switch";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String SYNCHRONIZED = "synchronized";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String THIS = "this";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String THROW = "throw";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String THROWS = "throws";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String TRANSIENT = "transient";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String TRY = "try";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String VOID = "void";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String VOLATILE = "volatile";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String WHILE = "while";


  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String TRUE = "true";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String FALSE = "false";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String NULL = "null";


  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String NON_SEALED = "non-sealed";

  // soft keywords:

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String EXPORTS = "exports";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String MODULE = "module";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String OPEN = "open";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String OPENS = "opens";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String PERMITS = "permits";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String PROVIDES = "provides";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String RECORD = "record";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String REQUIRES = "requires";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String SEALED = "sealed";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String TO = "to";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String TRANSITIVE = "transitive";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String USES = "uses";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String VALUE = "value";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String VAR = "var";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String WHEN = "when";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String WITH = "with";

  /**
   * @deprecated Use {@link com.intellij.java.syntax.parser.JavaKeywords} instead
   */
  @Deprecated @NlsSafe String YIELD = "yield";
}