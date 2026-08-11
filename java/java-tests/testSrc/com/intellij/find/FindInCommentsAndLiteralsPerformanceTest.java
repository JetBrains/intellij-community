// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;

/**
 * Guards the cost of building the {@code EXCEPT_*} skip-range set, which used to restart the lexer once per occurrence.
 * <p>
 * Both shapes here defeated the incremental resume and were quadratic: with 8000 occurrences {@link #oneGiantTag} took
 * ~9.6 s and {@link #oneHugeLiteral} ~5.4 s, against ~50 ms for the same number of occurrences spread over ordinary
 * markup. Collecting the ranges in a single pass brought both under 10 ms.
 */
@PerformanceUnitTest
public class FindInCommentsAndLiteralsPerformanceTest extends DaemonAnalyzerTestCase {
  private static final int OCCURRENCES = 8000;

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

  /** Every occurrence inside one attribute value, so they all share a single lexer token. */
  private static String oneHugeLiteral() {
    StringBuilder sb = new StringBuilder("<root>\n  <item data=\"");
    for (int i = 0; i < OCCURRENCES; i++) {
      sb.append("needle ").append(i).append(' ');
    }
    sb.append("\"/>\n  <tail>needle</tail>\n</root>\n");
    return sb.toString();
  }

  /** One element whose attribute list spans the file, so {@code lexer.getState() == 0} never holds inside it. */
  private static String oneGiantTag() {
    StringBuilder sb = new StringBuilder("<root>\n  <item\n");
    for (int i = 0; i < OCCURRENCES; i++) {
      sb.append("      a").append(i).append("=\"a needle here\"\n");
    }
    sb.append("  />\n  <tail>needle</tail>\n</root>\n");
    return sb.toString();
  }

  /**
   * The skip-range set is cached per (model, file, text length), so every iteration needs a fresh model and file —
   * otherwise only the first one does any work.
   */
  private void benchmarkFindString(String name, String text) {
    Benchmark.newBenchmark(name, () -> {
      for (int i = 0; i < 3; i++) {
        FindModel findModel = FindManagerTestUtils.configureFindModel("needle");
        findModel.setSearchContext(FindModel.SearchContext.EXCEPT_STRING_LITERALS);
        LightVirtualFile file = new LightVirtualFile("perf" + i + ".xml", text);
        assertTrue(myFindManager.findString(text, 0, findModel, file).isStringFound());
      }
    }).start();
  }

  public void testExceptLiteralsWhenAllOccurrencesShareOneToken() {
    benchmarkFindString("find except literals, all occurrences in one token", oneHugeLiteral());
  }

  public void testExceptLiteralsWhenLexerNeverReturnsToInitialState() {
    benchmarkFindString("find except literals, one element spanning the file", oneGiantTag());
  }
}
