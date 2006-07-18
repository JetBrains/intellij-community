/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.psi;

/**
 * Represents a Java keyword. Constants defined in this interface represent all
 * keywords of the Java language.
 */
@SuppressWarnings({"HardCodedStringLiteral", "JavaDoc"})
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
  String SUPER = "super";
  String SWITCH = "switch";
  String SYNCHRONIZED = "synchronized";
  String THIS = "this";
  String THROW = "throw";
  String THROWS = "throws";
  String TRANSIENT = "transient";
  String TRY = "try";
  String VOID = "void";
  String STATIC = "static";
  String STRICTFP = "strictfp";
  String WHILE = "while";
  String VOLATILE = "volatile";

  String TRUE = "true";
  String FALSE = "false";
  String NULL = "null";
}
