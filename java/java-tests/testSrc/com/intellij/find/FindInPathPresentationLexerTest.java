// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.PsiFile;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.ChunkExtractor;
import com.intellij.usages.TextChunk;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.impl.rules.UsageType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The rows the occurrences Find in Path found become are built one after another, in the order they were found, and they
 * share one {@link ChunkExtractor}: its highlighting lexer resumes where the row before it left it instead of restarting
 * per row, which is what keeps a file whose text is one long token from being lexed once per occurrence. So the rows
 * have to be lexed once between them all -- and each of them still has to show what it shows on its own.
 */
public class FindInPathPresentationLexerTest extends DaemonAnalyzerTestCase {

  private static final String NEEDLE = "needle";

  /** One occurrence per line, each in a differently highlighted place, so a lexer left in the wrong token shows up. */
  private static final String OCCURRENCES_IN_DIFFERENT_TOKENS = """
    <root>
      <!-- needle in a comment -->
      <item data="needle in an attribute value"/>
      <item>needle in element text</item>
      <item needle="in an attribute name"/>
    </root>
    """;

  /**
   * What the resuming is worth, counted rather than timed: a file whose text is a single token is lexed once for all of
   * its rows, and not once each. A lexer left past the row it was asked about has to be restarted from the beginning of
   * the file for the row after it, and reaching that one merged token again means reading the whole value again -- which
   * the character sequence the rows are presented over can count.
   */
  public void testRowsInsideOneMergedTokenAreLexedOnceBetweenThemAll() {
    int rowCount = 200;
    String text = oneAttributeValuePerLine(rowCount);
    List<UsageInfo2UsageAdapter> rows = rowsOf(text);
    assertEquals(rowCount, rows.size());

    CountingText chars = new CountingText(text);
    ChunkExtractor extractor = ChunkExtractor.getExtractor(getFile());
    Document document = getEditor().getDocument();
    for (UsageInfo2UsageAdapter row : rows) {
      int line = document.getLineNumber(row.getUsageInfo().getNavigationOffset());
      extractor.appendTextChunks(row, chars, document.getLineStartOffset(line), document.getLineEndOffset(line),
                                 true, new ArrayList<>());
    }

    // Resuming reads the file a handful of times over: once lexing it, and once more per row for the fragment it shows.
    // Restarting per row reads all of it for every one of them, which for 200 rows is two orders of magnitude more.
    assertTrue("presenting " + rowCount + " rows read " + chars.reads() + " characters of a " + text.length() +
               " character file, which is more than a few passes over it",
               chars.reads() <= 10L * text.length());
  }

  public void testRowsShowTheSameWhicheverOrderTheyAreBuiltIn() {
    assertRowsReadTheSameForwardsAndBackwards(OCCURRENCES_IN_DIFFERENT_TOKENS);
  }

  /**
   * The shape the resuming is there for: one attribute value spanning every line, which the XML lexer returns character
   * by character and {@code MergingLexerAdapter} merges into a single token however long the value is. Every row has to
   * be highlighted as that one token whether it was reached by resuming the lexer or by restarting it.
   */
  public void testRowsInsideOneMergedTokenShowTheSameWhicheverOrderTheyAreBuiltIn() {
    assertRowsReadTheSameForwardsAndBackwards(oneAttributeValuePerLine(20));
  }

  /**
   * The other reader of the tokens, which stops on the first one it has seen enough of and so leaves the lexer in the
   * middle of the row it was asked about: the type it derives still has to be the one its own token carries.
   */
  public void testUsageTypeOfEachRowComesFromItsOwnToken() {
    List<UsageInfo2UsageAdapter> rows = rowsOf(OCCURRENCES_IN_DIFFERENT_TOKENS);
    for (UsageInfo2UsageAdapter row : rows) {
      row.updateCachedPresentation();
    }

    assertEquals(List.of(UsageType.COMMENT_USAGE, UsageType.LITERAL_USAGE, UsageType.UNCLASSIFIED, UsageType.UNCLASSIFIED),
                 ContainerUtil.map(rows, UsageInfo2UsageAdapter::getUsageType));
  }

