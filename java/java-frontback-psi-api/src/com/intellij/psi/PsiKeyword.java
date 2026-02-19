// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.ApiStatus;

/**
 * Represents a Java keyword.
 * <p/>
 * For constants representing all keywords and literals of the Java language use {@link JavaKeywords}
 */
public interface PsiKeyword extends PsiJavaToken {
  /**
   * @deprecated Use {@link JavaKeywords#ABSTRACT} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String ABSTRACT = JavaKeywords.ABSTRACT;

  /**
   * @deprecated Use {@link JavaKeywords#ASSERT} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String ASSERT = JavaKeywords.ASSERT;

  /**
   * @deprecated Use {@link JavaKeywords#BOOLEAN} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String BOOLEAN = JavaKeywords.BOOLEAN;

  /**
   * @deprecated Use {@link JavaKeywords#BREAK} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String BREAK = JavaKeywords.BREAK;

  /**
   * @deprecated Use {@link JavaKeywords#BYTE} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String BYTE = JavaKeywords.BYTE;

  /**
   * @deprecated Use {@link JavaKeywords#CASE} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String CASE = JavaKeywords.CASE;

  /**
   * @deprecated Use {@link JavaKeywords#CATCH} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String CATCH = JavaKeywords.CATCH;

  /**
   * @deprecated Use {@link JavaKeywords#CHAR} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String CHAR = JavaKeywords.CHAR;

  /**
   * @deprecated Use {@link JavaKeywords#CLASS} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String CLASS = JavaKeywords.CLASS;

  /**
   * @deprecated Use {@link JavaKeywords#CONST} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String CONST = JavaKeywords.CONST;

  /**
   * @deprecated Use {@link JavaKeywords#CONTINUE} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String CONTINUE = JavaKeywords.CONTINUE;

  /**
   * @deprecated Use {@link JavaKeywords#DEFAULT} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String DEFAULT = JavaKeywords.DEFAULT;

  /**
   * @deprecated Use {@link JavaKeywords#DO} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String DO = JavaKeywords.DO;

  /**
   * @deprecated Use {@link JavaKeywords#DOUBLE} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String DOUBLE = JavaKeywords.DOUBLE;

  /**
   * @deprecated Use {@link JavaKeywords#ELSE} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String ELSE = JavaKeywords.ELSE;

  /**
   * @deprecated Use {@link JavaKeywords#ENUM} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String ENUM = JavaKeywords.ENUM;

  /**
   * @deprecated Use {@link JavaKeywords#EXTENDS} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String EXTENDS = JavaKeywords.EXTENDS;

  /**
   * @deprecated Use {@link JavaKeywords#FINAL} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String FINAL = JavaKeywords.FINAL;

  /**
   * @deprecated Use {@link JavaKeywords#FINALLY} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String FINALLY = JavaKeywords.FINALLY;

  /**
   * @deprecated Use {@link JavaKeywords#FLOAT} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String FLOAT = JavaKeywords.FLOAT;

  /**
   * @deprecated Use {@link JavaKeywords#FOR} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String FOR = JavaKeywords.FOR;

  /**
   * @deprecated Use {@link JavaKeywords#GOTO} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String GOTO = JavaKeywords.GOTO;

  /**
   * @deprecated Use {@link JavaKeywords#IF} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String IF = JavaKeywords.IF;

  /**
   * @deprecated Use {@link JavaKeywords#IMPLEMENTS} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String IMPLEMENTS = JavaKeywords.IMPLEMENTS;

  /**
   * @deprecated Use {@link JavaKeywords#IMPORT} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String IMPORT = JavaKeywords.IMPORT;

  /**
   * @deprecated Use {@link JavaKeywords#INSTANCEOF} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String INSTANCEOF = JavaKeywords.INSTANCEOF;

  /**
   * @deprecated Use {@link JavaKeywords#INT} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String INT = JavaKeywords.INT;

  /**
   * @deprecated Use {@link JavaKeywords#INTERFACE} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String INTERFACE = JavaKeywords.INTERFACE;

  /**
   * @deprecated Use {@link JavaKeywords#LONG} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String LONG = JavaKeywords.LONG;

  /**
   * @deprecated Use {@link JavaKeywords#NATIVE} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String NATIVE = JavaKeywords.NATIVE;

  /**
   * @deprecated Use {@link JavaKeywords#NEW} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String NEW = JavaKeywords.NEW;

  /**
   * @deprecated Use {@link JavaKeywords#PACKAGE} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String PACKAGE = JavaKeywords.PACKAGE;

  /**
   * @deprecated Use {@link JavaKeywords#PRIVATE} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String PRIVATE = JavaKeywords.PRIVATE;

  /**
   * @deprecated Use {@link JavaKeywords#PROTECTED} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String PROTECTED = JavaKeywords.PROTECTED;

  /**
   * @deprecated Use {@link JavaKeywords#PUBLIC} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String PUBLIC = JavaKeywords.PUBLIC;

  /**
   * @deprecated Use {@link JavaKeywords#RETURN} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String RETURN = JavaKeywords.RETURN;

  /**
   * @deprecated Use {@link JavaKeywords#SHORT} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String SHORT = JavaKeywords.SHORT;

  /**
   * @deprecated Use {@link JavaKeywords#STATIC} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String STATIC = JavaKeywords.STATIC;

  /**
   * @deprecated Use {@link JavaKeywords#STRICTFP} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String STRICTFP = JavaKeywords.STRICTFP;

  /**
   * @deprecated Use {@link JavaKeywords#SUPER} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String SUPER = JavaKeywords.SUPER;

  /**
   * @deprecated Use {@link JavaKeywords#SWITCH} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String SWITCH = JavaKeywords.SWITCH;

  /**
   * @deprecated Use {@link JavaKeywords#SYNCHRONIZED} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String SYNCHRONIZED = JavaKeywords.SYNCHRONIZED;

  /**
   * @deprecated Use {@link JavaKeywords#THIS} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String THIS = JavaKeywords.THIS;

  /**
   * @deprecated Use {@link JavaKeywords#THROW} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String THROW = JavaKeywords.THROW;

  /**
   * @deprecated Use {@link JavaKeywords#THROWS} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String THROWS = JavaKeywords.THROWS;

  /**
   * @deprecated Use {@link JavaKeywords#TRANSIENT} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String TRANSIENT = JavaKeywords.TRANSIENT;

  /**
   * @deprecated Use {@link JavaKeywords#TRY} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String TRY = JavaKeywords.TRY;

  /**
   * @deprecated Use {@link JavaKeywords#VOID} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String VOID = JavaKeywords.VOID;

  /**
   * @deprecated Use {@link JavaKeywords#VOLATILE} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String VOLATILE = JavaKeywords.VOLATILE;

  /**
   * @deprecated Use {@link JavaKeywords#WHILE} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String WHILE = JavaKeywords.WHILE;


  /**
   * @deprecated Use {@link JavaKeywords#TRUE} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String TRUE = JavaKeywords.TRUE;

  /**
   * @deprecated Use {@link JavaKeywords#FALSE} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String FALSE = JavaKeywords.FALSE;

  /**
   * @deprecated Use {@link JavaKeywords#NULL} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String NULL = JavaKeywords.NULL;


  /**
   * @deprecated Use {@link JavaKeywords#NON_SEALED} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String NON_SEALED = JavaKeywords.NON_SEALED;

  // soft keywords:

  /**
   * @deprecated Use {@link JavaKeywords#EXPORTS} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String EXPORTS = JavaKeywords.EXPORTS;

  /**
   * @deprecated Use {@link JavaKeywords#MODULE} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String MODULE = JavaKeywords.MODULE;

  /**
   * @deprecated Use {@link JavaKeywords#OPEN} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String OPEN = JavaKeywords.OPEN;

  /**
   * @deprecated Use {@link JavaKeywords#OPENS} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String OPENS = JavaKeywords.OPENS;

  /**
   * @deprecated Use {@link JavaKeywords#PERMITS} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String PERMITS = JavaKeywords.PERMITS;

  /**
   * @deprecated Use {@link JavaKeywords#PROVIDES} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String PROVIDES = JavaKeywords.PROVIDES;

  /**
   * @deprecated Use {@link JavaKeywords#RECORD} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String RECORD = JavaKeywords.RECORD;

  /**
   * @deprecated Use {@link JavaKeywords#REQUIRES} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String REQUIRES = JavaKeywords.REQUIRES;

  /**
   * @deprecated Use {@link JavaKeywords#SEALED} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String SEALED = JavaKeywords.SEALED;

  /**
   * @deprecated Use {@link JavaKeywords#TO} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String TO = JavaKeywords.TO;

  /**
   * @deprecated Use {@link JavaKeywords#TRANSITIVE} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String TRANSITIVE = JavaKeywords.TRANSITIVE;

  /**
   * @deprecated Use {@link JavaKeywords#USES} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String USES = JavaKeywords.USES;

  /**
   * @deprecated Use {@link JavaKeywords#VALUE} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String VALUE = JavaKeywords.VALUE;

  /**
   * @deprecated Use {@link JavaKeywords#VAR} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String VAR = JavaKeywords.VAR;

  /**
   * @deprecated Use {@link JavaKeywords#WHEN} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String WHEN = JavaKeywords.WHEN;

  /**
   * @deprecated Use {@link JavaKeywords#WITH} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String WITH = JavaKeywords.WITH;

  /**
   * @deprecated Use {@link JavaKeywords#YIELD} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval @NlsSafe String YIELD = JavaKeywords.YIELD;
}