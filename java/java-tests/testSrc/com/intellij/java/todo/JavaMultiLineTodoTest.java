// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.todo;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.testFramework.EditorTestUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class JavaMultiLineTodoTest extends LightDaemonAnalyzerTestCase {
  public void testSuccessiveLineComments() {
    testTodos("// [TODO first line]\n" +
              "//      [second line]");
  }

  public void testSuccessiveLineCommentsAfterEditing() {
    testTodos("// [TODO first line]\n" +
              "// <caret>second line");
    type("     ");
    checkTodos("// [TODO first line]\n" +
               "//      [second line]");
  }

  public void testAllLinesLoseHighlightingWithFirstLine() {
    testTodos("// [TO<caret>DO first line]\n" +
              "//      [second line]");
    delete();
    checkTodos("// TOO first line\n" +
               "//      second line");
  }

  public void testContinuationIsNotOverlappedWithFollowingTodo() {
    testTodos("// [TODO first line]\n" +
              "//  [TODO second line]");
  }

  public void testNoContinuationWithoutProperIndent() {
    testTodos("class C {} // [TODO todo]\n" +
              "//   unrelated comment line");
  }

  public void testContinuationInBlockCommentWithStars() {
    testTodos("/*\n" +
              " * [TODO first line]\n" +
              " *  [second line]\n" +
              " */");
  }

  public void testNewLineBetweenCommentLines() {
    testTodos("class C {\n" +
              "    // [TODO first line]<caret>\n" +
              "    //  [second line]\n" +
              "}");
    type('\n');
    checkTodos("class C {\n" +
               "    // [TODO first line]\n" +
               "    \n" +
               "    //  second line\n" +
               "}");
  }

  private void testTodos(String text) {
    configureFromFileText("Foo.java", text);
    EditorTestUtil.setEditorVisibleSize(myEditor, 1000, 1000); // set visible area for highlighting
    List<TextRange> expectedTodoRanges = extractExpectedTodoRanges(myEditor.getDocument());
    List<HighlightInfo> highlightInfos = doHighlighting();
    List<TextRange> actualTodoRanges = getActualTodoRanges(highlightInfos);
    assertTodoRanges(expectedTodoRanges, actualTodoRanges);
  }

  private void checkTodos(String text) {
    DocumentImpl document = new DocumentImpl(text);
    List<TextRange> expectedTodoRanges = extractExpectedTodoRanges(document);
    List<HighlightInfo> highlightInfos = doHighlighting();
    checkResultByText(document.getText());
    List<TextRange> actualTodoRanges = getActualTodoRanges(highlightInfos);
    assertTodoRanges(expectedTodoRanges, actualTodoRanges);
  }

  private static List<TextRange> extractExpectedTodoRanges(Document document) {
    ArrayList<TextRange> result = new ArrayList<>();
    int offset = 0;
    int startPos;
    while ((startPos = document.getText().indexOf('[', offset)) != -1) {
      int finalStartPos = startPos;
      WriteCommandAction.runWriteCommandAction(ourProject, () -> document.deleteString(finalStartPos, finalStartPos + 1));
      int endPos = document.getText().indexOf(']', startPos);
      if (endPos == -1) break;
      WriteCommandAction.runWriteCommandAction(ourProject, () -> document.deleteString(endPos, endPos + 1));
      result.add(new TextRange(startPos, endPos));
    }
    return result;
  }

  private static List<TextRange> getActualTodoRanges(List<HighlightInfo> highlightInfos) {
    return highlightInfos.stream()
                         .filter(info -> info.type == HighlightInfoType.TODO)
                         .map(info -> TextRange.create(info.getHighlighter()))
                         .sorted(Segment.BY_START_OFFSET_THEN_END_OFFSET)
                         .collect(Collectors.toList());
  }

  private static void assertTodoRanges(List<TextRange> expectedTodoRanges, List<TextRange> actualTodoRanges) {
    assertEquals("Unexpected todos highlihghting", generatePresentation(expectedTodoRanges), generatePresentation(actualTodoRanges));
  }

  private static String generatePresentation(List<TextRange> ranges) {
    StringBuilder b = new StringBuilder(myEditor.getDocument().getText());
    int prevStart = Integer.MAX_VALUE;
    for (int i = ranges.size() - 1; i >= 0; i--) {
      TextRange r = ranges.get(i);
      assertTrue(r.getEndOffset() <= prevStart);
      b.insert(r.getEndOffset(), ']');
      b.insert(prevStart = r.getStartOffset(), '[');
    }
    return b.toString();
  }

  @Override
  protected boolean doInspections() {
    return false;
  }
}
