// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.find;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.DaemonAnalyzerFixture;
import com.intellij.find.FindManager;
import com.intellij.find.FindManagerTestUtils;
import com.intellij.find.FindModel;
import com.intellij.find.FindResult;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.TextChunk;
import com.intellij.usages.UsageInfo2UsageAdapter;
import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInsight.daemon.DaemonAnalyzerFixture.daemonAnalyzerFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the row a find-in-path result becomes has to say: the occurrences of one line are merged into a single row, and
 * every one of them the row shows has to be shown as a match.
 */
@TestApplication
public class FindInPathPresentationTest {
  private final TestFixture<DaemonAnalyzerFixture> daemonFixture = daemonAnalyzerFixture(JavaTestUtil.getJavaTestDataPath());

  private static final String NEEDLE = "needle";

  @Test
  public void testEveryOccurrenceOfAShortLineIsHighlighted() {
    UsageInfo2UsageAdapter row = mergedRow("<root>\n  <item data=\"needle a needle b needle\"/>\n</root>\n");

    assertEquals(List.of(NEEDLE, NEEDLE, NEEDLE), matchedTexts(ReadAction.computeBlocking(() -> row.getPresentation().getText())));
  }

  /**
   * A line too long to show whole: the row shows a fragment of it around the first occurrence, so the ones past the
   * fragment have nowhere to be shown -- but the ones inside it are more than just the first, and they all have to be
   * highlighted even though thousands of further occurrences are merged into the same row.
   */
  @Test
  public void testOccurrencesInsideTheShownFragmentOfALongLineAreHighlighted() {
    StringBuilder sb = new StringBuilder("<root>\n  <item data=\"");
    for (int i = 0; i < 1000; i++) {
      sb.append(NEEDLE).append(' ').append(i).append(' ');
    }
    sb.append("\"/>\n</root>\n");

    UsageInfo2UsageAdapter row = mergedRow(sb.toString());
    TextChunk[] chunks = ReadAction.computeBlocking(() -> row.getPresentation().getText());
    List<String> matched = matchedTexts(chunks);

    assertFalse(matched.size() < 2, "the fragment shown around the first occurrence holds several of them");
    for (String text : matched) {
      assertEquals(NEEDLE, text);
    }
    // Everything the row shows is one run of the line: the chunks after the line number join back into it.
    StringBuilder shown = new StringBuilder();
    for (int i = 1; i < chunks.length; i++) {
      shown.append(chunks[i].getText());
    }
    assertTrue(sb.toString().contains(shown.toString()), "<" + shown + "> is not a fragment of the line");
  }

  private static List<String> matchedTexts(TextChunk[] chunks) {
    List<String> matched = new ArrayList<>();
    for (TextChunk chunk : chunks) {
      if ((chunk.getAttributes().getFontType() & Font.BOLD) != 0) {
        matched.add(chunk.getText());
      }
    }
    return matched;
  }

  /** The single row every occurrence of the file's one long line merges into, as the search builds it. */
  private UsageInfo2UsageAdapter mergedRow(String text) {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByText(XmlFileType.INSTANCE, text);
    PsiFile psiFile = fixture.getFile();
    FindModel findModel = FindManagerTestUtils.configureFindModel(NEEDLE);
    FindManager findManager = FindManager.getInstance(fixture.getProject());
    return ReadAction.computeBlocking(() -> mergedRow(text, psiFile, findModel, findManager));
  }

  private static UsageInfo2UsageAdapter mergedRow(String text, PsiFile psiFile, FindModel findModel, FindManager findManager) {
    UsageInfo2UsageAdapter row = null;
    int offset = 0;
    while (offset < text.length()) {
      FindResult found = findManager.findString(text, offset, findModel, psiFile.getVirtualFile());
      if (!found.isStringFound()) break;
      UsageInfo2UsageAdapter adapter = new UsageInfo2UsageAdapter(
        new UsageInfo(psiFile, found.getStartOffset(), found.getEndOffset()));
      if (row != null) assertTrue(adapter.merge(row), "occurrences of one line have to merge");
      row = adapter;
      offset = found.getEndOffset() == offset ? offset + 1 : found.getEndOffset();
    }
    assertNotNull(row);
    row.updateCachedPresentation();
    return row;
  }
}
