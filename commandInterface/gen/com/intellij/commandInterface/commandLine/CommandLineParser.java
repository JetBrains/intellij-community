// This is a generated file. Not intended for manual editing.
package com.intellij.commandInterface.commandLine;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.intellij.commandInterface.commandLine.CommandLineElementTypes.*;
import static com.intellij.commandInterface.commandLine.CommandLineParserUtil.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class CommandLineParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType t, PsiBuilder b) {
    parseLight(t, b);
    return b.getTreeBuilt();
  }

  public void parseLight(IElementType t, PsiBuilder b) {
    boolean r;
    b = adapt_builder_(t, b, this, null);
    Marker m = enter_section_(b, 0, _COLLAPSE_, null);
    r = parse_root_(t, b);
    exit_section_(b, 0, m, t, r, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType t, PsiBuilder b) {
    return parse_root_(t, b, 0);
  }

  static boolean parse_root_(IElementType t, PsiBuilder b, int l) {
    return root(b, l + 1);
  }

  /* ********************************************************** */
  // LITERAL_STARTS_FROM_LETTER | LITERAL_STARTS_FROM_DIGIT | LITERAL_STARTS_FROM_SYMBOL |
  // SPACED_LITERAL_STARTS_FROM_LETTER | SPACED_LITERAL_STARTS_FROM_DIGIT | SPACED_LITERAL_STARTS_FROM_SYMBOL |
  // SINGLE_Q_SPACED_LITERAL_STARTS_FROM_LETTER | SINGLE_Q_SPACED_LITERAL_STARTS_FROM_DIGIT | SINGLE_Q_SPACED_LITERAL_STARTS_FROM_SYMBOL
  public static boolean argument(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "argument")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ARGUMENT, "<argument>");
    r = consumeToken(b, LITERAL_STARTS_FROM_LETTER);
    if (!r) r = consumeToken(b, LITERAL_STARTS_FROM_DIGIT);
    if (!r) r = consumeToken(b, LITERAL_STARTS_FROM_SYMBOL);
    if (!r) r = consumeToken(b, SPACED_LITERAL_STARTS_FROM_LETTER);
    if (!r) r = consumeToken(b, SPACED_LITERAL_STARTS_FROM_DIGIT);
    if (!r) r = consumeToken(b, SPACED_LITERAL_STARTS_FROM_SYMBOL);
    if (!r) r = consumeToken(b, SINGLE_Q_SPACED_LITERAL_STARTS_FROM_LETTER);
    if (!r) r = consumeToken(b, SINGLE_Q_SPACED_LITERAL_STARTS_FROM_DIGIT);
    if (!r) r = consumeToken(b, SINGLE_Q_SPACED_LITERAL_STARTS_FROM_SYMBOL);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // LITERAL_STARTS_FROM_LETTER
  public static boolean command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "command")) return false;
    if (!nextTokenIs(b, LITERAL_STARTS_FROM_LETTER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LITERAL_STARTS_FROM_LETTER);
    exit_section_(b, m, COMMAND, r);
    return r;
  }

  /* ********************************************************** */
  // LONG_OPTION_NAME_TOKEN
  static boolean long_option_name(PsiBuilder b, int l) {
    return consumeToken(b, LONG_OPTION_NAME_TOKEN);
  }

  /* ********************************************************** */
  // short_option_name <<bound_argument>> ? | long_option_name <<bound_argument>> ?
  public static boolean option(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option")) return false;
    if (!nextTokenIs(b, "<option>", LONG_OPTION_NAME_TOKEN, SHORT_OPTION_NAME_TOKEN)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, OPTION, "<option>");
    r = option_0(b, l + 1);
    if (!r) r = option_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // short_option_name <<bound_argument>> ?
  private static boolean option_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = short_option_name(b, l + 1);
    r = r && option_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // <<bound_argument>> ?
  private static boolean option_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option_0_1")) return false;
    bound_argument(b, l + 1);
    return true;
  }

  // long_option_name <<bound_argument>> ?
  private static boolean option_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = long_option_name(b, l + 1);
    r = r && option_1_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // <<bound_argument>> ?
  private static boolean option_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option_1_1")) return false;
    bound_argument(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // command (argument | option ) *  <<eof>>
  static boolean root(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "root")) return false;
    if (!nextTokenIs(b, LITERAL_STARTS_FROM_LETTER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = command(b, l + 1);
    r = r && root_1(b, l + 1);
    r = r && eof(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (argument | option ) *
  private static boolean root_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "root_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!root_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "root_1", c)) break;
    }
    return true;
  }

  // argument | option
  private static boolean root_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "root_1_0")) return false;
    boolean r;
    r = argument(b, l + 1);
    if (!r) r = option(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // SHORT_OPTION_NAME_TOKEN
  static boolean short_option_name(PsiBuilder b, int l) {
    return consumeToken(b, SHORT_OPTION_NAME_TOKEN);
  }

}
