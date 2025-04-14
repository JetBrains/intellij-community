// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.util.NlsSafe;

/**
 * Represents a Java keyword.
 * <p/>
 * For constants representing all keywords and literals of the Java language use {@link JavaKeywords}
 */
public interface PsiKeyword extends PsiJavaToken {
  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String ABSTRACT = JavaKeywords.ABSTRACT;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String ASSERT = JavaKeywords.ASSERT;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String BOOLEAN = JavaKeywords.BOOLEAN;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String BREAK = JavaKeywords.BREAK;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String BYTE = JavaKeywords.BYTE;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String CASE = JavaKeywords.CASE;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String CATCH = JavaKeywords.CATCH;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String CHAR = JavaKeywords.CHAR;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String CLASS = JavaKeywords.CLASS;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String CONST = JavaKeywords.CONST;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String CONTINUE = JavaKeywords.CONTINUE;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String DEFAULT = JavaKeywords.DEFAULT;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String DO = JavaKeywords.DO;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String DOUBLE = JavaKeywords.DOUBLE;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String ELSE = JavaKeywords.ELSE;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String ENUM = JavaKeywords.ENUM;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String EXTENDS = JavaKeywords.EXTENDS;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String FINAL = JavaKeywords.FINAL;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String FINALLY = JavaKeywords.FINALLY;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String FLOAT = JavaKeywords.FLOAT;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String FOR = JavaKeywords.FOR;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String GOTO = JavaKeywords.GOTO;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String IF = JavaKeywords.IF;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String IMPLEMENTS = JavaKeywords.IMPLEMENTS;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String IMPORT = JavaKeywords.IMPORT;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String INSTANCEOF = JavaKeywords.INSTANCEOF;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String INT = JavaKeywords.INT;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String INTERFACE = JavaKeywords.INTERFACE;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String LONG = JavaKeywords.LONG;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String NATIVE = JavaKeywords.NATIVE;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String NEW = JavaKeywords.NEW;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String PACKAGE = JavaKeywords.PACKAGE;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String PRIVATE = JavaKeywords.PRIVATE;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String PROTECTED = JavaKeywords.PROTECTED;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String PUBLIC = JavaKeywords.PUBLIC;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String RETURN = JavaKeywords.RETURN;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String SHORT = JavaKeywords.SHORT;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String STATIC = JavaKeywords.STATIC;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String STRICTFP = JavaKeywords.STRICTFP;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String SUPER = JavaKeywords.SUPER;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String SWITCH = JavaKeywords.SWITCH;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String SYNCHRONIZED = JavaKeywords.SYNCHRONIZED;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String THIS = JavaKeywords.THIS;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String THROW = JavaKeywords.THROW;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String THROWS = JavaKeywords.THROWS;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String TRANSIENT = JavaKeywords.TRANSIENT;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String TRY = JavaKeywords.TRY;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String VOID = JavaKeywords.VOID;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String VOLATILE = JavaKeywords.VOLATILE;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String WHILE = JavaKeywords.WHILE;


  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String TRUE = JavaKeywords.TRUE;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String FALSE = JavaKeywords.FALSE;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String NULL = JavaKeywords.NULL;


  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String NON_SEALED = JavaKeywords.NON_SEALED;

  // soft keywords:

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String EXPORTS = JavaKeywords.EXPORTS;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String MODULE = JavaKeywords.MODULE;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String OPEN = JavaKeywords.OPEN;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String OPENS = JavaKeywords.OPENS;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String PERMITS = JavaKeywords.PERMITS;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String PROVIDES = JavaKeywords.PROVIDES;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String RECORD = JavaKeywords.RECORD;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String REQUIRES = JavaKeywords.REQUIRES;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String SEALED = JavaKeywords.SEALED;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String TO = JavaKeywords.TO;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String TRANSITIVE = JavaKeywords.TRANSITIVE;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String USES = JavaKeywords.USES;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String VALUE = JavaKeywords.VALUE;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String VAR = JavaKeywords.VAR;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String WHEN = JavaKeywords.WHEN;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String WITH = JavaKeywords.WITH;

  /**
   * @deprecated Use {@link JavaKeywords} instead
   */
  @Deprecated @NlsSafe String YIELD = JavaKeywords.YIELD;
}