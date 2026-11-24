// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.source.javadoc.PsiSnippetDocTagImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class PsiSnippetDocTagContentTest extends LightJavaCodeInsightFixtureTestCase {
  private static final String TEXT = """
    /**
     * {@snippet :
     *   if (a) {
     *     b();
     *   }
     * }
     */
    class A {}""";

  public void testContentRangesStayCachedUntilThePsiChanges() {
    myFixture.configureByText("A.java", TEXT);
    List<TextRange> first = snippet().getContentRanges();
    assertSame(first, snippet().getContentRanges());

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      Document document = myFixture.getEditor().getDocument();
      document.insertString(document.getText().indexOf("b();"), "c(); ");
      PsiDocumentManager.getInstance(getProject()).commitDocument(document);
    });

    List<TextRange> second = snippet().getContentRanges();
    assertNotSame(first, second);
    assertEquals("  c(); b();\n", second.get(1).substring(snippet().getText()));
  }

  public void testDecodeTakesTheLinesInsideTheRange() {
    myFixture.configureByText("A.java", TEXT);
    PsiSnippetDocTagImpl snippet = snippet();
    List<TextRange> ranges = snippet.getContentRanges();
    assertEquals(List.of("if (a) {\n", "  b();\n", "}"), ranges.stream().map(range -> range.substring(snippet.getText())).toList());

    assertEquals("if (a) {\n  b();\n}", decode(snippet, new TextRange(ranges.getFirst().getStartOffset(), ranges.getLast().getEndOffset())));
    assertEquals("  b();\n", decode(snippet, ranges.get(1)));
    assertEquals("  b();\n}", decode(snippet, new TextRange(ranges.get(1).getStartOffset(), ranges.get(2).getEndOffset())));
  }

  public void testDecodeOfARangeWithoutAWholeLineFails() {
    myFixture.configureByText("A.java", TEXT);
    PsiSnippetDocTagImpl snippet = snippet();
    TextRange line = snippet.getContentRanges().get(1);
    StringBuilder out = new StringBuilder();
    assertFalse(snippet.createLiteralTextEscaper().decode(new TextRange(line.getStartOffset() + 1, line.getEndOffset()), out));
    assertEmpty(out.toString());
  }

  private @NotNull PsiSnippetDocTagImpl snippet() {
    return PsiTreeUtil.findChildOfType(getFile(), PsiSnippetDocTagImpl.class);
  }

  private static @NotNull String decode(@NotNull PsiSnippetDocTagImpl snippet, @NotNull TextRange range) {
    StringBuilder out = new StringBuilder();
    assertTrue(snippet.createLiteralTextEscaper().decode(range, out));
    return out.toString();
  }
}
