// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.testFramework.FileBasedTestCaseHelper;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.TestDataPath;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;

@RunWith(com.intellij.testFramework.Parameterized.class)
@TestDataPath("/testData/../../../platform/lang-impl/testData/editor/braceHighlighter/")
public class BraceHighlightingHandlerTest extends LightPlatformCodeInsightTestCase implements FileBasedTestCaseHelper {
  private static final String PAIR_MARKER = "<pair>";

  @Test
  public void testAction() {
    runInEdtAndWait(() -> {
      configureByFile(myFileSuffix);
      Editor editor = getEditor();
      final Document document = editor.getDocument();
      int second = document.getText().indexOf(PAIR_MARKER);
      if (second >= 0) {
        WriteCommandAction.runWriteCommandAction(null, () -> document.replaceString(second, second + PAIR_MARKER.length(), ""));
      }
      int first;
      int firstBraceCandidate = document.getText().indexOf(PAIR_MARKER);
      if (firstBraceCandidate >= 0) {
        WriteCommandAction.runWriteCommandAction(null, () -> document.replaceString(firstBraceCandidate, firstBraceCandidate + PAIR_MARKER.length(), ""));
        first = firstBraceCandidate;
      } else {
        first = editor.getCaretModel().getOffset();
      }

      new BraceHighlightingHandler(getProject(), (EditorEx)editor, new Alarm(), getFile()).updateBraces();
      RangeHighlighter[] highlighters = editor.getMarkupModel().getAllHighlighters();
      int braceHighlighters = 0;
      for (RangeHighlighter highlighter : highlighters) {
        if (highlighter.getLayer() == BraceHighlightingHandler.LAYER) {
          braceHighlighters++;
          assertTrue(first == highlighter.getStartOffset() || second == highlighter.getStartOffset());
        }
      }
      assertEquals(second >= 0 ? 2 : 0, braceHighlighters);
    });
  }

  @Nullable
  @Override
  public String getFileSuffix(String fileName) {
    return fileName.contains("-after.") ? null : fileName;
  }
}