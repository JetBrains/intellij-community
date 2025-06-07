// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find;

import com.intellij.find.impl.livePreview.LivePreview;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;

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
