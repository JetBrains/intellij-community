// This is a generated file. Not intended for manual editing.
package com.intellij.json;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import com.intellij.openapi.diagnostic.Logger;
import static com.intellij.json.JsonElementTypes.*;
import static com.intellij.json.psi.JsonParserUtil.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class JsonParser implements PsiParser {

  public static final Logger LOG_ = Logger.getInstance("com.intellij.json.JsonParser");

  public ASTNode parse(IElementType root_, PsiBuilder builder_) {
    boolean result_;
    builder_ = adapt_builder_(root_, builder_, this, EXTENDS_SETS_);
    Marker marker_ = enter_section_(builder_, 0, _COLLAPSE_, null);
    if (root_ == ARRAY) {
      result_ = array(builder_, 0);
    }
    else if (root_ == BOOLEAN_LITERAL) {
      result_ = boolean_literal(builder_, 0);
    }
    else if (root_ == LITERAL) {
      result_ = literal(builder_, 0);
    }
    else if (root_ == NULL_LITERAL) {
      result_ = null_literal(builder_, 0);
    }
    else if (root_ == NUMBER_LITERAL) {
      result_ = number_literal(builder_, 0);
    }
    else if (root_ == OBJECT) {
      result_ = object(builder_, 0);
    }
    else if (root_ == PROPERTY) {
      result_ = property(builder_, 0);
    }
    else if (root_ == REFERENCE_EXPRESSION) {
      result_ = reference_expression(builder_, 0);
    }
    else if (root_ == STRING_LITERAL) {
      result_ = string_literal(builder_, 0);
    }
    else if (root_ == VALUE) {
      result_ = value(builder_, 0);
    }
    else {
      result_ = parse_root_(root_, builder_, 0);
    }
    exit_section_(builder_, 0, marker_, root_, result_, true, TRUE_CONDITION);
    return builder_.getTreeBuilt();
  }

  protected boolean parse_root_(final IElementType root_, final PsiBuilder builder_, final int level_) {
    return json(builder_, level_ + 1);
  }

  public static final TokenSet[] EXTENDS_SETS_ = new TokenSet[] {
    create_token_set_(BOOLEAN_LITERAL, LITERAL, NULL_LITERAL, NUMBER_LITERAL,
      STRING_LITERAL),
    create_token_set_(ARRAY, BOOLEAN_LITERAL, LITERAL, NULL_LITERAL,
      NUMBER_LITERAL, OBJECT, REFERENCE_EXPRESSION, STRING_LITERAL,
      VALUE),
  };

  /* ********************************************************** */
  // '[' array_element* ']'
  public static boolean array(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "array")) return false;
    if (!nextTokenIs(builder_, L_BRACKET)) return false;
    boolean result_;
    boolean pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, null);
    result_ = consumeToken(builder_, L_BRACKET);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, array_1(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, R_BRACKET) && result_;
    exit_section_(builder_, level_, marker_, ARRAY, result_, pinned_, null);
    return result_ || pinned_;
  }

  // array_element*
  private static boolean array_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "array_1")) return false;
    int pos_ = current_position_(builder_);
    while (true) {
      if (!array_element(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "array_1", pos_)) break;
      pos_ = current_position_(builder_);
    }
    return true;
  }

  /* ********************************************************** */
  // value (','|&']')
  static boolean array_element(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "array_element")) return false;
    boolean result_;
    boolean pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, null);
    result_ = value(builder_, level_ + 1);
    pinned_ = result_; // pin = 1
    result_ = result_ && array_element_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, null, result_, pinned_, not_bracket_or_next_value_parser_);
    return result_ || pinned_;
  }

  // ','|&']'
  private static boolean array_element_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "array_element_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    if (!result_) result_ = array_element_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // &']'
  private static boolean array_element_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "array_element_1_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _AND_, null);
    result_ = consumeToken(builder_, R_BRACKET);
    exit_section_(builder_, level_, marker_, null, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // TRUE | FALSE
  public static boolean boolean_literal(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boolean_literal")) return false;
    if (!nextTokenIs(builder_, "<boolean literal>", FALSE, TRUE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, "<boolean literal>");
    result_ = consumeToken(builder_, TRUE);
    if (!result_) result_ = consumeToken(builder_, FALSE);
    exit_section_(builder_, level_, marker_, BOOLEAN_LITERAL, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // value
  static boolean json(PsiBuilder builder_, int level_) {
    return value(builder_, level_ + 1);
  }

  /* ********************************************************** */
  // string_literal | number_literal | boolean_literal | null_literal
  public static boolean literal(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "literal")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, "<literal>");
    result_ = string_literal(builder_, level_ + 1);
    if (!result_) result_ = number_literal(builder_, level_ + 1);
    if (!result_) result_ = boolean_literal(builder_, level_ + 1);
    if (!result_) result_ = null_literal(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, LITERAL, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // !('}'|value)
  static boolean not_brace_or_next_value(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "not_brace_or_next_value")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_, null);
    result_ = !not_brace_or_next_value_0(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, null, result_, false, null);
    return result_;
  }

  // '}'|value
  private static boolean not_brace_or_next_value_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "not_brace_or_next_value_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, R_CURLY);
    if (!result_) result_ = value(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // !(']'|value)
  static boolean not_bracket_or_next_value(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "not_bracket_or_next_value")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_, null);
    result_ = !not_bracket_or_next_value_0(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, null, result_, false, null);
    return result_;
  }

  // ']'|value
  private static boolean not_bracket_or_next_value_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "not_bracket_or_next_value_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, R_BRACKET);
    if (!result_) result_ = value(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // NULL
  public static boolean null_literal(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "null_literal")) return false;
    if (!nextTokenIs(builder_, NULL)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NULL);
    exit_section_(builder_, marker_, NULL_LITERAL, result_);
    return result_;
  }

  /* ********************************************************** */
  // NUMBER
  public static boolean number_literal(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "number_literal")) return false;
    if (!nextTokenIs(builder_, NUMBER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NUMBER);
    exit_section_(builder_, marker_, NUMBER_LITERAL, result_);
    return result_;
  }

  /* ********************************************************** */
  // '{' object_element* '}'
  public static boolean object(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "object")) return false;
    if (!nextTokenIs(builder_, L_CURLY)) return false;
    boolean result_;
    boolean pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, null);
    result_ = consumeToken(builder_, L_CURLY);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, object_1(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, R_CURLY) && result_;
    exit_section_(builder_, level_, marker_, OBJECT, result_, pinned_, null);
    return result_ || pinned_;
  }

  // object_element*
  private static boolean object_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "object_1")) return false;
    int pos_ = current_position_(builder_);
    while (true) {
      if (!object_element(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "object_1", pos_)) break;
      pos_ = current_position_(builder_);
    }
    return true;
  }

  /* ********************************************************** */
  // property (','|&'}')
  static boolean object_element(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "object_element")) return false;
    boolean result_;
    boolean pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, null);
    result_ = property(builder_, level_ + 1);
    pinned_ = result_; // pin = 1
    result_ = result_ && object_element_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, null, result_, pinned_, not_brace_or_next_value_parser_);
    return result_ || pinned_;
  }

  // ','|&'}'
  private static boolean object_element_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "object_element_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    if (!result_) result_ = object_element_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // &'}'
  private static boolean object_element_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "object_element_1_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _AND_, null);
    result_ = consumeToken(builder_, R_CURLY);
    exit_section_(builder_, level_, marker_, null, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // property_name (':' value)
  public static boolean property(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "property")) return false;
    boolean result_;
    boolean pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, "<property>");
    result_ = property_name(builder_, level_ + 1);
    pinned_ = result_; // pin = 1
    result_ = result_ && property_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, PROPERTY, result_, pinned_, null);
    return result_ || pinned_;
  }

  // ':' value
  private static boolean property_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "property_1")) return false;
    boolean result_;
    boolean pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, null);
    result_ = consumeToken(builder_, COLON);
    pinned_ = result_; // pin = 1
    result_ = result_ && value(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, null, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // literal | reference_expression
  static boolean property_name(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "property_name")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = literal(builder_, level_ + 1);
    if (!result_) result_ = reference_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // INDENTIFIER
  public static boolean reference_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "reference_expression")) return false;
    if (!nextTokenIs(builder_, INDENTIFIER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, INDENTIFIER);
    exit_section_(builder_, marker_, REFERENCE_EXPRESSION, result_);
    return result_;
  }

  /* ********************************************************** */
  // SINGLE_QUOTED_STRING | DOUBLE_QUOTED_STRING
  public static boolean string_literal(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "string_literal")) return false;
    if (!nextTokenIs(builder_, "<string literal>", DOUBLE_QUOTED_STRING, SINGLE_QUOTED_STRING)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, "<string literal>");
    result_ = consumeToken(builder_, SINGLE_QUOTED_STRING);
    if (!result_) result_ = consumeToken(builder_, DOUBLE_QUOTED_STRING);
    exit_section_(builder_, level_, marker_, STRING_LITERAL, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // object | array | literal | reference_expression
  public static boolean value(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "value")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, "<value>");
    result_ = object(builder_, level_ + 1);
    if (!result_) result_ = array(builder_, level_ + 1);
    if (!result_) result_ = literal(builder_, level_ + 1);
    if (!result_) result_ = reference_expression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, VALUE, result_, false, null);
    return result_;
  }

  final static Parser not_brace_or_next_value_parser_ = new Parser() {
    public boolean parse(PsiBuilder builder_, int level_) {
      return not_brace_or_next_value(builder_, level_ + 1);
    }
  };
  final static Parser not_bracket_or_next_value_parser_ = new Parser() {
    public boolean parse(PsiBuilder builder_, int level_) {
      return not_bracket_or_next_value(builder_, level_ + 1);
    }
  };
}
