// This is a generated file. Not intended for manual editing.
package de.thomasrosenau.diffplugin.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static de.thomasrosenau.diffplugin.psi.DiffTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class DiffParser implements PsiParser, LightPsiParser {

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
    return diffFile(b, l + 1);
  }

  /* ********************************************************** */
  // WHITE_SPACE | OTHER
  static boolean anyLine(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "anyLine")) return false;
    if (!nextTokenIs(b, "", OTHER, WHITE_SPACE)) return false;
    boolean r;
    r = consumeToken(b, WHITE_SPACE);
    if (!r) r = consumeToken(b, OTHER);
    return r;
  }

  /* ********************************************************** */
  // COMMAND
  public static boolean consoleCommand(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "consoleCommand")) return false;
    if (!nextTokenIs(b, COMMAND)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMAND);
    exit_section_(b, m, CONSOLE_COMMAND, r);
    return r;
  }

  /* ********************************************************** */
  // CONTEXT_FROM_LABEL CONTEXT_TO_LABEL contextHunk+
  static boolean contextDiff(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "contextDiff")) return false;
    if (!nextTokenIs(b, CONTEXT_FROM_LABEL)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, CONTEXT_FROM_LABEL, CONTEXT_TO_LABEL);
    r = r && contextDiff_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // contextHunk+
  private static boolean contextDiff_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "contextDiff_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = contextHunk(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!contextHunk(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "contextDiff_2", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // CONTEXT_COMMON_LINE | CONTEXT_CHANGED_LINE | CONTEXT_DELETED_LINE
  static boolean contextFromFileLine(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "contextFromFileLine")) return false;
    boolean r;
    r = consumeToken(b, CONTEXT_COMMON_LINE);
    if (!r) r = consumeToken(b, CONTEXT_CHANGED_LINE);
    if (!r) r = consumeToken(b, CONTEXT_DELETED_LINE);
    return r;
  }

  /* ********************************************************** */
  // CONTEXT_HUNK_SEPARATOR contextHunkFrom contextHunkTo
  public static boolean contextHunk(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "contextHunk")) return false;
    if (!nextTokenIs(b, CONTEXT_HUNK_SEPARATOR)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, CONTEXT_HUNK_SEPARATOR);
    r = r && contextHunkFrom(b, l + 1);
    r = r && contextHunkTo(b, l + 1);
    exit_section_(b, m, CONTEXT_HUNK, r);
    return r;
  }

  /* ********************************************************** */
  // CONTEXT_FROM_LINE_NUMBERS contextFromFileLine* (EOL_HINT)?
  public static boolean contextHunkFrom(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "contextHunkFrom")) return false;
    if (!nextTokenIs(b, CONTEXT_FROM_LINE_NUMBERS)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, CONTEXT_FROM_LINE_NUMBERS);
    r = r && contextHunkFrom_1(b, l + 1);
    r = r && contextHunkFrom_2(b, l + 1);
    exit_section_(b, m, CONTEXT_HUNK_FROM, r);
    return r;
  }

  // contextFromFileLine*
  private static boolean contextHunkFrom_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "contextHunkFrom_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!contextFromFileLine(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "contextHunkFrom_1", c)) break;
    }
    return true;
  }

  // (EOL_HINT)?
  private static boolean contextHunkFrom_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "contextHunkFrom_2")) return false;
    consumeToken(b, EOL_HINT);
    return true;
  }

  /* ********************************************************** */
  // CONTEXT_TO_LINE_NUMBERS contextToFileLine* (EOL_HINT)?
  public static boolean contextHunkTo(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "contextHunkTo")) return false;
    if (!nextTokenIs(b, CONTEXT_TO_LINE_NUMBERS)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, CONTEXT_TO_LINE_NUMBERS);
    r = r && contextHunkTo_1(b, l + 1);
    r = r && contextHunkTo_2(b, l + 1);
    exit_section_(b, m, CONTEXT_HUNK_TO, r);
    return r;
  }

  // contextToFileLine*
  private static boolean contextHunkTo_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "contextHunkTo_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!contextToFileLine(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "contextHunkTo_1", c)) break;
    }
    return true;
  }

  // (EOL_HINT)?
  private static boolean contextHunkTo_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "contextHunkTo_2")) return false;
    consumeToken(b, EOL_HINT);
    return true;
  }

  /* ********************************************************** */
  // CONTEXT_COMMON_LINE | CONTEXT_CHANGED_LINE | CONTEXT_INSERTED_LINE
  static boolean contextToFileLine(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "contextToFileLine")) return false;
    boolean r;
    r = consumeToken(b, CONTEXT_COMMON_LINE);
    if (!r) r = consumeToken(b, CONTEXT_CHANGED_LINE);
    if (!r) r = consumeToken(b, CONTEXT_INSERTED_LINE);
    return r;
  }

  /* ********************************************************** */
  // gitDiffFile | (singleDiffFile | multiDiffPart+) trailingText
  static boolean diffFile(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "diffFile")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = gitDiffFile(b, l + 1);
    if (!r) r = diffFile_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (singleDiffFile | multiDiffPart+) trailingText
  private static boolean diffFile_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "diffFile_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = diffFile_1_0(b, l + 1);
    r = r && trailingText(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // singleDiffFile | multiDiffPart+
  private static boolean diffFile_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "diffFile_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = singleDiffFile(b, l + 1);
    if (!r) r = diffFile_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // multiDiffPart+
  private static boolean diffFile_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "diffFile_1_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = multiDiffPart(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!multiDiffPart(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "diffFile_1_0_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // GIT_BINARY_PATCH_HEADER (GIT_BINARY_PATCH_CONTENT | WHITE_SPACE)*
  public static boolean gitBinaryPatch(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "gitBinaryPatch")) return false;
    if (!nextTokenIs(b, GIT_BINARY_PATCH_HEADER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, GIT_BINARY_PATCH_HEADER);
    r = r && gitBinaryPatch_1(b, l + 1);
    exit_section_(b, m, GIT_BINARY_PATCH, r);
    return r;
  }

  // (GIT_BINARY_PATCH_CONTENT | WHITE_SPACE)*
  private static boolean gitBinaryPatch_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "gitBinaryPatch_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!gitBinaryPatch_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "gitBinaryPatch_1", c)) break;
    }
    return true;
  }

  // GIT_BINARY_PATCH_CONTENT | WHITE_SPACE
  private static boolean gitBinaryPatch_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "gitBinaryPatch_1_0")) return false;
    boolean r;
    r = consumeToken(b, GIT_BINARY_PATCH_CONTENT);
    if (!r) r = consumeToken(b, WHITE_SPACE);
    return r;
  }

  /* ********************************************************** */
  // gitDiff+
  static boolean gitBody(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "gitBody")) return false;
    if (!nextTokenIs(b, COMMAND)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = gitDiff(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!gitDiff(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "gitBody", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // consoleCommand anyLine* (unifiedDiff | gitBinaryPatch)
  public static boolean gitDiff(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "gitDiff")) return false;
    if (!nextTokenIs(b, COMMAND)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consoleCommand(b, l + 1);
    r = r && gitDiff_1(b, l + 1);
    r = r && gitDiff_2(b, l + 1);
    exit_section_(b, m, GIT_DIFF, r);
    return r;
  }

  // anyLine*
  private static boolean gitDiff_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "gitDiff_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!anyLine(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "gitDiff_1", c)) break;
    }
    return true;
  }

  // unifiedDiff | gitBinaryPatch
  private static boolean gitDiff_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "gitDiff_2")) return false;
    boolean r;
    r = unifiedDiff(b, l + 1);
    if (!r) r = gitBinaryPatch(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // gitHeader gitBody gitFooter
  static boolean gitDiffFile(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "gitDiffFile")) return false;
    if (!nextTokenIs(b, GIT_FIRST_LINE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = gitHeader(b, l + 1);
    r = r && gitBody(b, l + 1);
    r = r && gitFooter(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // GIT_SEPARATOR GIT_VERSION_NUMBER
  public static boolean gitFooter(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "gitFooter")) return false;
    if (!nextTokenIs(b, GIT_SEPARATOR)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, GIT_SEPARATOR, GIT_VERSION_NUMBER);
    exit_section_(b, m, GIT_FOOTER, r);
    return r;
  }

  /* ********************************************************** */
  // GIT_FIRST_LINE anyLine+ GIT_SEPARATOR anyLine+
  public static boolean gitHeader(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "gitHeader")) return false;
    if (!nextTokenIs(b, GIT_FIRST_LINE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, GIT_FIRST_LINE);
    r = r && gitHeader_1(b, l + 1);
    r = r && consumeToken(b, GIT_SEPARATOR);
    r = r && gitHeader_3(b, l + 1);
    exit_section_(b, m, GIT_HEADER, r);
    return r;
  }

  // anyLine+
  private static boolean gitHeader_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "gitHeader_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = anyLine(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!anyLine(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "gitHeader_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // anyLine+
  private static boolean gitHeader_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "gitHeader_3")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = anyLine(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!anyLine(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "gitHeader_3", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // anyLine* consoleCommand anyLine* singleDiffFile
  public static boolean multiDiffPart(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "multiDiffPart")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, MULTI_DIFF_PART, "<multi diff part>");
    r = multiDiffPart_0(b, l + 1);
    r = r && consoleCommand(b, l + 1);
    r = r && multiDiffPart_2(b, l + 1);
    r = r && singleDiffFile(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // anyLine*
  private static boolean multiDiffPart_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "multiDiffPart_0")) return false;
    while (true) {
      int c = current_position_(b);
      if (!anyLine(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "multiDiffPart_0", c)) break;
    }
    return true;
  }

  // anyLine*
  private static boolean multiDiffPart_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "multiDiffPart_2")) return false;
    while (true) {
      int c = current_position_(b);
      if (!anyLine(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "multiDiffPart_2", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // normalHunk+
  static boolean normalDiff(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "normalDiff")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = normalHunk(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!normalHunk(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "normalDiff", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // normalHunkAdd | normalHunkChange | normalHunkDelete
  public static boolean normalHunk(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "normalHunk")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, NORMAL_HUNK, "<normal hunk>");
    r = normalHunkAdd(b, l + 1);
    if (!r) r = normalHunkChange(b, l + 1);
    if (!r) r = normalHunkDelete(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // NORMAL_ADD_COMMAND NORMAL_TO_LINE+ EOL_HINT?
  static boolean normalHunkAdd(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "normalHunkAdd")) return false;
    if (!nextTokenIs(b, NORMAL_ADD_COMMAND)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, NORMAL_ADD_COMMAND);
    r = r && normalHunkAdd_1(b, l + 1);
    r = r && normalHunkAdd_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // NORMAL_TO_LINE+
  private static boolean normalHunkAdd_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "normalHunkAdd_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, NORMAL_TO_LINE);
    while (r) {
      int c = current_position_(b);
      if (!consumeToken(b, NORMAL_TO_LINE)) break;
      if (!empty_element_parsed_guard_(b, "normalHunkAdd_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // EOL_HINT?
  private static boolean normalHunkAdd_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "normalHunkAdd_2")) return false;
    consumeToken(b, EOL_HINT);
    return true;
  }

  /* ********************************************************** */
  // NORMAL_CHANGE_COMMAND NORMAL_FROM_LINE+ EOL_HINT? NORMAL_SEPARATOR NORMAL_TO_LINE+ EOL_HINT?
  static boolean normalHunkChange(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "normalHunkChange")) return false;
    if (!nextTokenIs(b, NORMAL_CHANGE_COMMAND)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, NORMAL_CHANGE_COMMAND);
    r = r && normalHunkChange_1(b, l + 1);
    r = r && normalHunkChange_2(b, l + 1);
    r = r && consumeToken(b, NORMAL_SEPARATOR);
    r = r && normalHunkChange_4(b, l + 1);
    r = r && normalHunkChange_5(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // NORMAL_FROM_LINE+
  private static boolean normalHunkChange_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "normalHunkChange_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, NORMAL_FROM_LINE);
    while (r) {
      int c = current_position_(b);
      if (!consumeToken(b, NORMAL_FROM_LINE)) break;
      if (!empty_element_parsed_guard_(b, "normalHunkChange_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // EOL_HINT?
  private static boolean normalHunkChange_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "normalHunkChange_2")) return false;
    consumeToken(b, EOL_HINT);
    return true;
  }

  // NORMAL_TO_LINE+
  private static boolean normalHunkChange_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "normalHunkChange_4")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, NORMAL_TO_LINE);
    while (r) {
      int c = current_position_(b);
      if (!consumeToken(b, NORMAL_TO_LINE)) break;
      if (!empty_element_parsed_guard_(b, "normalHunkChange_4", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // EOL_HINT?
  private static boolean normalHunkChange_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "normalHunkChange_5")) return false;
    consumeToken(b, EOL_HINT);
    return true;
  }

  /* ********************************************************** */
  // NORMAL_DELETE_COMMAND NORMAL_FROM_LINE+ EOL_HINT?
  static boolean normalHunkDelete(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "normalHunkDelete")) return false;
    if (!nextTokenIs(b, NORMAL_DELETE_COMMAND)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, NORMAL_DELETE_COMMAND);
    r = r && normalHunkDelete_1(b, l + 1);
    r = r && normalHunkDelete_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // NORMAL_FROM_LINE+
  private static boolean normalHunkDelete_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "normalHunkDelete_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, NORMAL_FROM_LINE);
    while (r) {
      int c = current_position_(b);
      if (!consumeToken(b, NORMAL_FROM_LINE)) break;
      if (!empty_element_parsed_guard_(b, "normalHunkDelete_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // EOL_HINT?
  private static boolean normalHunkDelete_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "normalHunkDelete_2")) return false;
    consumeToken(b, EOL_HINT);
    return true;
  }

  /* ********************************************************** */
  // anyLine* (normalDiff | contextDiff | unifiedDiff)
  static boolean singleDiffFile(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "singleDiffFile")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = singleDiffFile_0(b, l + 1);
    r = r && singleDiffFile_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // anyLine*
  private static boolean singleDiffFile_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "singleDiffFile_0")) return false;
    while (true) {
      int c = current_position_(b);
      if (!anyLine(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "singleDiffFile_0", c)) break;
    }
    return true;
  }

  // normalDiff | contextDiff | unifiedDiff
  private static boolean singleDiffFile_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "singleDiffFile_1")) return false;
    boolean r;
    r = normalDiff(b, l + 1);
    if (!r) r = contextDiff(b, l + 1);
    if (!r) r = unifiedDiff(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // anyLine*
  static boolean trailingText(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "trailingText")) return false;
    while (true) {
      int c = current_position_(b);
      if (!anyLine(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "trailingText", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // UNIFIED_FROM_LABEL UNIFIED_TO_LABEL unifiedHunk+
  static boolean unifiedDiff(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unifiedDiff")) return false;
    if (!nextTokenIs(b, UNIFIED_FROM_LABEL)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, UNIFIED_FROM_LABEL, UNIFIED_TO_LABEL);
    r = r && unifiedDiff_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // unifiedHunk+
  private static boolean unifiedDiff_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unifiedDiff_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = unifiedHunk(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!unifiedHunk(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "unifiedDiff_2", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // UNIFIED_LINE_NUMBERS (unifiedLine | WHITE_SPACE)+
  public static boolean unifiedHunk(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unifiedHunk")) return false;
    if (!nextTokenIs(b, UNIFIED_LINE_NUMBERS)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, UNIFIED_LINE_NUMBERS);
    r = r && unifiedHunk_1(b, l + 1);
    exit_section_(b, m, UNIFIED_HUNK, r);
    return r;
  }

  // (unifiedLine | WHITE_SPACE)+
  private static boolean unifiedHunk_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unifiedHunk_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = unifiedHunk_1_0(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!unifiedHunk_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "unifiedHunk_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // unifiedLine | WHITE_SPACE
  private static boolean unifiedHunk_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unifiedHunk_1_0")) return false;
    boolean r;
    r = unifiedLine(b, l + 1);
    if (!r) r = consumeToken(b, WHITE_SPACE);
    return r;
  }

  /* ********************************************************** */
  // UNIFIED_INSERTED_LINE | UNIFIED_DELETED_LINE | UNIFIED_COMMON_LINE | EOL_HINT
  static boolean unifiedLine(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unifiedLine")) return false;
    boolean r;
    r = consumeToken(b, UNIFIED_INSERTED_LINE);
    if (!r) r = consumeToken(b, UNIFIED_DELETED_LINE);
    if (!r) r = consumeToken(b, UNIFIED_COMMON_LINE);
    if (!r) r = consumeToken(b, EOL_HINT);
    return r;
  }

}
