// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tools.ide.metrics.benchmark.PerformanceTestUtil;

public class FindInEditorPerformanceTest extends AbstractFindInEditorTest {
  public void testEditingWithSearchResultsShown() {
    init(StringUtil.repeat("cheese\n", 9999)); // just below the limit for occurrences highlighting
    initFind();
    setTextToFind("s");
    assertEquals(9999 + 1 /* cursor highlighting */, getEditor().getMarkupModel().getAllHighlighters().length);
    getEditor().getCaretModel().moveToOffset(0);
    PerformanceTestUtil.newPerformanceTest("typing in editor when a lot of search results are highlighted", () -> {
      for (int i = 0; i < 100; i++) {
        myFixture.type(' ');
      }
    }).start();
  }
}
