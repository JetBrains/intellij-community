// This is a generated file. Not intended for manual editing.
package com.intellij.jsonpath.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.intellij.jsonpath.psi.JsonPathTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class JsonPathParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType t, PsiBuilder b) {
    parseLight(t, b);
    return b.getTreeBuilt();
  }

  public void parseLight(IElementType t, PsiBuilder b) {
    boolean r;
    b = adapt_builder_(t, b, this, EXTENDS_SETS_);
    Marker m = enter_section_(b, 0, _COLLAPSE_, null);
    r = parse_root_(t, b);
    exit_section_(b, 0, m, t, r, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType t, PsiBuilder b) {
    return parse_root_(t, b, 0);
  }

  static boolean parse_root_(IElementType t, PsiBuilder b, int l) {
    boolean r;
    if (t == EXPRESSION) {
      r = expression(b, l + 1, -1);
    }
    else {
      r = root(b, l + 1);
    }
    return r;
  }

  public static final TokenSet[] EXTENDS_SETS_ = new TokenSet[] {
    create_token_set_(AND_EXPRESSION, BOOLEAN_LITERAL, CONDITIONAL_EXPRESSION, DIVIDE_EXPRESSION,
      EXPRESSION, LITERAL, MINUS_EXPRESSION, MULTIPLY_EXPRESSION,
      NULL_LITERAL, NUMBER_LITERAL, OR_EXPRESSION, PARENTHESIZED_EXPRESSION,
      PATH_EXPRESSION, PLUS_EXPRESSION, REGEX_EXPRESSION, STRING_LITERAL,
      UNARY_MINUS_EXPRESSION, UNARY_NOT_EXPRESSION),
  };

  /* ********************************************************** */
  // TRUE | FALSE
  public static boolean booleanLiteral(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "booleanLiteral")) return false;
    if (!nextTokenIs(b, "<boolean literal>", FALSE, TRUE)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, BOOLEAN_LITERAL, "<boolean literal>");
    r = consumeToken(b, TRUE);
    if (!r) r = consumeToken(b, FALSE);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // DOT segmentExpression+
  static boolean dotExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dotExpr")) return false;
    if (!nextTokenIs(b, DOT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, DOT);
    r = r && dotExpr_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // segmentExpression+
  private static boolean dotExpr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dotExpr_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = segmentExpression(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!segmentExpression(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "dotExpr_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // (RECURSIVE_DESCENT | DOT) (functionCall | idSegment | wildcardSegment | quotedSegment)
  static boolean dotSegment(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dotSegment")) return false;
    if (!nextTokenIs(b, "", DOT, RECURSIVE_DESCENT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = dotSegment_0(b, l + 1);
    r = r && dotSegment_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // RECURSIVE_DESCENT | DOT
  private static boolean dotSegment_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dotSegment_0")) return false;
    boolean r;
    r = consumeToken(b, RECURSIVE_DESCENT);
    if (!r) r = consumeToken(b, DOT);
    return r;
  }

  // functionCall | idSegment | wildcardSegment | quotedSegment
  private static boolean dotSegment_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dotSegment_1")) return false;
    boolean r;
    r = functionCall(b, l + 1);
    if (!r) r = idSegment(b, l + 1);
    if (!r) r = wildcardSegment(b, l + 1);
    if (!r) r = quotedSegment(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // EVAL_CONTEXT segmentExpression*
  public static boolean evalSegment(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "evalSegment")) return false;
    if (!nextTokenIs(b, EVAL_CONTEXT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, EVAL_CONTEXT);
    r = r && evalSegment_1(b, l + 1);
    exit_section_(b, m, EVAL_SEGMENT, r);
    return r;
  }

  // segmentExpression*
  private static boolean evalSegment_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "evalSegment_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!segmentExpression(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "evalSegment_1", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // expression
  public static boolean filterExpression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "filterExpression")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FILTER_EXPRESSION, "<filter expression>");
    r = expression(b, l + 1, -1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // expression (COMMA expression)*
  public static boolean functionArgsList(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionArgsList")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FUNCTION_ARGS_LIST, "<function arguments>");
    r = expression(b, l + 1, -1);
    r = r && functionArgsList_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (COMMA expression)*
  private static boolean functionArgsList_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionArgsList_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!functionArgsList_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "functionArgsList_1", c)) break;
    }
    return true;
  }

  // COMMA expression
  private static boolean functionArgsList_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionArgsList_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && expression(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // functionName LPARENTH functionArgsList? RPARENTH
  public static boolean functionCall(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionCall")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = functionName(b, l + 1);
    r = r && consumeToken(b, LPARENTH);
    r = r && functionCall_2(b, l + 1);
    r = r && consumeToken(b, RPARENTH);
    exit_section_(b, m, FUNCTION_CALL, r);
    return r;
  }

  // functionArgsList?
  private static boolean functionCall_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionCall_2")) return false;
    functionArgsList(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // IDENTIFIER
  public static boolean functionName(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionName")) return false;
    if (!nextTokenIs(b, "<function name>", IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FUNCTION_NAME, "<function name>");
    r = consumeToken(b, IDENTIFIER);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // rootSegment | evalSegment | idSegment | quotedSegment | wildcardSegment
  static boolean head_(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "head_")) return false;
    boolean r;
    r = rootSegment(b, l + 1);
    if (!r) r = evalSegment(b, l + 1);
    if (!r) r = idSegment(b, l + 1);
    if (!r) r = quotedSegment(b, l + 1);
    if (!r) r = wildcardSegment(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER segmentExpression*
  public static boolean idSegment(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "idSegment")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    r = r && idSegment_1(b, l + 1);
    exit_section_(b, m, ID_SEGMENT, r);
    return r;
  }

  // segmentExpression*
  private static boolean idSegment_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "idSegment_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!segmentExpression(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "idSegment_1", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // expression
  public static boolean indexExpression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "indexExpression")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, INDEX_EXPRESSION, "<index expression>");
    r = expression(b, l + 1, -1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // INTEGER_NUMBER (COMMA INTEGER_NUMBER)*
  public static boolean indexesList(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "indexesList")) return false;
    if (!nextTokenIs(b, "<indexes list>", INTEGER_NUMBER)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, INDEXES_LIST, "<indexes list>");
    r = consumeToken(b, INTEGER_NUMBER);
    r = r && indexesList_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (COMMA INTEGER_NUMBER)*
  private static boolean indexesList_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "indexesList_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!indexesList_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "indexesList_1", c)) break;
    }
    return true;
  }

  // COMMA INTEGER_NUMBER
  private static boolean indexesList_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "indexesList_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, COMMA, INTEGER_NUMBER);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // wildcardSegment |
  //   FILTER_OPERATOR (LPARENTH filterExpression RPARENTH)? | // standalone filter operator may be substituted by programmatic filter
  //   (LPARENTH indexExpression RPARENTH) | // supported only in some implementations
  //   spliceExpression |
  //   indexesList
  static boolean nestedExpression_(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nestedExpression_")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = wildcardSegment(b, l + 1);
    if (!r) r = nestedExpression__1(b, l + 1);
    if (!r) r = nestedExpression__2(b, l + 1);
    if (!r) r = spliceExpression(b, l + 1);
    if (!r) r = indexesList(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // FILTER_OPERATOR (LPARENTH filterExpression RPARENTH)?
  private static boolean nestedExpression__1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nestedExpression__1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, FILTER_OPERATOR);
    r = r && nestedExpression__1_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (LPARENTH filterExpression RPARENTH)?
  private static boolean nestedExpression__1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nestedExpression__1_1")) return false;
    nestedExpression__1_1_0(b, l + 1);
    return true;
  }

  // LPARENTH filterExpression RPARENTH
  private static boolean nestedExpression__1_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nestedExpression__1_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPARENTH);
    r = r && filterExpression(b, l + 1);
    r = r && consumeToken(b, RPARENTH);
    exit_section_(b, m, null, r);
    return r;
  }

  // LPARENTH indexExpression RPARENTH
  private static boolean nestedExpression__2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nestedExpression__2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPARENTH);
    r = r && indexExpression(b, l + 1);
    r = r && consumeToken(b, RPARENTH);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // NULL
  public static boolean nullLiteral(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nullLiteral")) return false;
    if (!nextTokenIs(b, NULL)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, NULL);
    exit_section_(b, m, NULL_LITERAL, r);
    return r;
  }

  /* ********************************************************** */
  // DOUBLE_NUMBER | INTEGER_NUMBER
  public static boolean numberLiteral(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "numberLiteral")) return false;
    if (!nextTokenIs(b, "<number literal>", DOUBLE_NUMBER, INTEGER_NUMBER)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, NUMBER_LITERAL, "<number literal>");
    r = consumeToken(b, DOUBLE_NUMBER);
    if (!r) r = consumeToken(b, INTEGER_NUMBER);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // stringLiteral (COMMA stringLiteral)*
  public static boolean quotedPathsList(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "quotedPathsList")) return false;
    if (!nextTokenIs(b, "<quoted paths list>", DOUBLE_QUOTED_STRING, SINGLE_QUOTED_STRING)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, QUOTED_PATHS_LIST, "<quoted paths list>");
    r = stringLiteral(b, l + 1);
    r = r && quotedPathsList_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (COMMA stringLiteral)*
  private static boolean quotedPathsList_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "quotedPathsList_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!quotedPathsList_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "quotedPathsList_1", c)) break;
    }
    return true;
  }

  // COMMA stringLiteral
  private static boolean quotedPathsList_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "quotedPathsList_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && stringLiteral(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // LBRACKET quotedPathsList RBRACKET segmentExpression*
  public static boolean quotedSegment(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "quotedSegment")) return false;
    if (!nextTokenIs(b, LBRACKET)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LBRACKET);
    r = r && quotedPathsList(b, l + 1);
    r = r && consumeToken(b, RBRACKET);
    r = r && quotedSegment_3(b, l + 1);
    exit_section_(b, m, QUOTED_SEGMENT, r);
    return r;
  }

  // segmentExpression*
  private static boolean quotedSegment_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "quotedSegment_3")) return false;
    while (true) {
      int c = current_position_(b);
      if (!segmentExpression(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "quotedSegment_3", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // REGEX_STRING
  public static boolean regexLiteral(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "regexLiteral")) return false;
    if (!nextTokenIs(b, "<regex literal>", REGEX_STRING)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, REGEX_LITERAL, "<regex literal>");
    r = consumeToken(b, REGEX_STRING);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // functionCall | (head_ segments_*)
  static boolean root(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "root")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = functionCall(b, l + 1);
    if (!r) r = root_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // head_ segments_*
  private static boolean root_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "root_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = head_(b, l + 1);
    r = r && root_1_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // segments_*
  private static boolean root_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "root_1_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!segments_(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "root_1_1", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // ROOT_CONTEXT segmentExpression*
  public static boolean rootSegment(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "rootSegment")) return false;
    if (!nextTokenIs(b, ROOT_CONTEXT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ROOT_CONTEXT);
    r = r && rootSegment_1(b, l + 1);
    exit_section_(b, m, ROOT_SEGMENT, r);
    return r;
  }

  // segmentExpression*
  private static boolean rootSegment_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "rootSegment_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!segmentExpression(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "rootSegment_1", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // RECURSIVE_DESCENT segmentExpression+
  static boolean scanExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "scanExpr")) return false;
    if (!nextTokenIs(b, RECURSIVE_DESCENT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, RECURSIVE_DESCENT);
    r = r && scanExpr_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // segmentExpression+
  private static boolean scanExpr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "scanExpr_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = segmentExpression(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!segmentExpression(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "scanExpr_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // LBRACKET nestedExpression_ RBRACKET
  public static boolean segmentExpression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "segmentExpression")) return false;
    if (!nextTokenIs(b, LBRACKET)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LBRACKET);
    r = r && nestedExpression_(b, l + 1);
    r = r && consumeToken(b, RBRACKET);
    exit_section_(b, m, SEGMENT_EXPRESSION, r);
    return r;
  }

  /* ********************************************************** */
  // dotExpr | scanExpr | dotSegment | quotedSegment
  static boolean segments_(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "segments_")) return false;
    boolean r;
    r = dotExpr(b, l + 1);
    if (!r) r = scanExpr(b, l + 1);
    if (!r) r = dotSegment(b, l + 1);
    if (!r) r = quotedSegment(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // (INTEGER_NUMBER COLON INTEGER_NUMBER) |
  //   (INTEGER_NUMBER COLON) |
  //   (COLON INTEGER_NUMBER)
  public static boolean spliceExpression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "spliceExpression")) return false;
    if (!nextTokenIs(b, "<splice expression>", COLON, INTEGER_NUMBER)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, SPLICE_EXPRESSION, "<splice expression>");
    r = spliceExpression_0(b, l + 1);
    if (!r) r = spliceExpression_1(b, l + 1);
    if (!r) r = spliceExpression_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // INTEGER_NUMBER COLON INTEGER_NUMBER
  private static boolean spliceExpression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "spliceExpression_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, INTEGER_NUMBER, COLON, INTEGER_NUMBER);
    exit_section_(b, m, null, r);
    return r;
  }

  // INTEGER_NUMBER COLON
  private static boolean spliceExpression_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "spliceExpression_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, INTEGER_NUMBER, COLON);
    exit_section_(b, m, null, r);
    return r;
  }

  // COLON INTEGER_NUMBER
  private static boolean spliceExpression_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "spliceExpression_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, COLON, INTEGER_NUMBER);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // SINGLE_QUOTED_STRING | DOUBLE_QUOTED_STRING
  public static boolean stringLiteral(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "stringLiteral")) return false;
    if (!nextTokenIs(b, "<string literal>", DOUBLE_QUOTED_STRING, SINGLE_QUOTED_STRING)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, STRING_LITERAL, "<string literal>");
    r = consumeToken(b, SINGLE_QUOTED_STRING);
    if (!r) r = consumeToken(b, DOUBLE_QUOTED_STRING);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // WILDCARD segmentExpression*
  public static boolean wildcardSegment(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "wildcardSegment")) return false;
    if (!nextTokenIs(b, "<wildcard>", WILDCARD)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, WILDCARD_SEGMENT, "<wildcard>");
    r = consumeToken(b, WILDCARD);
    r = r && wildcardSegment_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // segmentExpression*
  private static boolean wildcardSegment_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "wildcardSegment_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!segmentExpression(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "wildcardSegment_1", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // Expression root: expression
  // Operator priority table:
  // 0: PREFIX(unaryNotExpression) PREFIX(unaryMinusExpression)
  // 1: BINARY(andExpression) BINARY(orExpression)
  // 2: BINARY(conditionalExpression) POSTFIX(regexExpression)
  // 3: BINARY(plusExpression) BINARY(minusExpression)
  // 4: BINARY(multiplyExpression) BINARY(divideExpression)
  // 5: ATOM(literal) ATOM(pathExpression) PREFIX(parenthesizedExpression)
  public static boolean expression(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "expression")) return false;
    addVariant(b, "<expression>");
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, "<expression>");
    r = unaryNotExpression(b, l + 1);
    if (!r) r = unaryMinusExpression(b, l + 1);
    if (!r) r = literal(b, l + 1);
    if (!r) r = pathExpression(b, l + 1);
    if (!r) r = parenthesizedExpression(b, l + 1);
    p = r;
    r = r && expression_0(b, l + 1, g);
    exit_section_(b, l, m, null, r, p, null);
    return r || p;
  }

  public static boolean expression_0(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "expression_0")) return false;
    boolean r = true;
    while (true) {
      Marker m = enter_section_(b, l, _LEFT_, null);
      if (g < 3 && consumeTokenSmart(b, MINUS_OP)) {
        r = expression(b, l, 3);
        exit_section_(b, l, m, MINUS_EXPRESSION, r, true, null);
      }
      else if (g < 1 && consumeTokenSmart(b, AND_OP)) {
        r = expression(b, l, 1);
        exit_section_(b, l, m, AND_EXPRESSION, r, true, null);
      }
      else if (g < 1 && consumeTokenSmart(b, OR_OP)) {
        r = expression(b, l, 1);
        exit_section_(b, l, m, OR_EXPRESSION, r, true, null);
      }
      else if (g < 2 && conditionalExpression_0(b, l + 1)) {
        r = expression(b, l, 2);
        exit_section_(b, l, m, CONDITIONAL_EXPRESSION, r, true, null);
      }
      else if (g < 2 && regexExpression_0(b, l + 1)) {
        r = true;
        exit_section_(b, l, m, REGEX_EXPRESSION, r, true, null);
      }
      else if (g < 3 && consumeTokenSmart(b, PLUS_OP)) {
        r = expression(b, l, 3);
        exit_section_(b, l, m, PLUS_EXPRESSION, r, true, null);
      }
      else if (g < 4 && consumeTokenSmart(b, MULTIPLY_OP)) {
        r = expression(b, l, 4);
        exit_section_(b, l, m, MULTIPLY_EXPRESSION, r, true, null);
      }
      else if (g < 4 && consumeTokenSmart(b, DIVIDE_OP)) {
        r = expression(b, l, 4);
        exit_section_(b, l, m, DIVIDE_EXPRESSION, r, true, null);
      }
      else {
        exit_section_(b, l, m, null, false, false, null);
        break;
      }
    }
    return r;
  }

  public static boolean unaryNotExpression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unaryNotExpression")) return false;
    if (!nextTokenIsSmart(b, NOT_OP)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = consumeTokenSmart(b, NOT_OP);
    p = r;
    r = p && expression(b, l, 0);
    exit_section_(b, l, m, UNARY_NOT_EXPRESSION, r, p, null);
    return r || p;
  }

  public static boolean unaryMinusExpression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unaryMinusExpression")) return false;
    if (!nextTokenIsSmart(b, MINUS_OP)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = consumeTokenSmart(b, MINUS_OP);
    p = r;
    r = p && expression(b, l, 0);
    exit_section_(b, l, m, UNARY_MINUS_EXPRESSION, r, p, null);
    return r || p;
  }

  // EQ_OP | NE_OP | GT_OP | LT_OP | GE_OP | LE_OP | IN_OP
  private static boolean conditionalExpression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "conditionalExpression_0")) return false;
    boolean r;
    r = consumeTokenSmart(b, EQ_OP);
    if (!r) r = consumeTokenSmart(b, NE_OP);
    if (!r) r = consumeTokenSmart(b, GT_OP);
    if (!r) r = consumeTokenSmart(b, LT_OP);
    if (!r) r = consumeTokenSmart(b, GE_OP);
    if (!r) r = consumeTokenSmart(b, LE_OP);
    if (!r) r = consumeTokenSmart(b, IN_OP);
    return r;
  }

  // RE_OP regexLiteral
  private static boolean regexExpression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "regexExpression_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, RE_OP);
    r = r && regexLiteral(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // nullLiteral | booleanLiteral | numberLiteral | stringLiteral
  public static boolean literal(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "literal")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, LITERAL, "<literal>");
    r = nullLiteral(b, l + 1);
    if (!r) r = booleanLiteral(b, l + 1);
    if (!r) r = numberLiteral(b, l + 1);
    if (!r) r = stringLiteral(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // head_ segments_*
  public static boolean pathExpression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pathExpression")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PATH_EXPRESSION, "<path expression>");
    r = head_(b, l + 1);
    r = r && pathExpression_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // segments_*
  private static boolean pathExpression_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pathExpression_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!segments_(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "pathExpression_1", c)) break;
    }
    return true;
  }

  public static boolean parenthesizedExpression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parenthesizedExpression")) return false;
    if (!nextTokenIsSmart(b, LPARENTH)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = consumeTokenSmart(b, LPARENTH);
    p = r;
    r = p && expression(b, l, -1);
    r = p && report_error_(b, consumeToken(b, RPARENTH)) && r;
    exit_section_(b, l, m, PARENTHESIZED_EXPRESSION, r, p, null);
    return r || p;
  }

}
