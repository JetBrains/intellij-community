// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

/**
 * Represents a Java keyword. Constants defined in this interface represent all keywords and literals of the Java language.
 */
public interface PsiKeyword extends PsiJavaToken {
  String ABSTRACT = "abstract";
  String ASSERT = "assert";
  String BOOLEAN = "boolean";
  String BREAK = "break";
  String BYTE = "byte";
  String CASE = "case";
  String CATCH = "catch";
  String CHAR = "char";
  String CLASS = "class";
  String CONST = "const";
  String CONTINUE = "continue";
  String DEFAULT = "default";
  String DO = "do";
  String DOUBLE = "double";
  String ELSE = "else";
  String ENUM = "enum";
  String EXTENDS = "extends";
  String FINAL = "final";
  String FINALLY = "finally";
  String FLOAT = "float";
  String FOR = "for";
  String GOTO = "goto";
  String IF = "if";
  String IMPLEMENTS = "implements";
  String IMPORT = "import";
  String INSTANCEOF = "instanceof";
  String INT = "int";
  String INTERFACE = "interface";
  String LONG = "long";
  String NATIVE = "native";
  String NEW = "new";
  String PACKAGE = "package";
  String PRIVATE = "private";
  String PROTECTED = "protected";
  String PUBLIC = "public";
  String RETURN = "return";
  String SHORT = "short";
  String STATIC = "static";
  String STRICTFP = "strictfp";
  String SUPER = "super";
  String SWITCH = "switch";
  String SYNCHRONIZED = "synchronized";
  String THIS = "this";
  String THROW = "throw";
  String THROWS = "throws";
  String TRANSIENT = "transient";
  String TRY = "try";
  String VOID = "void";
  String VOLATILE = "volatile";
  String WHILE = "while";

  String TRUE = "true";
  String FALSE = "false";
  String NULL = "null";

  String OPEN = "open";
  String MODULE = "module";
  String REQUIRES = "requires";
  String EXPORTS = "exports";
  String OPENS = "opens";
  String USES = "uses";
  String PROVIDES = "provides";
  String TRANSITIVE = "transitive";
  String TO = "to";
  String WITH = "with";

  String VAR = "var";
  String YIELD = "yield";

  String RECORD = "record";
}