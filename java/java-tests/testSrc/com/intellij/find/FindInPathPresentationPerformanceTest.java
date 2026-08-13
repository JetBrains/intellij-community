// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.ChunkExtractor;
import com.intellij.usages.UsageInfo2UsageAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Guards the cost of turning the occurrences Find in Path found into the rows the popup shows -- which is where a file
 * whose text is a single merged token used to spend everything, and not in the search:
 * {@link com.intellij.usages.ChunkExtractor} left its highlighting lexer past the range it had been asked for, so the
 * next occurrence restarted it at offset 0 and paid for a lexer pass over the whole file.
 * <p>
 * What the search itself costs is guarded by {@link FindInCommentsAndLiteralsPerformanceTest}; here it is incidental and
 * runs outside what is measured.
 */
@PerformanceUnitTest
public class FindInPathPresentationPerformanceTest extends DaemonAnalyzerTestCase {

  /**
   * One attribute value spanning {@code occurrences} lines. The XML lexer returns every character of an attribute value
   * as its own token and {@code MergingLexerAdapter} merges them, so however long the value is it stays one token --
   * while every occurrence still sits on its own line, and so becomes its own row, merging into nothing.
   */
  private static String manyLinesInOneLiteral(int occurrences) {
    StringBuilder sb = new StringBuilder("<root>\n  <item data=\"");
    for (int i = 0; i < occurrences; i++) {
      sb.append("needle ").append(i).append('\n');
    }
    sb.append("\"/>\n</root>\n");
    return sb.toString();
  }

  /** Everything in one attribute value on one line, so every occurrence merges into the row the first one started. */
  private static String oneHugeLiteral(int occurrences) {
    StringBuilder sb = new StringBuilder("<root>\n  <item data=\"");
    for (int i = 0; i < occurrences; i++) {
      sb.append("needle ").append(i).append(' ');
    }
    sb.append("\"/>\n</root>\n");
    return sb.toString();
  }

  /**
   * Pins the complexity rather than a millisecond count, which is machine-specific: restarting the lexer per occurrence
   * made four times as many of them -- in a file four times the size -- cost some sixteen times as much, where one pass
   * per row is proportional to the file.
   */
  public void testPresentingOccurrencesSharingOneTokenScalesWithTheirCount() {
    millisOfPresenting(1000); // warms up the JIT and whatever the highlighter and the file type resolve to

    long thousand = millisOfPresenting(1000);
    long fourThousand = millisOfPresenting(4000);
    assertTrue("4000 occurrences took " + fourThousand + " ms against " + thousand + " ms for 1000",
               fourThousand <= 6 * thousand + 100);
  }

  /**
   * Presenting one row must not cost the occurrences merged into it. Only the ones inside the fragment the row shows can
   * appear in it, and there are a handful of those however long the line is -- but reading on past them resolved a marker
   * per occurrence of the whole line, for every token of the fragment.
   */
  public void testPresentingOneRowDoesNotCostTheOccurrencesMergedIntoIt() {
    millisOfRepresenting(500); // warms up the JIT and whatever the highlighter and the file type resolve to

    long fiveHundred = millisOfRepresenting(500);
    long fourThousand = millisOfRepresenting(4000);
    assertTrue("a row of 4000 occurrences took " + fourThousand + " ms against " + fiveHundred + " ms for 500",
               fourThousand <= 3 * fiveHundred + 100);
  }

  private static final int PRESENTATIONS = 2000;

  /**
   * How long it takes to present the same row {@value #PRESENTATIONS} times. Building it is not measured, so what is left
   * is what one row costs -- and it must not grow with the occurrences it holds.
   */
  private long millisOfRepresenting(int occurrences) {
    UsageInfo2UsageAdapter row = mergedRow(oneHugeLiteral(occurrences), occurrences);
    ChunkExtractor extractor = ChunkExtractor.getExtractor(getFile());
    CharSequence chars = getFile().getFileDocument().getCharsSequence();
    int start = row.getNavigationOffset();
    int end = start + OFFSET_AFTER_TO_SHOW;

    long startedAt = System.nanoTime();
    for (int i = 0; i < PRESENTATIONS; i++) {
      extractor.appendTextChunks(row, chars, start, end, true, new ArrayList<>());
    }
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
  }

  /** As much of a long line as a row shows past the occurrence it is anchored at. */
  private static final int OFFSET_AFTER_TO_SHOW = 100;

  /** The single row every occurrence of the file's one long line merges into, as the search builds it. */
  private UsageInfo2UsageAdapter mergedRow(String text, int expectedOccurrences) {
    List<UsageInfo> usages = findOccurrences(text);
    assertEquals(expectedOccurrences, usages.size());

    UsageInfo2UsageAdapter row = null;
    for (UsageInfo usage : usages) {
      UsageInfo2UsageAdapter adapter = new UsageInfo2UsageAdapter(usage);
      if (row != null) assertTrue("occurrences of one line have to merge", adapter.merge(row));
      row = adapter;
    }
    assertNotNull(row);
    row.updateCachedPresentation();
    return row;
  }

  private long millisOfPresenting(int occurrences) {
    List<UsageInfo> usages = findOccurrences(manyLinesInOneLiteral(occurrences));
    assertEquals(occurrences, usages.size());

    long startedAt = System.nanoTime();
    for (UsageInfo usage : usages) {
      new UsageInfo2UsageAdapter(usage).updateCachedPresentation();
    }
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
  }

  /** What {@code FindInProjectUtil.processUsagesInFile} hands the popup, collected up front so it is not measured. */
  private List<UsageInfo> findOccurrences(String text) {
    configureByText(XmlFileType.INSTANCE, text);
    PsiFile psiFile = getFile();
    FindModel findModel = FindManagerTestUtils.configureFindModel("needle");
    FindManager findManager = FindManager.getInstance(myProject);

    List<UsageInfo> usages = new ArrayList<>();
    int offset = 0;
    while (offset < text.length()) {
      FindResult found = findManager.findString(text, offset, findModel, psiFile.getVirtualFile());
      if (!found.isStringFound()) break;
      usages.add(new UsageInfo(psiFile, found.getStartOffset(), found.getEndOffset()));
      offset = found.getEndOffset() == offset ? offset + 1 : found.getEndOffset();
    }
    return usages;
  }
}
