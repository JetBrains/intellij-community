// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.syntax

import com.intellij.platform.syntax.SyntaxElementType
import kotlin.jvm.JvmField

object JsonSyntaxElementTypes {
  @JvmField
  val ARRAY = SyntaxElementType("ARRAY")
  @JvmField
  val BOOLEAN_LITERAL = SyntaxElementType("BOOLEAN_LITERAL")
  @JvmField
  val LITERAL = SyntaxElementType("LITERAL")
  @JvmField
  val NULL_LITERAL = SyntaxElementType("NULL_LITERAL")
  @JvmField
  val NUMBER_LITERAL = SyntaxElementType("NUMBER_LITERAL")
  @JvmField
  val OBJECT = SyntaxElementType("OBJECT")
  @JvmField
  val PROPERTY = SyntaxElementType("PROPERTY")
  @JvmField
  val REFERENCE_EXPRESSION = SyntaxElementType("REFERENCE_EXPRESSION")
  @JvmField
  val STRING_LITERAL = SyntaxElementType("STRING_LITERAL")
  @JvmField
  val VALUE = SyntaxElementType("VALUE")

  @JvmField
  val BLOCK_COMMENT = SyntaxElementType("BLOCK_COMMENT")
  @JvmField
  val COLON = SyntaxElementType(":")
  @JvmField
  val COMMA = SyntaxElementType(",")
  @JvmField
  val DOUBLE_QUOTED_STRING = SyntaxElementType("DOUBLE_QUOTED_STRING")
  @JvmField
  val FALSE = SyntaxElementType("false")
  @JvmField
  val IDENTIFIER = SyntaxElementType("IDENTIFIER")
  @JvmField
  val LINE_COMMENT = SyntaxElementType("LINE_COMMENT")
  @JvmField
  val L_BRACKET = SyntaxElementType("[")
  @JvmField
  val L_CURLY = SyntaxElementType("{")
  @JvmField
  val NULL = SyntaxElementType("null")
  @JvmField
  val NUMBER = SyntaxElementType("NUMBER")
  @JvmField
  val R_BRACKET = SyntaxElementType("]")
  @JvmField
  val R_CURLY = SyntaxElementType("}")
  @JvmField
  val SINGLE_QUOTED_STRING = SyntaxElementType("SINGLE_QUOTED_STRING")
  @JvmField
  val TRUE = SyntaxElementType("true")
}