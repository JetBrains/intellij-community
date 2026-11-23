// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.find;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.DaemonAnalyzerFixture;
import com.intellij.find.FindManager;
import com.intellij.find.FindManagerTestUtils;
import com.intellij.find.FindModel;
import com.intellij.find.FindResult;
import com.intellij.find.impl.livePreview.LivePreviewController;
import com.intellij.find.impl.livePreview.SearchResults;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.concurrent.TimeUnit;

import static com.intellij.codeInsight.daemon.DaemonAnalyzerFixture.daemonAnalyzerFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the cost of searching comments and string literals, which used to restart the lexer once per occurrence -- for
 * the {@code EXCEPT_*} contexts when building their skip-range set, and for the {@code IN_*} ones on every occurrence
 * handed to the caller.
 * <p>
 * Both shapes here defeated the incremental resume and were quadratic: with 8000 occurrences building the skip ranges of
 * {@link #oneGiantTag} took ~9.6 s and of {@link #oneHugeLiteral} ~5.4 s, against ~50 ms for the same number of
 * occurrences spread over ordinary markup, and walking {@code IN_STRING_LITERALS} over the latter held a thread for 6.3 s.
 * Collecting the occurrences in a single pass brought them all under 10 ms.
 */
@StressTestApplication
@PerformanceUnitTest
public class FindInCommentsAndLiteralsPerformanceTest {
  private static final int OCCURRENCES = 8000;

  private final TestFixture<DaemonAnalyzerFixture> daemonFixture = daemonAnalyzerFixture(JavaTestUtil.getJavaTestDataPath());
  private FindManager myFindManager;

  @BeforeEach
  void setUp() {
    myFindManager = FindManager.getInstance(daemonFixture.get().getProject());
  }

  /** Every occurrence inside one attribute value, so they all share a single lexer token. */
  private static String oneHugeLiteral() {
    return oneHugeLiteral(OCCURRENCES);
  }

  private static String oneHugeLiteral(int occurrences) {
    StringBuilder sb = new StringBuilder("<root>\n  <item data=\"");
    for (int i = 0; i < occurrences; i++) {
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

  @Test
  public void testExceptLiteralsWhenAllOccurrencesShareOneToken() {
    benchmarkFindString("find except literals, all occurrences in one token", oneHugeLiteral());
  }

  @Test
  public void testExceptLiteralsWhenLexerNeverReturnsToInitialState() {
    benchmarkFindString("find except literals, one element spanning the file", oneGiantTag());
  }

  /**
   * What the {@code IN_*} contexts cost is not one {@link FindManager#findString} call -- that one stops at the first
   * occurrence -- but the walk over all of them, which is what every caller does: the find bar to count the matches,
   * Find in Files and replace-all to report them. Each of those calls used to lex the file again.
   * <p>
   * A fresh model and file per iteration for the same reason the {@code EXCEPT_*} benchmark needs them: what is
   * collected is cached against both.
   */
  private void benchmarkFindAll(String name, String text) {
    Benchmark.newBenchmark(name, () -> {
      for (int i = 0; i < 3; i++) {
        LightVirtualFile file = new LightVirtualFile("perf" + i + ".xml", text);
        assertEquals(OCCURRENCES, countOccurrences(text, inStringLiterals(), file));
      }
    }).start();
  }

  private int countOccurrences(String text, FindModel findModel, VirtualFile file) {
    int count = 0;
    int offset = 0;
    while (offset < text.length()) {
      FindResult found = myFindManager.findString(text, offset, findModel, file);
      if (!found.isStringFound()) break;
      count++;
      offset = found.getEndOffset() == offset ? offset + 1 : found.getEndOffset();
    }
    return count;
  }

  private static FindModel inStringLiterals() {
    FindModel findModel = FindManagerTestUtils.configureFindModel("needle");
    findModel.setSearchContext(FindModel.SearchContext.IN_STRING_LITERALS);
    return findModel;
  }

  @Test
  public void testInLiteralsWhenAllOccurrencesShareOneToken() {
    benchmarkFindAll("find all in literals, all occurrences in one token", oneHugeLiteral());
  }

  @Test
  public void testInLiteralsWhenLexerNeverReturnsToInitialState() {
    benchmarkFindAll("find all in literals, one element spanning the file", oneGiantTag());
  }

  /**
   * Pins the complexity rather than a millisecond count, which is machine-specific: lexing the file once per occurrence
   * made four times as many of them cost ~14 times as much (130 ms at 1000, 1895 ms at 4000), where one pass over the
   * file is proportional to its size.
   */
  @Test
  public void testFindAllInLiteralsScalesWithOccurrenceCount() {
    millisOfFindAll(1000); // warms up the JIT and whatever the highlighter and the file type resolve to

    long thousand = millisOfFindAll(1000);
    long fourThousand = millisOfFindAll(4000);
    assertTrue(fourThousand <= 6 * thousand + 100,
               "4000 occurrences took " + fourThousand + " ms against " + thousand + " ms for 1000");
  }

  private long millisOfFindAll(int occurrences) {
    String text = oneHugeLiteral(occurrences);
    LightVirtualFile file = new LightVirtualFile("scaling.xml", text);
    FindModel findModel = inStringLiterals();

    long startedAt = System.nanoTime();
    int found = countOccurrences(text, findModel, file);
    long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

    assertEquals(occurrences, found);
    return millis;
  }

  /**
   * The editor's find bar reruns the whole find-all pass on every keystroke, so what has to stay cheap is the update and
   * not just one {@link FindManager#findString} call. It runs each of them against a fresh {@link FindModel} - which is
   * what {@link com.intellij.find.impl.livePreview.LivePreviewController#updateInBackground} gives it, and what makes
   * everything the search caches be collected again per update.
   */
  private void benchmarkLivePreviewUpdate(String testMethodName, String name, FindModel.SearchContext context, int expectedMatches) {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByText(XmlFileType.INSTANCE, oneHugeLiteral());

    EdtTestUtil.runInEdtAndWait(() -> {
      SearchResults searchResults = new SearchResults(fixture.getEditor(), fixture.getProject());
      LivePreviewController controller = new LivePreviewController(searchResults, null, fixture.getTestRootDisposable());
      FindModel findModel = FindManagerTestUtils.configureFindModel("needle");
      findModel.setSearchContext(context);

      controller.on();
      Benchmark.newBenchmark(name, () -> {
        for (int i = 0; i < 3; i++) {
          controller.updateInBackground(findModel, false);
          assertEquals(expectedMatches, searchResults.getMatchesCount());
        }
      }).start(testMethodName);
    });
  }

  /**
   * The worst case for the {@code EXCEPT_*} skip ranges: every occurrence but the last is inside the literal, so the pass
   * walks past all {@value #OCCURRENCES} of them to hand out the single match it can keep -- only
   * {@code <tail>needle</tail>} is outside the literal.
   */
  @Test
  public void testLivePreviewUpdateWhenAllOccurrencesShareOneToken(TestInfo testInfo) {
    benchmarkLivePreviewUpdate(getClass().getName() + "." + testInfo.getTestMethod().orElseThrow().getName(), "live preview update except literals, all occurrences in one token",
                               FindModel.SearchContext.EXCEPT_STRING_LITERALS, 1);
  }

  /** The update IJPL-252883 was reported for: the profile it came with has one of these holding a thread for 6.3 s. */
  @Test
  public void testLivePreviewUpdateInLiteralsWhenAllOccurrencesShareOneToken(TestInfo testInfo) {
    benchmarkLivePreviewUpdate(getClass().getName() + "." + testInfo.getTestMethod().orElseThrow().getName(), "live preview update in literals, all occurrences in one token",
                               FindModel.SearchContext.IN_STRING_LITERALS, OCCURRENCES);
  }
}