  /** One attribute value spanning {@code lines} lines, with an occurrence on each of them. */
  private static String oneAttributeValuePerLine(int lines) {
    StringBuilder sb = new StringBuilder("<root>\n  <item data=\"");
    for (int i = 0; i < lines; i++) {
      sb.append(NEEDLE).append(' ').append(i).append('\n');
    }
    sb.append("\"/>\n</root>\n");
    return sb.toString();
  }

  /**
   * Presenting the rows in the order the search found them, so every row but the first resumes the lexer, against
   * presenting them backwards, where every row finds the lexer past its own offset and so restarts it from the
   * beginning of the file.
   */
  private void assertRowsReadTheSameForwardsAndBackwards(String text) {
    List<String> backwards = presentedRows(text, true);
    List<String> forwards = presentedRows(text, false);

    assertFalse("nothing was found to present", forwards.isEmpty());
    assertEquals(backwards.size(), forwards.size());
    for (int row = 0; row < forwards.size(); row++) {
      assertEquals("row " + row, backwards.get(row), forwards.get(row));
    }
  }

  /** What the rows of a fresh search over {@code text} say, in offset order, having been presented in the given one. */
  private List<String> presentedRows(String text, boolean backwards) {
    List<UsageInfo2UsageAdapter> rows = rowsOf(text);
    List<UsageInfo2UsageAdapter> order = new ArrayList<>(rows);
    if (backwards) {
      Collections.reverse(order);
    }
    for (UsageInfo2UsageAdapter row : order) {
      row.updateCachedPresentation();
    }
    return ContainerUtil.map(rows, FindInPathPresentationLexerTest::render);
  }

  /** Everything a chunk carries that a lexer left in the wrong token would change: the text, the colour, the boldness. */
  private static String render(UsageInfo2UsageAdapter row) {
    StringBuilder sb = new StringBuilder();
    for (TextChunk chunk : row.getPresentation().getText()) {
      TextAttributes attributes = chunk.getAttributes();
      sb.append('[').append(chunk.getText())
        .append('|').append(attributes.getForegroundColor())
        .append('|').append(attributes.getFontType())
        .append(']');
    }
    return sb.toString();
  }

  /**
   * A row per occurrence, in the order the search finds them -- which is what
   * {@code FindInProjectUtil.processUsagesInFile} hands the popup. Nothing is presented yet: each row computes what it
   * shows the first time it is asked to, off the extractor the rows share.
   */
  private List<UsageInfo2UsageAdapter> rowsOf(String text) {
    configureByText(XmlFileType.INSTANCE, text);
    PsiFile psiFile = getFile();
    FindModel findModel = FindManagerTestUtils.configureFindModel(NEEDLE);
    FindManager findManager = FindManager.getInstance(myProject);

    List<UsageInfo2UsageAdapter> rows = new ArrayList<>();
    int offset = 0;
    while (offset < text.length()) {
      FindResult found = findManager.findString(text, offset, findModel, psiFile.getVirtualFile());
      if (!found.isStringFound()) break;
      rows.add(new UsageInfo2UsageAdapter(new UsageInfo(psiFile, found.getStartOffset(), found.getEndOffset())));
      offset = found.getEndOffset() == offset ? offset + 1 : found.getEndOffset();
    }
    return rows;
  }

  /** The text the rows are presented over, counting every character the lexing of it reads. */
  private static final class CountingText implements CharSequence {

    private final CharSequence myText;
    private final long[] myReads;

    CountingText(@NotNull CharSequence text) {
      this(text, new long[1]);
    }

    private CountingText(@NotNull CharSequence text, long @NotNull [] reads) {
      myText = text;
      myReads = reads;
    }

    long reads() {
      return myReads[0];
    }

    @Override
    public int length() {
      return myText.length();
    }

    @Override
    public char charAt(int index) {
      myReads[0]++;
      return myText.charAt(index);
    }

    @Override
    public @NotNull CharSequence subSequence(int start, int end) {
      return new CountingText(myText.subSequence(start, end), myReads);
    }

    @Override
    public @NotNull String toString() {
      myReads[0] += myText.length();
      return myText.toString();
    }
  }
}
