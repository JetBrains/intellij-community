// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// This is a generated file. Not intended for manual editing.
package com.intellij.json.syntax

import com.intellij.platform.syntax.util.runtime.*
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder.Marker
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.syntaxElementTypeSetOf
import com.intellij.platform.syntax.SyntaxElementType

@Suppress("unused", "FunctionName", "JoinDeclarationAndAssignment")
object JsonSyntaxParser {

  fun parse(t: SyntaxElementType, s: SyntaxGeneratedParserRuntime) {
    var r: Boolean
    s.init(::parse, EXTENDS_SETS_)
    val m: Marker = s.enter_section_(0, Modifiers._COLLAPSE_, null)
    r = parse_root_(t, s, 0)
    s.exit_section_(0, m, t, r, true, TRUE_CONDITION)
  }

  internal fun parse_root_(t: SyntaxElementType, s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    var r: Boolean
    if (t == JsonSyntaxElementTypes.OBJECT) {
      r = object__(s, l + 1)
    }
    else if (t == JsonSyntaxElementTypes.ARRAY) {
      r = array(s, l + 1)
    }
    else {
      r = json(s, l + 1)
    }
    return r
  }

  val EXTENDS_SETS_: Array<SyntaxElementTypeSet> = arrayOf(
    create_token_set_(JsonSyntaxElementTypes.ARRAY, JsonSyntaxElementTypes.BOOLEAN_LITERAL, JsonSyntaxElementTypes.LITERAL, JsonSyntaxElementTypes.NULL_LITERAL,
      JsonSyntaxElementTypes.NUMBER_LITERAL, JsonSyntaxElementTypes.OBJECT, JsonSyntaxElementTypes.REFERENCE_EXPRESSION, JsonSyntaxElementTypes.STRING_LITERAL,
      JsonSyntaxElementTypes.VALUE),
  )

