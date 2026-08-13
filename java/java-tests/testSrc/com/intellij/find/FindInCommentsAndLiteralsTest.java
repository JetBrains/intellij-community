// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.testFramework.LightVirtualFile;

import java.util.ArrayList;
import java.util.List;

/**
 * Pins down every occurrence {@code findString} reports for the comments/literals search contexts.
 * <p>
 * The interesting inputs are the ones where the lexer stays in a non-initial state for a long span
 * ({@link #oneGiantTag}) or where all the occurrences live in a single token ({@link #oneHugeLiteral}) — those used to
 * make the {@code EXCEPT_*} skip-range build quadratic, because it restarted the lexer once per occurrence.
 */
public class FindInCommentsAndLiteralsTest extends DaemonAnalyzerTestCase {
  private FindManager myFindManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFindManager = FindManager.getInstance(myProject);
  }

  @Override
  protected void tearDown() throws Exception {
    myFindManager = null;
    super.tearDown();
  }

  private static String multiLine(int n) {
    StringBuilder sb = new StringBuilder("<root>\n");
    for (int i = 0; i < n; i++) {
      sb.append("  <item id=\"needle").append(i).append("\" name=\"a needle here\"/>\n");
    }
    sb.append("  <tail>needle</tail>\n</root>\n");
    return sb.toString();
  }

  /** Every occurrence inside one attribute value, so they all share a single lexer token. */
  private static String oneHugeLiteral(int n) {
    StringBuilder sb = new StringBuilder("<root>\n  <item data=\"");
    for (int i = 0; i < n; i++) {
      sb.append("needle ").append(i).append(' ');
    }
    sb.append("\"/>\n  <tail>needle</tail>\n</root>\n");
    return sb.toString();
  }

  /** One element whose attribute list spans the file, so {@code lexer.getState() == 0} never holds inside it. */
  private static String oneGiantTag(int n) {
    StringBuilder sb = new StringBuilder("<root>\n  <item\n");
    for (int i = 0; i < n; i++) {
      sb.append("      a").append(i).append("=\"a needle here\"\n");
    }
    sb.append("  />\n  <tail>needle</tail>\n</root>\n");
    return sb.toString();
  }

  private static String comments(int n) {
    StringBuilder sb = new StringBuilder("<root>\n");
    for (int i = 0; i < n; i++) {
      sb.append("  <!-- a needle here ").append(i).append(" -->\n");
    }
    sb.append("  <tail>needle</tail>\n</root>\n");
    return sb.toString();
  }

  /** Iterates the way {@code FindInProjectUtil.processSomeOccurrencesInFile} does, collecting every reported result. */
  private List<String> occurrences(String text, FindModel.SearchContext context, boolean regex, boolean wholeWords) {
    FindModel model = FindManagerTestUtils.configureFindModel("needle");
    model.setSearchContext(context);
    model.setRegularExpressions(regex);
    model.setWholeWordsOnly(wholeWords);

    LightVirtualFile file = new LightVirtualFile("A.xml", text);
    List<String> result = new ArrayList<>();
    int offset = 0;
    while (offset < text.length() && result.size() < 400) {
      FindResult found = myFindManager.findString(text, offset, model, file);
      if (!found.isStringFound()) break;
      result.add(found.getStartOffset() + "-" + found.getEndOffset());
      offset = found.getEndOffset() == offset ? offset + 1 : found.getEndOffset();
    }
    return result;
  }

  private void assertOccurrences(List<String> expected, String text, FindModel.SearchContext context) {
    assertEquals(context + ", plain", expected, occurrences(text, context, false, false));
    assertEquals(context + ", regexp", expected, occurrences(text, context, true, false));
  }

  public void testMultiLine() {
    String text = multiLine(6);
    assertOccurrences(List.of("19-25", "36-42", "63-69", "80-86", "107-113", "124-130", "151-157", "168-174",
                              "195-201", "212-218", "239-245", "256-262", "279-285"),
                      text, FindModel.SearchContext.EXCEPT_COMMENTS);
    assertOccurrences(List.of("279-285"), text, FindModel.SearchContext.EXCEPT_STRING_LITERALS);
    assertOccurrences(List.of("279-285"), text, FindModel.SearchContext.EXCEPT_COMMENTS_AND_STRING_LITERALS);
    assertOccurrences(List.of("19-25", "36-42", "63-69", "80-86", "107-113", "124-130", "151-157", "168-174",
                              "195-201", "212-218", "239-245", "256-262"),
                      text, FindModel.SearchContext.IN_STRING_LITERALS);
    assertOccurrences(List.of(), text, FindModel.SearchContext.IN_COMMENTS);
  }

  public void testAllOccurrencesInOneToken() {
    String text = oneHugeLiteral(6);
    assertOccurrences(List.of("21-27", "30-36", "39-45", "48-54", "57-63", "66-72", "87-93"),
                      text, FindModel.SearchContext.EXCEPT_COMMENTS);
    assertOccurrences(List.of("87-93"), text, FindModel.SearchContext.EXCEPT_STRING_LITERALS);
    assertOccurrences(List.of("21-27", "30-36", "39-45", "48-54", "57-63", "66-72"),
                      text, FindModel.SearchContext.IN_STRING_LITERALS);
  }

  public void testLexerNeverReturnsToInitialState() {
    String text = oneGiantTag(6);
    assertOccurrences(List.of("27-33", "52-58", "77-83", "102-108", "127-133", "152-158", "178-184"),
                      text, FindModel.SearchContext.EXCEPT_COMMENTS);
    assertOccurrences(List.of("178-184"), text, FindModel.SearchContext.EXCEPT_STRING_LITERALS);
    assertOccurrences(List.of("27-33", "52-58", "77-83", "102-108", "127-133", "152-158"),
                      text, FindModel.SearchContext.IN_STRING_LITERALS);
  }

  public void testComments() {
    String text = comments(6);
    assertOccurrences(List.of("177-183"), text, FindModel.SearchContext.EXCEPT_COMMENTS);
    assertOccurrences(List.of("16-22", "43-49", "70-76", "97-103", "124-130", "151-157", "177-183"),
                      text, FindModel.SearchContext.EXCEPT_STRING_LITERALS);
    assertOccurrences(List.of("177-183"), text, FindModel.SearchContext.EXCEPT_COMMENTS_AND_STRING_LITERALS);
    assertOccurrences(List.of("16-22", "43-49", "70-76", "97-103", "124-130", "151-157"),
                      text, FindModel.SearchContext.IN_COMMENTS);
    assertOccurrences(List.of(), text, FindModel.SearchContext.IN_STRING_LITERALS);
  }

  /**
   * The skip ranges are collected in a single pass now, so this checks that pass still drops non-whole-word matches
   * the way the per-occurrence {@code findStringLoop} does. In {@code id="needle0"} the match is followed by a digit
   * and is therefore not a whole word; in {@code name="a needle here"} it is.
   */
  public void testWholeWordsOnly() {
    String text = multiLine(6);
    assertEquals(List.of("36-42", "80-86", "124-130", "168-174", "212-218", "256-262", "279-285"),
                 occurrences(text, FindModel.SearchContext.EXCEPT_COMMENTS, false, true));
    assertEquals(List.of("36-42", "80-86", "124-130", "168-174", "212-218", "256-262"),
                 occurrences(text, FindModel.SearchContext.IN_STRING_LITERALS, false, true));
    assertEquals(List.of("279-285"),
                 occurrences(text, FindModel.SearchContext.EXCEPT_STRING_LITERALS, false, true));
  }

  /**
   * A repeated Find Previous through occurrences that all share one token: the whole file is collected on the first of
   * these calls and every one of them is answered from that, so the walk they used to cost each is gone.
   */
  public void testBackwardSearchInOneHugeLiteral() {
    String text = oneHugeLiteral(6);
    FindModel model = FindManagerTestUtils.configureFindModel("needle");
    model.setSearchContext(FindModel.SearchContext.IN_STRING_LITERALS);
    model.setForward(false);
    LightVirtualFile file = new LightVirtualFile("A.xml", text);

    List<String> result = new ArrayList<>();
    int offset = text.length();
    while (offset > 0) {
      FindResult found = myFindManager.findString(text, offset, model, file);
      if (!found.isStringFound()) break;
      result.add(found.getStartOffset() + "-" + found.getEndOffset());
      offset = found.getStartOffset();
    }
    // the forward walk of testAllOccurrencesInOneToken, in reverse; <tail>needle</tail> is outside the literal
    assertEquals(List.of("66-72", "57-63", "48-54", "39-45", "30-36", "21-27"), result);
  }

  public void testBackwardSearch() {
    String text = multiLine(3);
    FindModel model = FindManagerTestUtils.configureFindModel("needle");
    model.setSearchContext(FindModel.SearchContext.IN_STRING_LITERALS);
    model.setForward(false);
    LightVirtualFile file = new LightVirtualFile("A.xml", text);

    // last literal occurrence before end of file
    FindResult result = myFindManager.findString(text, text.length(), model, file);
    assertTrue(result.isStringFound());
    assertEquals(124, result.getStartOffset());

    // and the one before that
    result = myFindManager.findString(text, result.getStartOffset(), model, file);
    assertTrue(result.isStringFound());
    assertEquals(107, result.getStartOffset());
  }
}
