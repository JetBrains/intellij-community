// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.syntax

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder.Marker
import com.intellij.platform.syntax.util.runtime.*
import com.intellij.platform.syntax.util.runtime.Modifiers.Companion._AND_
import com.intellij.platform.syntax.util.runtime.Modifiers.Companion._COLLAPSE_
import com.intellij.platform.syntax.util.runtime.Modifiers.Companion._NONE_
import com.intellij.platform.syntax.util.runtime.Modifiers.Companion._NOT_

@Suppress("unused", "FunctionName", "JoinDeclarationAndAssignment")
class JsonSyntaxParser {

  fun parse(t: SyntaxElementType, s: SyntaxGeneratedParserRuntime) {
    var r: Boolean
    s.init(::parse, EXTENDS_SETS_)
    val m: Marker = s.enter_section_(0, _COLLAPSE_, null)
    r = parse_root_(t, s)
    s.exit_section_(0, m, t, r, true, TRUE_CONDITION)
  }

  protected fun parse_root_(t: SyntaxElementType, s: SyntaxGeneratedParserRuntime): Boolean {
    return parse_root_(t, s, 0)
  }

  companion object {
    internal fun parse_root_(t: SyntaxElementType, s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
      return json(s, l + 1)
    }

    val EXTENDS_SETS_: Array<SyntaxElementTypeSet> = arrayOf(
      create_token_set_(JsonSyntaxElementTypes.ARRAY, JsonSyntaxElementTypes.BOOLEAN_LITERAL, JsonSyntaxElementTypes.LITERAL, JsonSyntaxElementTypes.NULL_LITERAL,
                        JsonSyntaxElementTypes.NUMBER_LITERAL, JsonSyntaxElementTypes.OBJECT, JsonSyntaxElementTypes.REFERENCE_EXPRESSION, JsonSyntaxElementTypes.STRING_LITERAL,
                        JsonSyntaxElementTypes.VALUE),
    )

    /* ********************************************************** */
    // '[' array_element* ']'
    fun array(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
      if (!s.recursion_guard_(l, "array")) return false
      if (!s.nextTokenIs(JsonSyntaxElementTypes.L_BRACKET)) return false
      var r: Boolean
      var p: Boolean
      val m: Marker = s.enter_section_(l, _NONE_, JsonSyntaxElementTypes.ARRAY, null)
      r = s.consumeToken(JsonSyntaxElementTypes.L_BRACKET)
      p = r // pin = 1
      r = r && s.report_error_(array_1(s, l + 1))
      r = p && s.consumeToken(JsonSyntaxElementTypes.R_BRACKET) && r
      s.exit_section_(l, m, r, p, null)
      return r || p
    }

    // array_element*
    private fun array_1(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
      if (!s.recursion_guard_(l, "array_1")) return false
      while (true) {
        val c: Int = s.current_position_()
        if (!array_element(s, l + 1)) break
        if (!s.empty_element_parsed_guard_("array_1", c)) break
      }
      return true
    }

    /* ********************************************************** */
    // value (','|&']')
    internal fun array_element(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
      if (!s.recursion_guard_(l, "array_element")) return false
      var r: Boolean
      var p: Boolean
      val m: Marker = s.enter_section_(l, _NONE_)
      r = `value_$`(s, l + 1)
      p = r // pin = 1
      r = r && array_element_1(s, l + 1)
      s.exit_section_(l, m, r, p, JsonSyntaxParser::not_bracket_or_next_value)
      return r || p
    }

    // ','|&']'
    private fun array_element_1(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
      if (!s.recursion_guard_(l, "array_element_1")) return false
      var r: Boolean
      val m: Marker = s.enter_section_()
      r = s.consumeToken(JsonSyntaxElementTypes.COMMA)
      if (!r) r = array_element_1_1(s, l + 1)
      s.exit_section_(m, null, r)
      return r
    }

    // &']'
    private fun array_element_1_1(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
      if (!s.recursion_guard_(l, "array_element_1_1")) return false
      var r: Boolean
      val m: Marker = s.enter_section_(l, _AND_)
      r = s.consumeToken(JsonSyntaxElementTypes.R_BRACKET)
      s.exit_section_(l, m, r, false, null)
      return r
    }

    /* ********************************************************** */
    // TRUE | FALSE
    fun boolean_literal(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
      if (!s.recursion_guard_(l, "boolean_literal")) return false
      if (!s.nextTokenIs("<boolean literal>", JsonSyntaxElementTypes.FALSE, JsonSyntaxElementTypes.TRUE)) return false
      var r: Boolean
      val m: Marker = s.enter_section_(l, _NONE_, JsonSyntaxElementTypes.BOOLEAN_LITERAL, "<boolean literal>")
      r = s.consumeToken(JsonSyntaxElementTypes.TRUE)
      if (!r) r = s.consumeToken(JsonSyntaxElementTypes.FALSE)
      s.exit_section_(l, m, r, false, null)
      return r
    }

    /* ********************************************************** */
    // value*
    internal fun json(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
      if (!s.recursion_guard_(l, "json")) return false
      while (true) {
        val c: Int = s.current_position_()
        if (!`value_$`(s, l + 1)) break
        if (!s.empty_element_parsed_guard_("json", c)) break
      }
      return true
    }

    /* ********************************************************** */
    // string_literal | number_literal | boolean_literal | null_literal
    fun literal(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
      if (!s.recursion_guard_(l, "literal")) return false
      var r: Boolean
      val m: Marker = s.enter_section_(l, _COLLAPSE_, JsonSyntaxElementTypes.LITERAL, "<literal>")
      r = string_literal(s, l + 1)
      if (!r) r = number_literal(s, l + 1)
      if (!r) r = boolean_literal(s, l + 1)
      if (!r) r = null_literal(s, l + 1)
      s.exit_section_(l, m, r, false, null)
      return r
    }

    /* ********************************************************** */
    // !('}'|value)
    internal fun not_brace_or_next_value(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
      if (!s.recursion_guard_(l, "not_brace_or_next_value")) return false
      var r: Boolean
      val m: Marker = s.enter_section_(l, _NOT_)
      r = !not_brace_or_next_value_0(s, l + 1)
      s.exit_section_(l, m, r, false, null)
      return r
    }

    // '}'|value
    private fun not_brace_or_next_value_0(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
      if (!s.recursion_guard_(l, "not_brace_or_next_value_0")) return false
      var r: Boolean
      r = s.consumeToken(JsonSyntaxElementTypes.R_CURLY)
      if (!r) r = `value_$`(s, l + 1)
      return r
    }

    /* ********************************************************** */
    // !(']'|value)
    internal fun not_bracket_or_next_value(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
      if (!s.recursion_guard_(l, "not_bracket_or_next_value")) return false
      var r: Boolean
      val m: Marker = s.enter_section_(l, _NOT_)
      r = !not_bracket_or_next_value_0(s, l + 1)
      s.exit_section_(l, m, r, false, null)
      return r
    }

    // ']'|value
    private fun not_bracket_or_next_value_0(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
      if (!s.recursion_guard_(l, "not_bracket_or_next_value_0")) return false
      var r: Boolean
      r = s.consumeToken(JsonSyntaxElementTypes.R_BRACKET)
      if (!r) r = `value_$`(s, l + 1)
      return r
    }

    /* ********************************************************** */
    // NULL
    fun null_literal(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
      if (!s.recursion_guard_(l, "null_literal")) return false
      if (!s.nextTokenIs(JsonSyntaxElementTypes.NULL)) return false
      var r: Boolean
      val m: Marker = s.enter_section_()
      r = s.consumeToken(JsonSyntaxElementTypes.NULL)
      s.exit_section_(m, JsonSyntaxElementTypes.NULL_LITERAL, r)
      return r
    }

    /* ********************************************************** */
    // NUMBER
    fun number_literal(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
      if (!s.recursion_guard_(l, "number_literal")) return false
      if (!s.nextTokenIs(JsonSyntaxElementTypes.NUMBER)) return false
      var r: Boolean
      val m: Marker = s.enter_section_()
      r = s.consumeToken(JsonSyntaxElementTypes.NUMBER)
      s.exit_section_(m, JsonSyntaxElementTypes.NUMBER_LITERAL, r)
      return r
    }

    /* ********************************************************** */
    // '{' object_element* '}'
    fun `object_$`(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
      if (!s.recursion_guard_(l, "`object_$`")) return false
      if (!s.nextTokenIs(JsonSyntaxElementTypes.L_CURLY)) return false
      var r: Boolean
      var p: Boolean
      val m: Marker = s.enter_section_(l, _NONE_, JsonSyntaxElementTypes.OBJECT, null)
      r = s.consumeToken(JsonSyntaxElementTypes.L_CURLY)
      p = r // pin = 1
      r = r && s.report_error_(object_1(s, l + 1))
      r = p && s.consumeToken(JsonSyntaxElementTypes.R_CURLY) && r
      s.exit_section_(l, m, r, p, null)
      return r || p
    }

    // object_element*
    private fun object_1(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
      if (!s.recursion_guard_(l, "object_1")) return false
      while (true) {
        val c: Int = s.current_position_()
        if (!object_element(s, l + 1)) break
        if (!s.empty_element_parsed_guard_("object_1", c)) break
      }
      return true
    }

    /* ********************************************************** */
    // property (','|&'}')
    internal fun object_element(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
      if (!s.recursion_guard_(l, "object_element")) return false
      var r: Boolean
      var p: Boolean
      val m: Marker = s.enter_section_(l, _NONE_)
      r = `property_$`(s, l + 1)
      p = r // pin = 1
      r = r && object_element_1(s, l + 1)
      s.exit_section_(l, m, r, p, JsonSyntaxParser::not_brace_or_next_value)
      return r || p
    }

    // ','|&'}'
    private fun object_element_1(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
      if (!s.recursion_guard_(l, "object_element_1")) return false
      var r: Boolean
      val m: Marker = s.enter_section_()
      r = s.consumeToken(JsonSyntaxElementTypes.COMMA)
      if (!r) r = object_element_1_1(s, l + 1)
      s.exit_section_(m, null, r)
      return r
    }

    // &'}'
    private fun object_element_1_1(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
      if (!s.recursion_guard_(l, "object_element_1_1")) return false
      var r: Boolean
      val m: Marker = s.enter_section_(l, _AND_)
      r = s.consumeToken(JsonSyntaxElementTypes.R_CURLY)
      s.exit_section_(l, m, r, false, null)
      return r
    }

    /* ********************************************************** */
    // property_name (':' property_value)
    fun `property_$`(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
      if (!s.recursion_guard_(l, "`property_$`")) return false
      var r: Boolean
      var p: Boolean
      val m: Marker = s.enter_section_(l, _NONE_, JsonSyntaxElementTypes.PROPERTY, "<property>")
      r = property_name(s, l + 1)
      p = r // pin = 1
      r = r && property_1(s, l + 1)
      s.exit_section_(l, m, r, p, null)
      return r || p
    }

    // ':' property_value
    private fun property_1(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
      if (!s.recursion_guard_(l, "property_1")) return false
      var r: Boolean
      var p: Boolean
      val m: Marker = s.enter_section_(l, _NONE_)
      r = s.consumeToken(JsonSyntaxElementTypes.COLON)
      p = r // pin = 1
      r = r && property_value(s, l + 1)
      s.exit_section_(l, m, r, p, null)
      return r || p
    }

    /* ********************************************************** */
    // literal | reference_expression
    internal fun property_name(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
      if (!s.recursion_guard_(l, "property_name")) return false
      var r: Boolean
      r = literal(s, l + 1)
      if (!r) r = reference_expression(s, l + 1)
      return r
    }

    /* ********************************************************** */
    // value
    internal fun property_value(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
      return `value_$`(s, l + 1)
    }

    /* ********************************************************** */
    // IDENTIFIER
    fun reference_expression(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
      if (!s.recursion_guard_(l, "reference_expression")) return false
      if (!s.nextTokenIs(JsonSyntaxElementTypes.IDENTIFIER)) return false
      var r: Boolean
      val m: Marker = s.enter_section_()
      r = s.consumeToken(JsonSyntaxElementTypes.IDENTIFIER)
      s.exit_section_(m, JsonSyntaxElementTypes.REFERENCE_EXPRESSION, r)
      return r
    }

    /* ********************************************************** */
    // SINGLE_QUOTED_STRING | DOUBLE_QUOTED_STRING
    fun string_literal(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
      if (!s.recursion_guard_(l, "string_literal")) return false
      if (!s.nextTokenIs("<string literal>", JsonSyntaxElementTypes.DOUBLE_QUOTED_STRING, JsonSyntaxElementTypes.SINGLE_QUOTED_STRING)) return false
      var r: Boolean
      val m: Marker = s.enter_section_(l, _NONE_, JsonSyntaxElementTypes.STRING_LITERAL, "<string literal>")
      r = s.consumeToken(JsonSyntaxElementTypes.SINGLE_QUOTED_STRING)
      if (!r) r = s.consumeToken(JsonSyntaxElementTypes.DOUBLE_QUOTED_STRING)
      s.exit_section_(l, m, r, false, null)
      return r
    }

    /* ********************************************************** */
    // object | array | literal | reference_expression
    fun `value_$`(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
      if (!s.recursion_guard_(l, "`value_$`")) return false
      var r: Boolean
      val m: Marker = s.enter_section_(l, _COLLAPSE_, JsonSyntaxElementTypes.VALUE, "<value>")
      r = `object_$`(s, l + 1)
      if (!r) r = array(s, l + 1)
      if (!r) r = literal(s, l + 1)
      if (!r) r = reference_expression(s, l + 1)
      s.exit_section_(l, m, r, false, null)
      return r
    }

  }
}
