// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.indentGuide;

import com.intellij.codeInsight.daemon.impl.StringContentIndentUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.TextRange;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JavaTextBlockIndentGuideTest extends BaseIndentGuideTest {

  public void testOneLiner() {
    doTest(
      """
        class Test {
          void m() {
          String textBlock = ""\"
                             |block
                             ""\";
          }
        }
        """);
  }

  public void testWithoutIndent() {
    doTest(
      """
        class Test {
          void m() {
          String textBlock = ""\"
                             zero
                             indent
        ""\";
          }
        }
        """);
  }

  public void testEmpty() {
    doTest(
      """
        class Test {
          void m() {
          String textBlock = ""\"
                             ""\";
          }
        }
        """);
  }

  public void testTextOnLastLine() {
    doTest(
      """
        class Test {
          void m() {
          String textBlock = ""\"
                             |text
                             | also text""\";
          }
        }
        """);
  }

  public void testWithWhitespacesOnly() {
    doTest(
      """
        class Test {
          void m() {
          String textBlock = ""\"
                             |   \s
                             |   \s
                             |   \s
                             ""\";
          }
        }
        """);
  }

  public void testMultipleTextBlocks() {
    doTest(
      """
        class Test {
          void m() {
          String textBlock = ""\"
                             |block
                             ""\";
          String oneMore = ""\"
                         |also block
                           ""\";
          }
        }
        """);
  }

  public void testTabsOnlyIndent() {
    doTest("""
             public class TextBlock {

               String text = ""\"
             		|1
             		|2
             		|3
             		""\";
             }""");
  }

  public void testMixedIndent() {
    doTest("""
             public class TextBlock {

               String text = ""\"
             	1
              2
             	3
             	""\";
             }""");
  }

  private void doTest(@NotNull String text) {
    doTest(text, TextBlockIndentGuidesProvider::create);
  }

  private static final class TextBlockIndentGuidesProvider implements IndentGuidesProvider {

    private final List<Guide> myGuides;

    @Contract(pure = true)
    private TextBlockIndentGuidesProvider(List<Guide> guides) {
      myGuides = guides;
    }

    @NotNull
    @Override
    public List<Guide> getGuides() {
      return myGuides;
    }

    @NotNull
    private static TextBlockIndentGuidesProvider create(@NotNull JavaCodeInsightTestFixture fixture) {
      List<Guide> guides = extractTextBlockGuides(fixture.getEditor());
      return new TextBlockIndentGuidesProvider(guides);
    }

    @NotNull
    private static List<Guide> extractTextBlockGuides(@NotNull Editor editor) {
      Document document = editor.getDocument();
      Map<TextRange, RangeHighlighter> highlighters = StringContentIndentUtil.getIndentHighlighters(editor);
      List<Guide> guides = new ArrayList<>();
      for (Map.Entry<TextRange, RangeHighlighter> entry : highlighters.entrySet()) {
        TextRange range = entry.getKey();
        int startLine = document.getLineNumber(range.getStartOffset());
        int endLine = document.getLineNumber(range.getEndOffset());
        int indent = StringContentIndentUtil.getIndent(entry.getValue());
        guides.add(new Guide(startLine - 1, endLine + 1, indent));
      }
      return guides;
    }
  }
}
