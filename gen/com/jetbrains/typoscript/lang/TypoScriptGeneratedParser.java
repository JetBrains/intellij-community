// This is a generated file. Not intended for manual editing.
package com.jetbrains.typoscript.lang;

import org.jetbrains.annotations.*;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import com.intellij.openapi.diagnostic.Logger;
import static com.jetbrains.typoscript.lang.TypoScriptElementTypes.*;
import static com.jetbrains.typoscript.lang.TypoScriptParserUtil.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import static com.jetbrains.typoscript.lang.TypoScriptTokenTypes.*;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class TypoScriptGeneratedParser implements PsiParser {

  public static Logger LOG_ = Logger.getInstance("com.jetbrains.typoscript.lang.TypoScriptGeneratedParser");

  @NotNull
  public ASTNode parse(final IElementType root_, final PsiBuilder builder_) {
    int level_ = 0;
    boolean result_;
    if (root_ == ASSIGNMENT) {
      result_ = assignment(builder_, level_ + 1);
    }
    else if (root_ == CODE_BLOCK) {
      result_ = code_block(builder_, level_ + 1);
    }
    else if (root_ == CONDITION_ELEMENT) {
      result_ = condition_element(builder_, level_ + 1);
    }
    else if (root_ == COPYING) {
      result_ = copying(builder_, level_ + 1);
    }
    else if (root_ == INCLUDE_STATEMENT_ELEMENT) {
      result_ = include_statement_element(builder_, level_ + 1);
    }
    else if (root_ == MULTILINE_VALUE_ASSIGNMENT) {
      result_ = multiline_value_assignment(builder_, level_ + 1);
    }
    else if (root_ == OBJECT_PATH) {
      result_ = object_path(builder_, level_ + 1);
    }
    else if (root_ == ONE_LINE_COMMENT_ELEMENT) {
      result_ = one_line_comment_element(builder_, level_ + 1);
    }
    else if (root_ == UNSETTING) {
      result_ = unsetting(builder_, level_ + 1);
    }
    else if (root_ == VALUE_MODIFICATION) {
      result_ = value_modification(builder_, level_ + 1);
    }
    else {
      Marker marker_ = builder_.mark();
      result_ = c_style_comment_element(builder_, level_ + 1);
      while (builder_.getTokenType() != null) {
        builder_.advanceLexer();
      }
      marker_.done(root_);
    }
    return builder_.getTreeBuilt();
  }

  /* ********************************************************** */
  // ASSIGNMENT ASSIGNMENT_VALUE
  public static boolean assignment(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "assignment")) return false;
    if (!nextTokenIs(builder_, ASSIGNMENT)) return false;
    boolean result_ = false;
    final Marker marker_ = builder_.mark();
    result_ = consumeToken(builder_, ASSIGNMENT);
    result_ = result_ && consumeToken(builder_, ASSIGNMENT_VALUE);
    if (result_) {
      marker_.done(ASSIGNMENT);
    }
    else {
      marker_.rollbackTo();
    }
    return result_;
  }

  /* ********************************************************** */
  // C_STYLE_COMMENT
  static boolean c_style_comment_element(PsiBuilder builder_, int level_) {
    return consumeToken(builder_, C_STYLE_COMMENT);
  }

  /* ********************************************************** */
  // object_path CODE_BLOCK_OPERATOR_BEGIN IGNORED_TEXT? (internal_expression)* CODE_BLOCK_OPERATOR_END IGNORED_TEXT?
  public static boolean code_block(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "code_block")) return false;
    if (!nextTokenIs(builder_, OBJECT_PATH_ENTITY)) return false;
    boolean result_ = false;
    final Marker marker_ = builder_.mark();
    result_ = object_path(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CODE_BLOCK_OPERATOR_BEGIN);
    result_ = result_ && code_block_2(builder_, level_ + 1);
    result_ = result_ && code_block_3(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CODE_BLOCK_OPERATOR_END);
    result_ = result_ && code_block_5(builder_, level_ + 1);
    if (result_) {
      marker_.done(CODE_BLOCK);
    }
    else {
      marker_.rollbackTo();
    }
    return result_;
  }

  // IGNORED_TEXT?
  private static boolean code_block_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "code_block_2")) return false;
    consumeToken(builder_, IGNORED_TEXT);
    return true;
  }

  // (internal_expression)*
  private static boolean code_block_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "code_block_3")) return false;
    int offset_ = builder_.getCurrentOffset();
    while (true) {
      if (!code_block_3_0(builder_, level_ + 1)) break;
      int next_offset_ = builder_.getCurrentOffset();
      if (offset_ == next_offset_) {
        empty_element_parsed_guard_(builder_, offset_, "code_block_3");
        break;
      }
      offset_ = next_offset_;
    }
    return true;
  }

  // (internal_expression)
  private static boolean code_block_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "code_block_3_0")) return false;
    boolean result_ = false;
    final Marker marker_ = builder_.mark();
    result_ = internal_expression(builder_, level_ + 1);
    if (!result_) {
      marker_.rollbackTo();
    }
    else {
      marker_.drop();
    }
    return result_;
  }

  // IGNORED_TEXT?
  private static boolean code_block_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "code_block_5")) return false;
    consumeToken(builder_, IGNORED_TEXT);
    return true;
  }

  /* ********************************************************** */
  // CONDITION
  public static boolean condition_element(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "condition_element")) return false;
    if (!nextTokenIs(builder_, CONDITION)) return false;
    boolean result_ = false;
    final Marker marker_ = builder_.mark();
    result_ = consumeToken(builder_, CONDITION);
    if (result_) {
      marker_.done(CONDITION_ELEMENT);
    }
    else {
      marker_.rollbackTo();
    }
    return result_;
  }

  /* ********************************************************** */
  // COPYING_OPERATOR object_path
  public static boolean copying(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "copying")) return false;
    if (!nextTokenIs(builder_, COPYING_OPERATOR)) return false;
    boolean result_ = false;
    final Marker marker_ = builder_.mark();
    result_ = consumeToken(builder_, COPYING_OPERATOR);
    result_ = result_ && object_path(builder_, level_ + 1);
    if (result_) {
      marker_.done(COPYING);
    }
    else {
      marker_.rollbackTo();
    }
    return result_;
  }

  /* ********************************************************** */
  // ONE_LINE_COMMENT
  public static boolean include_statement_element(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "include_statement_element")) return false;
    if (!nextTokenIs(builder_, ONE_LINE_COMMENT)) return false;
    boolean result_ = false;
    final Marker marker_ = builder_.mark();
    result_ = consumeToken(builder_, ONE_LINE_COMMENT);
    if (result_) {
      marker_.done(INCLUDE_STATEMENT_ELEMENT);
    }
    else {
      marker_.rollbackTo();
    }
    return result_;
  }

  /* ********************************************************** */
  // internal_object_path { assignment | value_modification | multiline_value_assignment | copying | unsetting | code_block}
  static boolean internal_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "internal_expression")) return false;
    if (!nextTokenIs(builder_, OBJECT_PATH_ENTITY) && !nextTokenIs(builder_, OBJECT_PATH_SEPARATOR)) return false;
    boolean result_ = false;
    final Marker marker_ = builder_.mark();
    result_ = internal_object_path(builder_, level_ + 1);
    result_ = result_ && internal_expression_1(builder_, level_ + 1);
    if (!result_) {
      marker_.rollbackTo();
    }
    else {
      marker_.drop();
    }
    return result_;
  }

  // { assignment | value_modification | multiline_value_assignment | copying | unsetting | code_block}
  private static boolean internal_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "internal_expression_1")) return false;
    return internal_expression_1_0(builder_, level_ + 1);
  }

  // assignment | value_modification | multiline_value_assignment | copying | unsetting | code_block
  private static boolean internal_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "internal_expression_1_0")) return false;
    boolean result_ = false;
    final Marker marker_ = builder_.mark();
    result_ = assignment(builder_, level_ + 1);
    if (!result_) result_ = value_modification(builder_, level_ + 1);
    if (!result_) result_ = multiline_value_assignment(builder_, level_ + 1);
    if (!result_) result_ = copying(builder_, level_ + 1);
    if (!result_) result_ = unsetting(builder_, level_ + 1);
    if (!result_) result_ = code_block(builder_, level_ + 1);
    if (!result_) {
      marker_.rollbackTo();
    }
    else {
      marker_.drop();
    }
    return result_;
  }

  /* ********************************************************** */
  // object_path | OBJECT_PATH_SEPARATOR object_path
  static boolean internal_object_path(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "internal_object_path")) return false;
    if (!nextTokenIs(builder_, OBJECT_PATH_ENTITY) && !nextTokenIs(builder_, OBJECT_PATH_SEPARATOR)) return false;
    boolean result_ = false;
    final Marker marker_ = builder_.mark();
    result_ = object_path(builder_, level_ + 1);
    if (!result_) result_ = internal_object_path_1(builder_, level_ + 1);
    if (!result_) {
      marker_.rollbackTo();
    }
    else {
      marker_.drop();
    }
    return result_;
  }

  // OBJECT_PATH_SEPARATOR object_path
  private static boolean internal_object_path_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "internal_object_path_1")) return false;
    boolean result_ = false;
    final Marker marker_ = builder_.mark();
    result_ = consumeToken(builder_, OBJECT_PATH_SEPARATOR);
    result_ = result_ && object_path(builder_, level_ + 1);
    if (!result_) {
      marker_.rollbackTo();
    }
    else {
      marker_.drop();
    }
    return result_;
  }

  /* ********************************************************** */
  // MULTILINE_VALUE_OPERATOR_BEGIN IGNORED_TEXT? (MULTILINE_VALUE)* MULTILINE_VALUE_OPERATOR_END IGNORED_TEXT?
  public static boolean multiline_value_assignment(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multiline_value_assignment")) return false;
    if (!nextTokenIs(builder_, MULTILINE_VALUE_OPERATOR_BEGIN)) return false;
    boolean result_ = false;
    final Marker marker_ = builder_.mark();
    result_ = consumeToken(builder_, MULTILINE_VALUE_OPERATOR_BEGIN);
    result_ = result_ && multiline_value_assignment_1(builder_, level_ + 1);
    result_ = result_ && multiline_value_assignment_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, MULTILINE_VALUE_OPERATOR_END);
    result_ = result_ && multiline_value_assignment_4(builder_, level_ + 1);
    if (result_) {
      marker_.done(MULTILINE_VALUE_ASSIGNMENT);
    }
    else {
      marker_.rollbackTo();
    }
    return result_;
  }

  // IGNORED_TEXT?
  private static boolean multiline_value_assignment_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multiline_value_assignment_1")) return false;
    consumeToken(builder_, IGNORED_TEXT);
    return true;
  }

  // (MULTILINE_VALUE)*
  private static boolean multiline_value_assignment_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multiline_value_assignment_2")) return false;
    int offset_ = builder_.getCurrentOffset();
    while (true) {
      if (!multiline_value_assignment_2_0(builder_, level_ + 1)) break;
      int next_offset_ = builder_.getCurrentOffset();
      if (offset_ == next_offset_) {
        empty_element_parsed_guard_(builder_, offset_, "multiline_value_assignment_2");
        break;
      }
      offset_ = next_offset_;
    }
    return true;
  }

  // (MULTILINE_VALUE)
  private static boolean multiline_value_assignment_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multiline_value_assignment_2_0")) return false;
    boolean result_ = false;
    final Marker marker_ = builder_.mark();
    result_ = consumeToken(builder_, MULTILINE_VALUE);
    if (!result_) {
      marker_.rollbackTo();
    }
    else {
      marker_.drop();
    }
    return result_;
  }

  // IGNORED_TEXT?
  private static boolean multiline_value_assignment_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multiline_value_assignment_4")) return false;
    consumeToken(builder_, IGNORED_TEXT);
    return true;
  }

  /* ********************************************************** */
  // OBJECT_PATH_ENTITY (OBJECT_PATH_SEPARATOR OBJECT_PATH_ENTITY)*
  public static boolean object_path(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "object_path")) return false;
    if (!nextTokenIs(builder_, OBJECT_PATH_ENTITY)) return false;
    boolean result_ = false;
    final Marker marker_ = builder_.mark();
    result_ = consumeToken(builder_, OBJECT_PATH_ENTITY);
    result_ = result_ && object_path_1(builder_, level_ + 1);
    if (result_) {
      marker_.done(OBJECT_PATH);
    }
    else {
      marker_.rollbackTo();
    }
    return result_;
  }

  // (OBJECT_PATH_SEPARATOR OBJECT_PATH_ENTITY)*
  private static boolean object_path_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "object_path_1")) return false;
    int offset_ = builder_.getCurrentOffset();
    while (true) {
      if (!object_path_1_0(builder_, level_ + 1)) break;
      int next_offset_ = builder_.getCurrentOffset();
      if (offset_ == next_offset_) {
        empty_element_parsed_guard_(builder_, offset_, "object_path_1");
        break;
      }
      offset_ = next_offset_;
    }
    return true;
  }

  // (OBJECT_PATH_SEPARATOR OBJECT_PATH_ENTITY)
  private static boolean object_path_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "object_path_1_0")) return false;
    return object_path_1_0_0(builder_, level_ + 1);
  }

  // OBJECT_PATH_SEPARATOR OBJECT_PATH_ENTITY
  private static boolean object_path_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "object_path_1_0_0")) return false;
    boolean result_ = false;
    final Marker marker_ = builder_.mark();
    result_ = consumeToken(builder_, OBJECT_PATH_SEPARATOR);
    result_ = result_ && consumeToken(builder_, OBJECT_PATH_ENTITY);
    if (!result_) {
      marker_.rollbackTo();
    }
    else {
      marker_.drop();
    }
    return result_;
  }

  /* ********************************************************** */
  // ONE_LINE_COMMENT
  public static boolean one_line_comment_element(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "one_line_comment_element")) return false;
    if (!nextTokenIs(builder_, ONE_LINE_COMMENT)) return false;
    boolean result_ = false;
    final Marker marker_ = builder_.mark();
    result_ = consumeToken(builder_, ONE_LINE_COMMENT);
    if (result_) {
      marker_.done(ONE_LINE_COMMENT_ELEMENT);
    }
    else {
      marker_.rollbackTo();
    }
    return result_;
  }

  /* ********************************************************** */
  // UNSETTING_OPERATOR IGNORED_TEXT?
  public static boolean unsetting(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "unsetting")) return false;
    if (!nextTokenIs(builder_, UNSETTING_OPERATOR)) return false;
    boolean result_ = false;
    final Marker marker_ = builder_.mark();
    result_ = consumeToken(builder_, UNSETTING_OPERATOR);
    result_ = result_ && unsetting_1(builder_, level_ + 1);
    if (result_) {
      marker_.done(UNSETTING);
    }
    else {
      marker_.rollbackTo();
    }
    return result_;
  }

  // IGNORED_TEXT?
  private static boolean unsetting_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "unsetting_1")) return false;
    consumeToken(builder_, IGNORED_TEXT);
    return true;
  }

  /* ********************************************************** */
  // MODIFICATION_OPERATOR MODIFICATION_OPERATOR_FUNCTION MODIFICATION_OPERATOR_FUNCTION_PARAM_BEGIN MODIFICATION_OPERATOR_FUNCTION_ARGUMENT MODIFICATION_OPERATOR_FUNCTION_PARAM_END
  public static boolean value_modification(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "value_modification")) return false;
    if (!nextTokenIs(builder_, MODIFICATION_OPERATOR)) return false;
    boolean result_ = false;
    final Marker marker_ = builder_.mark();
    result_ = consumeToken(builder_, MODIFICATION_OPERATOR);
    result_ = result_ && consumeToken(builder_, MODIFICATION_OPERATOR_FUNCTION);
    result_ = result_ && consumeToken(builder_, MODIFICATION_OPERATOR_FUNCTION_PARAM_BEGIN);
    result_ = result_ && consumeToken(builder_, MODIFICATION_OPERATOR_FUNCTION_ARGUMENT);
    result_ = result_ && consumeToken(builder_, MODIFICATION_OPERATOR_FUNCTION_PARAM_END);
    if (result_) {
      marker_.done(VALUE_MODIFICATION);
    }
    else {
      marker_.rollbackTo();
    }
    return result_;
  }

}