  /* ********************************************************** */
  // '[' <<consumeArrayContentIfTooDeep>> array_element* array_leftovers ']'
  fun array(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "array")) return false
    if (!s.nextTokenIs(JsonSyntaxElementTypes.L_BRACKET)) return false
    var r: Boolean
    var p: Boolean
    val m: Marker = s.enter_section_(l, Modifiers._NONE_, JsonSyntaxElementTypes.ARRAY, null)
    r = s.consumeToken(JsonSyntaxElementTypes.L_BRACKET)
    p = r // pin = 1
    r = r && s.report_error_(consumeArrayContentIfTooDeep(s, l + 1))
    r = p && s.report_error_(array_2(s, l + 1)) && r
    r = p && s.report_error_(array_leftovers(s, l + 1)) && r
    r = p && s.consumeToken(JsonSyntaxElementTypes.R_BRACKET) && r
    s.exit_section_(l, m, r, p, null)
    return r || p
  }

  // array_element*
  private fun array_2(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "array_2")) return false
    while (true) {
      val c: Int = s.current_position_()
      if (!array_element(s, l + 1)) break
      if (!s.empty_element_parsed_guard_("array_2", c)) break
    }
    return true
  }

  /* ********************************************************** */
  // value_impl (','|&']')
  internal fun array_element(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "array_element")) return false
    var r: Boolean
    var p: Boolean
    val m: Marker = s.enter_section_(l, Modifiers._NONE_)
    r = value_impl(s, l + 1)
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
    val m: Marker = s.enter_section_(l, Modifiers._AND_)
    r = s.consumeToken(JsonSyntaxElementTypes.R_BRACKET)
    s.exit_section_(l, m, r, false, null)
    return r
  }

  /* ********************************************************** */
  // &']' | <<eof>> | <<leftoverErrorInArray>> leftover_value_inside_array*
  internal fun array_leftovers(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "array_leftovers")) return false
    var r: Boolean
    val m: Marker = s.enter_section_()
    r = array_leftovers_0(s, l + 1)
    if (!r) r = s.eof(l + 1)
    if (!r) r = array_leftovers_2(s, l + 1)
    s.exit_section_(m, null, r)
    return r
  }

  // &']'
  private fun array_leftovers_0(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "array_leftovers_0")) return false
    var r: Boolean
    val m: Marker = s.enter_section_(l, Modifiers._AND_)
    r = s.consumeToken(JsonSyntaxElementTypes.R_BRACKET)
    s.exit_section_(l, m, r, false, null)
    return r
  }

  // <<leftoverErrorInArray>> leftover_value_inside_array*
  private fun array_leftovers_2(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "array_leftovers_2")) return false
    var r: Boolean
    val m: Marker = s.enter_section_()
    r = leftoverErrorInArray(s, l + 1)
    r = r && array_leftovers_2_1(s, l + 1)
    s.exit_section_(m, null, r)
    return r
  }

  // leftover_value_inside_array*
  private fun array_leftovers_2_1(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "array_leftovers_2_1")) return false
    while (true) {
      val c: Int = s.current_position_()
      if (!leftover_value_inside_array(s, l + 1)) break
      if (!s.empty_element_parsed_guard_("array_leftovers_2_1", c)) break
    }
    return true
  }

  /* ********************************************************** */
  // TRUE | FALSE
  fun boolean_literal(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "boolean_literal")) return false
    if (!s.nextTokenIs("<boolean literal>", JsonSyntaxElementTypes.FALSE, JsonSyntaxElementTypes.TRUE)) return false
    var r: Boolean
    val m: Marker = s.enter_section_(l, Modifiers._NONE_, JsonSyntaxElementTypes.BOOLEAN_LITERAL, "<boolean literal>")
    r = s.consumeToken(JsonSyntaxElementTypes.TRUE)
    if (!r) r = s.consumeToken(JsonSyntaxElementTypes.FALSE)
    s.exit_section_(l, m, r, false, null)
    return r
  }

  /* ********************************************************** */
  // top_level_value*
  internal fun json(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "json")) return false
    while (true) {
      val c: Int = s.current_position_()
      if (!top_level_value(s, l + 1)) break
      if (!s.empty_element_parsed_guard_("json", c)) break
    }
    return true
  }

  /* ********************************************************** */
  // value_impl
  internal fun leftover_value_inside_array(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "leftover_value_inside_array")) return false
    var r: Boolean
    val m: Marker = s.enter_section_(l, Modifiers._NONE_)
    r = value_impl(s, l + 1)
    s.exit_section_(l, m, r, false, JsonSyntaxParser::leftover_value_inside_array_recoverer)
    return r
  }

  /* ********************************************************** */
  // !(value_start | ']')
  internal fun leftover_value_inside_array_recoverer(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "leftover_value_inside_array_recoverer")) return false
    var r: Boolean
    val m: Marker = s.enter_section_(l, Modifiers._NOT_)
    r = !leftover_value_inside_array_recoverer_0(s, l + 1)
    s.exit_section_(l, m, r, false, null)
    return r
  }

  // value_start | ']'
  private fun leftover_value_inside_array_recoverer_0(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "leftover_value_inside_array_recoverer_0")) return false
    var r: Boolean
    r = value_start(s, l + 1)
    if (!r) r = s.consumeToken(JsonSyntaxElementTypes.R_BRACKET)
    return r
  }

  /* ********************************************************** */
  // value_impl
  internal fun leftover_value_inside_object(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "leftover_value_inside_object")) return false
    var r: Boolean
    val m: Marker = s.enter_section_(l, Modifiers._NONE_)
    r = value_impl(s, l + 1)
    s.exit_section_(l, m, r, false, JsonSyntaxParser::leftover_value_inside_object_recoverer)
    return r
  }

  /* ********************************************************** */
  // !(value_start | '}')
  internal fun leftover_value_inside_object_recoverer(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "leftover_value_inside_object_recoverer")) return false
    var r: Boolean
    val m: Marker = s.enter_section_(l, Modifiers._NOT_)
    r = !leftover_value_inside_object_recoverer_0(s, l + 1)
    s.exit_section_(l, m, r, false, null)
    return r
  }

  // value_start | '}'
  private fun leftover_value_inside_object_recoverer_0(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "leftover_value_inside_object_recoverer_0")) return false
    var r: Boolean
    r = value_start(s, l + 1)
    if (!r) r = s.consumeToken(JsonSyntaxElementTypes.R_CURLY)
    return r
  }

  /* ********************************************************** */
  // string_literal | number_literal | boolean_literal | null_literal
  fun literal(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "literal")) return false
    var r: Boolean
    val m: Marker = s.enter_section_(l, Modifiers._COLLAPSE_, JsonSyntaxElementTypes.LITERAL, "<literal>")
    r = string_literal(s, l + 1)
    if (!r) r = number_literal(s, l + 1)
    if (!r) r = boolean_literal(s, l + 1)
    if (!r) r = null_literal(s, l + 1)
    s.exit_section_(l, m, r, false, null)
    return r
  }

  /* ********************************************************** */
  // !( '}' | value_start )
  internal fun not_brace_or_next_value(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "not_brace_or_next_value")) return false
    var r: Boolean
    val m: Marker = s.enter_section_(l, Modifiers._NOT_)
    r = !not_brace_or_next_value_0(s, l + 1)
    s.exit_section_(l, m, r, false, null)
    return r
  }

  // '}' | value_start
  private fun not_brace_or_next_value_0(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "not_brace_or_next_value_0")) return false
    var r: Boolean
    r = s.consumeToken(JsonSyntaxElementTypes.R_CURLY)
    if (!r) r = value_start(s, l + 1)
    return r
  }

  /* ********************************************************** */
  // !( ']' | '}' | value_start )
  internal fun not_bracket_or_next_value(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "not_bracket_or_next_value")) return false
    var r: Boolean
    val m: Marker = s.enter_section_(l, Modifiers._NOT_)
    r = !not_bracket_or_next_value_0(s, l + 1)
    s.exit_section_(l, m, r, false, null)
    return r
  }

  // ']' | '}' | value_start
  private fun not_bracket_or_next_value_0(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "not_bracket_or_next_value_0")) return false
    var r: Boolean
    r = s.consumeToken(JsonSyntaxElementTypes.R_BRACKET)
    if (!r) r = s.consumeToken(JsonSyntaxElementTypes.R_CURLY)
    if (!r) r = value_start(s, l + 1)
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
  // '{' <<consumeObjectContentIfTooDeep>> object_element* object_leftovers '}'
  fun object__(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "object__")) return false
    if (!s.nextTokenIs(JsonSyntaxElementTypes.L_CURLY)) return false
    var r: Boolean
    var p: Boolean
    val m: Marker = s.enter_section_(l, Modifiers._NONE_, JsonSyntaxElementTypes.OBJECT, null)
    r = s.consumeToken(JsonSyntaxElementTypes.L_CURLY)
    p = r // pin = 1
    r = r && s.report_error_(consumeObjectContentIfTooDeep(s, l + 1))
    r = p && s.report_error_(object_2(s, l + 1)) && r
    r = p && s.report_error_(object_leftovers(s, l + 1)) && r
    r = p && s.consumeToken(JsonSyntaxElementTypes.R_CURLY) && r
    s.exit_section_(l, m, r, p, null)
    return r || p
  }

  // object_element*
  private fun object_2(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "object_2")) return false
    while (true) {
      val c: Int = s.current_position_()
      if (!object_element(s, l + 1)) break
      if (!s.empty_element_parsed_guard_("object_2", c)) break
    }
    return true
  }

  /* ********************************************************** */
  // property (','|&'}')
  internal fun object_element(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "object_element")) return false
    var r: Boolean
    var p: Boolean
    val m: Marker = s.enter_section_(l, Modifiers._NONE_)
    r = property__(s, l + 1)
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
    val m: Marker = s.enter_section_(l, Modifiers._AND_)
    r = s.consumeToken(JsonSyntaxElementTypes.R_CURLY)
    s.exit_section_(l, m, r, false, null)
    return r
  }

  /* ********************************************************** */
  // &'}' | <<eof>> | <<leftoverErrorInObject>> leftover_value_inside_object*
  internal fun object_leftovers(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "object_leftovers")) return false
    var r: Boolean
    val m: Marker = s.enter_section_()
    r = object_leftovers_0(s, l + 1)
    if (!r) r = s.eof(l + 1)
    if (!r) r = object_leftovers_2(s, l + 1)
    s.exit_section_(m, null, r)
    return r
  }

  // &'}'
  private fun object_leftovers_0(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "object_leftovers_0")) return false
    var r: Boolean
    val m: Marker = s.enter_section_(l, Modifiers._AND_)
    r = s.consumeToken(JsonSyntaxElementTypes.R_CURLY)
    s.exit_section_(l, m, r, false, null)
    return r
  }

  // <<leftoverErrorInObject>> leftover_value_inside_object*
  private fun object_leftovers_2(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "object_leftovers_2")) return false
    var r: Boolean
    val m: Marker = s.enter_section_()
    r = leftoverErrorInObject(s, l + 1)
    r = r && object_leftovers_2_1(s, l + 1)
    s.exit_section_(m, null, r)
    return r
  }

  // leftover_value_inside_object*
  private fun object_leftovers_2_1(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "object_leftovers_2_1")) return false
    while (true) {
      val c: Int = s.current_position_()
      if (!leftover_value_inside_object(s, l + 1)) break
      if (!s.empty_element_parsed_guard_("object_leftovers_2_1", c)) break
    }
    return true
  }

  /* ********************************************************** */
  // property_name (':' property_value)
  fun property__(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "property__")) return false
    var r: Boolean
    var p: Boolean
    val m: Marker = s.enter_section_(l, Modifiers._NONE_, JsonSyntaxElementTypes.PROPERTY, "<property>")
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
    val m: Marker = s.enter_section_(l, Modifiers._NONE_)
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
  // value_impl
  internal fun property_value(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    return value_impl(s, l + 1)
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
  // &'[' <<shallowParseArray>>
  internal fun shallow_array(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "shallow_array")) return false
    if (!s.nextTokenIs(JsonSyntaxElementTypes.L_BRACKET)) return false
    var r: Boolean
    val m: Marker = s.enter_section_()
    r = shallow_array_0(s, l + 1)
    r = r && shallowParseArray(s, l + 1)
    s.exit_section_(m, null, r)
    return r
  }

  // &'['
  private fun shallow_array_0(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "shallow_array_0")) return false
    var r: Boolean
    val m: Marker = s.enter_section_(l, Modifiers._AND_)
    r = s.consumeToken(JsonSyntaxElementTypes.L_BRACKET)
    s.exit_section_(l, m, r, false, null)
    return r
  }

  /* ********************************************************** */
  // &'{' <<shallowParseObject>>
  internal fun shallow_object(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "shallow_object")) return false
    if (!s.nextTokenIs(JsonSyntaxElementTypes.L_CURLY)) return false
    var r: Boolean
    val m: Marker = s.enter_section_()
    r = shallow_object_0(s, l + 1)
    r = r && shallowParseObject(s, l + 1)
    s.exit_section_(m, null, r)
    return r
  }

  // &'{'
  private fun shallow_object_0(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "shallow_object_0")) return false
    var r: Boolean
    val m: Marker = s.enter_section_(l, Modifiers._AND_)
    r = s.consumeToken(JsonSyntaxElementTypes.L_CURLY)
    s.exit_section_(l, m, r, false, null)
    return r
  }

  /* ********************************************************** */
  // SINGLE_QUOTED_STRING | DOUBLE_QUOTED_STRING
  fun string_literal(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "string_literal")) return false
    if (!s.nextTokenIs("<string literal>", JsonSyntaxElementTypes.DOUBLE_QUOTED_STRING, JsonSyntaxElementTypes.SINGLE_QUOTED_STRING)) return false
    var r: Boolean
    val m: Marker = s.enter_section_(l, Modifiers._NONE_, JsonSyntaxElementTypes.STRING_LITERAL, "<string literal>")
    r = s.consumeToken(JsonSyntaxElementTypes.SINGLE_QUOTED_STRING)
    if (!r) r = s.consumeToken(JsonSyntaxElementTypes.DOUBLE_QUOTED_STRING)
    s.exit_section_(l, m, r, false, null)
    return r
  }

  /* ********************************************************** */
  // object | array | literal | reference_expression
  internal fun top_level_value(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "top_level_value")) return false
    var r: Boolean
    val m: Marker = s.enter_section_(l, Modifiers._NONE_)
    r = object__(s, l + 1)
    if (!r) r = array(s, l + 1)
    if (!r) r = literal(s, l + 1)
    if (!r) r = reference_expression(s, l + 1)
    s.exit_section_(l, m, r, false, JsonSyntaxParser::top_level_value_recoverer)
    return r
  }

  /* ********************************************************** */
  // !value_start
  internal fun top_level_value_recoverer(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "top_level_value_recoverer")) return false
    var r: Boolean
    val m: Marker = s.enter_section_(l, Modifiers._NOT_)
    r = !value_start(s, l + 1)
    s.exit_section_(l, m, r, false, null)
    return r
  }

  /* ********************************************************** */
  // shallow_object | shallow_array | literal | reference_expression
  fun value__(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "value__")) return false
    var r: Boolean
    val m: Marker = s.enter_section_(l, Modifiers._COLLAPSE_, JsonSyntaxElementTypes.VALUE, "<value>")
    r = shallow_object(s, l + 1)
    if (!r) r = shallow_array(s, l + 1)
    if (!r) r = literal(s, l + 1)
    if (!r) r = reference_expression(s, l + 1)
    s.exit_section_(l, m, r, false, null)
    return r
  }

  /* ********************************************************** */
  // shallow_object | shallow_array | literal | reference_expression
  internal fun value_impl(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "value_impl")) return false
    var r: Boolean
    r = shallow_object(s, l + 1)
    if (!r) r = shallow_array(s, l + 1)
    if (!r) r = literal(s, l + 1)
    if (!r) r = reference_expression(s, l + 1)
    return r
  }

  /* ********************************************************** */
  // '{' | '[' | SINGLE_QUOTED_STRING | DOUBLE_QUOTED_STRING | NUMBER | TRUE | FALSE | NULL | IDENTIFIER
  internal fun value_start(s: SyntaxGeneratedParserRuntime, l: Int): Boolean {
    if (!s.recursion_guard_(l, "value_start")) return false
    var r: Boolean
    r = s.consumeToken(JsonSyntaxElementTypes.L_CURLY)
    if (!r) r = s.consumeToken(JsonSyntaxElementTypes.L_BRACKET)
    if (!r) r = s.consumeToken(JsonSyntaxElementTypes.SINGLE_QUOTED_STRING)
    if (!r) r = s.consumeToken(JsonSyntaxElementTypes.DOUBLE_QUOTED_STRING)
    if (!r) r = s.consumeToken(JsonSyntaxElementTypes.NUMBER)
    if (!r) r = s.consumeToken(JsonSyntaxElementTypes.TRUE)
    if (!r) r = s.consumeToken(JsonSyntaxElementTypes.FALSE)
    if (!r) r = s.consumeToken(JsonSyntaxElementTypes.NULL)
    if (!r) r = s.consumeToken(JsonSyntaxElementTypes.IDENTIFIER)
    return r
  }

}
