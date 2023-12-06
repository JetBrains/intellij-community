// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.util.NlsSafe;

/**
 * Represents a Java keyword. Constants defined in this interface represent all keywords and literals of the Java language.
 */
public interface PsiKeyword extends PsiJavaToken {
  @NlsSafe String ABSTRACT = "abstract";
  @NlsSafe String ASSERT = "assert";
  @NlsSafe String BOOLEAN = "boolean";
  @NlsSafe String BREAK = "break";
  @NlsSafe String BYTE = "byte";
  @NlsSafe String CASE = "case";
  @NlsSafe String CATCH = "catch";
  @NlsSafe String CHAR = "char";
  @NlsSafe String CLASS = "class";
  @NlsSafe String CONST = "const";
  @NlsSafe String CONTINUE = "continue";
  @NlsSafe String DEFAULT = "default";
  @NlsSafe String DO = "do";
  @NlsSafe String DOUBLE = "double";
  @NlsSafe String ELSE = "else";
  @NlsSafe String ENUM = "enum";
  @NlsSafe String EXTENDS = "extends";
  @NlsSafe String FINAL = "final";
  @NlsSafe String FINALLY = "finally";
  @NlsSafe String FLOAT = "float";
  @NlsSafe String FOR = "for";
  @NlsSafe String GOTO = "goto";
  @NlsSafe String IF = "if";
  @NlsSafe String IMPLEMENTS = "implements";
  @NlsSafe String IMPORT = "import";
  @NlsSafe String INSTANCEOF = "instanceof";
  @NlsSafe String INT = "int";
  @NlsSafe String INTERFACE = "interface";
  @NlsSafe String LONG = "long";
  @NlsSafe String NATIVE = "native";
  @NlsSafe String NEW = "new";
  @NlsSafe String PACKAGE = "package";
  @NlsSafe String PRIVATE = "private";
  @NlsSafe String PROTECTED = "protected";
  @NlsSafe String PUBLIC = "public";
  @NlsSafe String RETURN = "return";
  @NlsSafe String SHORT = "short";
  @NlsSafe String STATIC = "static";
  @NlsSafe String STRICTFP = "strictfp";
  @NlsSafe String SUPER = "super";
  @NlsSafe String SWITCH = "switch";
  @NlsSafe String SYNCHRONIZED = "synchronized";
  @NlsSafe String THIS = "this";
  @NlsSafe String THROW = "throw";
  @NlsSafe String THROWS = "throws";
  @NlsSafe String TRANSIENT = "transient";
  @NlsSafe String TRY = "try";
  @NlsSafe String VOID = "void";
  @NlsSafe String VOLATILE = "volatile";
  @NlsSafe String WHILE = "while";

  @NlsSafe String TRUE = "true";
  @NlsSafe String FALSE = "false";
  @NlsSafe String NULL = "null";

  @NlsSafe String OPEN = "open";
  @NlsSafe String MODULE = "module";
  @NlsSafe String REQUIRES = "requires";
  @NlsSafe String EXPORTS = "exports";
  @NlsSafe String OPENS = "opens";
  @NlsSafe String USES = "uses";
  @NlsSafe String PROVIDES = "provides";
  @NlsSafe String TRANSITIVE = "transitive";
  @NlsSafe String TO = "to";
  @NlsSafe String WITH = "with";

  @NlsSafe String VAR = "var";
  @NlsSafe String YIELD = "yield";

  @NlsSafe String RECORD = "record";

  @NlsSafe String SEALED = "sealed";
  @NlsSafe String PERMITS = "permits";
  @NlsSafe String NON_SEALED = "non-sealed";
  @NlsSafe String WHEN = "when";
}