// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.indentGuide;

import com.intellij.codeInsight.daemon.impl.StringContentIndentUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class JavaTextBlockIndentGuideTest extends LightJavaCodeInsightFixtureTestCase {

  public void testOneLiner() {
    doTest(
      "class Test {" +
      "  void m() {\n" +
      "  String textBlock = \"\"\"\n" +
      "                     |block\n" +
      "                     \"\"\";" +
      "  }" +
      "}");
  }

  public void testWithoutIndent() {
    doTest(
      "class Test {" +
      "  void m() {\n" +
      "  String textBlock = \"\"\"\n" +
      "                     zero\n" +
      "                     indent\n" +
      "\"\"\";" +
      "  }" +
      "}");
  }

  public void testEmpty() {
    doTest(
      "class Test {" +
      "  void m() {\n" +
      "  String textBlock = \"\"\"\n" +
      "                     \"\"\";" +
      "  }" +
      "}");
  }

  public void testTextOnLastLine() {
    doTest(
      "class Test {" +
      "  void m() {\n" +
      "  String textBlock = \"\"\"\n" +
      "                     |text\n" +
      "                     | also text\"\"\";" +
      "  }" +
      "}");
  }

  public void testWithWhitespacesOnly() {
    doTest(
      "class Test {" +
      "  void m() {\n" +
      "  String textBlock = \"\"\"\n" +
      "                     |    \n" +
      "                     |    \n" +
      "                     |    \n" +
      "                     \"\"\";" +
      "  }" +
      "}");
  }

  public void testMultipleTextBlocks() {
    doTest(
      "class Test {" +
      "  void m() {\n" +
      "  String textBlock = \"\"\"\n" +
      "                     |block\n" +
      "                     \"\"\";" +
      "  String oneMore = \"\"\"\n" +
      "                 |also block\n" +
      "                   \"\"\";" +
      "  }" +
      "}");
  }

  private void doTest(@NotNull String text) {
    Set<Indent> expected = extractExpectedIndents(text);
    text = text.replaceAll("\\|", "");
    String filename = getTestName(false) + ".java";
    runTest(filename, text);
    Editor editor = myFixture.getEditor();
    Set<Indent> actual = extractActualIndents(editor);
    assertEquals(String.format("Expected to find %d indents (%s), but found %d (%s)",
                               expected.size(), expected, actual.size(), actual),
                 expected.size(), actual.size());

    Map<Pair<Integer, Integer>, Integer> indentsByLines = byLines(actual);
    for (Indent indent : expected) {
      Integer actualColumn = indentsByLines.get(new Pair<>(indent.nStartLine, indent.nEndLine));
      assertNotNull(String.format("Expected indent at %d for lines from %d to %d",
                                  indent.column, indent.nStartLine, indent.nEndLine),
                    actualColumn);
      int expectedColumn = indent.column;
      assertEquals(String.format("Expected indent at %d for lines from %d to %d, but found it on %d",
                                 expectedColumn, indent.nStartLine, indent.nEndLine, actualColumn),
                   actualColumn.intValue(), expectedColumn);
    }
  }


  private void runTest(@NotNull String filename, @NotNull String text) {
    myFixture.configureByText(filename, text);
    CodeInsightTestFixtureImpl.instantiateAndRun(myFixture.getFile(), myFixture.getEditor(), ArrayUtilRt.EMPTY_INT_ARRAY, false);
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_13;
  }

  @NotNull
  private static Set<Indent> extractExpectedIndents(@NotNull String text) {
    Set<Indent> indents = new HashSet<>();
    String[] lines = LineTokenizer.tokenize(text.toCharArray(), false, true);

    int nLine = 0;
    while (nLine < lines.length) {
      int idx = lines[nLine].indexOf('|');
      nLine++;
      if (idx == -1) {
        continue;
      }
      int nStartLine = nLine - 1;
      while (nLine < lines.length && lines[nLine].indexOf('|') == idx) {
        nLine++;
      }
      indents.add(new Indent(nStartLine, nLine - 1, idx));
    }

    return indents;
  }

  @NotNull
  private static Set<Indent> extractActualIndents(@NotNull Editor editor) {
    Document document = editor.getDocument();
    Map<TextRange, RangeHighlighter> highlighters = StringContentIndentUtil.getIndentHighlighters(editor);
    Set<Indent> indents = new HashSet<>();
    for (Map.Entry<TextRange, RangeHighlighter> entry : highlighters.entrySet()) {
      TextRange range = entry.getKey();
      int nStartLine = document.getLineNumber(range.getStartOffset());
      int nEndLine = document.getLineNumber(range.getEndOffset());
      int indent = StringContentIndentUtil.getIndent(entry.getValue());
      indents.add(new Indent(nStartLine, nEndLine, indent));
    }

    return indents;
  }

  private static Map<Pair<Integer, Integer>, Integer> byLines(@NotNull Set<Indent> indents) {
    return indents.stream().collect(Collectors.toMap(i -> new Pair<>(i.nStartLine, i.nEndLine), i -> i.column));
  }

  private static class Indent {
    private final int nStartLine;
    private final int nEndLine;
    private final int column;

    @Contract(pure = true)
    private Indent(int nStartLine, int nEndLine, int column) {
      this.nStartLine = nStartLine;
      this.nEndLine = nEndLine;
      this.column = column;
    }

    @Contract(value = "null -> false", pure = true)
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Indent indent = (Indent)o;
      return nStartLine == indent.nStartLine &&
             nEndLine == indent.nEndLine &&
             column == indent.column;
    }

    @Override
    public String toString() {
      return "Indent{" +
             "nStartLine=" + nStartLine +
             ", nEndLine=" + nEndLine +
             ", column=" + column +
             '}';
    }

    @Override
    public int hashCode() {
      return Objects.hash(nStartLine, nEndLine, column);
    }
  }
}
