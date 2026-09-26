// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find;

import com.intellij.find.impl.livePreview.LivePreview;
import com.intellij.find.impl.livePreview.LivePreviewController;
import com.intellij.find.impl.livePreview.SearchResults;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.ui.UIUtil;

import java.util.concurrent.TimeUnit;

@PerformanceUnitTest
public class FindInEditorPerformanceTest extends AbstractFindInEditorTest {
  public void testEditingWithSearchResultsShown() {
    init(StringUtil.repeat("cheese\n", 9999)); // just below the limit for occurrences highlighting
    initFind();
    setTextToFind("s");
    assertEquals(9999 + 1 /* cursor highlighting */, getEditor().getMarkupModel().getAllHighlighters().length);
    getEditor().getCaretModel().moveToOffset(0);
    Benchmark.newBenchmark("typing in editor when a lot of search results are highlighted", () -> {
      for (int i = 0; i < 100; i++) {
        myFixture.type(' ');
      }
    }).start();
  }

  /**
   * The same scenario as above, with the search running where a production one runs: on the pooled thread, in
   * cancellable chunks, publishing its matches as it finds them. The benchmark above cannot cover that, because an
   * update in a test runs synchronously on the EDT, and the EDT is the one place a search is never chunked - it has no
   * write action to yield the read lock to. So the path every real Find in the editor takes had, until this benchmark,
   * nothing measuring it.
   * <p>
   * Nothing here waits for a search to finish, because in this scenario no search ever does: every keystroke cancels
   * the one in flight and starts another. That is what should make typing cheap - the find pass is not on the EDT and
   * not in the way - and what this benchmark exists to check, because it is not what happens. A CPU snapshot of typing
   * into an editor with 9999 matches shown spends 54% of its EDT time inside {@link LivePreview} creating and removing
   * range highlighters, and none of it inside the search: 15% goes to the {@code EditorImpl.onHighlighterChanged}
   * every one of them fires, and 13% to the interval tree query {@code findExistingHighlighter} makes per occurrence.
   * <p>
   * The volume behind that is the point. Each restarted search publishes a first chunk, whose full update runs
   * {@code clearUnusedHighlighters} and so drops every highlighter the previous search left beyond that chunk; the
   * chunks that would put them back never arrive, because the next keystroke cancels the search. Measured over the 100
   * keystrokes below, that is ~5000 range highlighters created and ~5000 removed per keystroke, against 3 of each when
   * the same search runs unchunked.
   *
   * @see #testEditingWithSearchResultsShown for the same typing measured against an unchunked search
   */
  public void testEditingWhileTheSearchRunsInBackground() {
    TestModeFlags.set(LivePreviewController.ourTestingBackgroundUpdate, true, getTestRootDisposable());
    init(StringUtil.repeat("cheese\n", 9999)); // just below the limit for occurrences highlighting
    initFind();
    setTextToFind("s");
    waitForTheSearchToSettle();
    assertEquals(9999 + 1 /* cursor highlighting */, getEditor().getMarkupModel().getAllHighlighters().length);
    getEditor().getCaretModel().moveToOffset(0);
    Benchmark.newBenchmark("typing in editor while a lot of search results are highlighted in background", () -> {
      for (int i = 0; i < 100; i++) {
        myFixture.type(' ');
        // What the EDT does between two keystrokes in production, and the only thing that lets the live preview apply
        // what the search it is about to lose has published. Typing does not wait for the search itself: a keystroke
        // cancels it, and how long typing takes despite that is the measurement.
        UIUtil.dispatchAllInvocationEvents();
      }
      waitForTheSearchToSettle(); // so that every attempt starts from the state the first one did
    }).start();
  }

  /**
   * Pumps the event queue until the search has settled: a chunked search runs off the EDT and reaches the editor
   * through the event queue, one chunk at a time, and a chunk that loses its read action to the typing is retried.
   */
  private void waitForTheSearchToSettle() {
    SearchResults searchResults = getEditorSearchComponent().getSearchResults();
    long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(1);
    while (searchResults.isUpdating()) {
      assertTrue("the search did not settle", System.nanoTime() < deadline);
      UIUtil.dispatchAllInvocationEvents();
      Thread.yield(); // the search itself is on a pooled thread, and spinning the EDT flat out only starves it
    }
  }

  public void testReplacePerformance() {
    String aas = StringUtil.repeat("a", 100);
    String text = StringUtil.repeat(aas + "\n" + StringUtil.repeat("aaaaasdbbbbbbbbbbbbbbbbb\n", 100), 1000);
    String bbs = StringUtil.repeat("b", 100);
    String repl = StringUtil.replace(text, aas, bbs);
    init(text);
    Editor editor = getEditor();
    FindModel findModel = new FindModel();
    LivePreview.ourTestOutput = null;

    try {
      initFind();
      findModel.setReplaceState(true);
      findModel.setPromptOnReplace(false);

      Benchmark.newBenchmark("replace", ()->{
        for (int i=0; i<25; i++) {
          findModel.   setStringToFind(aas);
          findModel.setStringToReplace(bbs);
          FindUtil.replace(getProject(), editor, 0, findModel);
          assertEquals(repl, editor.getDocument().getText());
          findModel.   setStringToFind(bbs);
          findModel.setStringToReplace(aas);
          FindUtil.replace(getProject(), editor, 0, findModel);
          assertEquals(text, editor.getDocument().getText());
        }
      }).start();
    }
    finally {
      EditorFactory.getInstance().releaseEditor(editor);
    }
  }
}
