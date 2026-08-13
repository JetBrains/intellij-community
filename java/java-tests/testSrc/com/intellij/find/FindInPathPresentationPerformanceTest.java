// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.usageView.UsageInfo;
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
