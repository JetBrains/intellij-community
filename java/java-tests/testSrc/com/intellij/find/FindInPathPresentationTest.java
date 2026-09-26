// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.psi.PsiFile;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.TextChunk;
import com.intellij.usages.UsageInfo2UsageAdapter;

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

/**
 * What the row a find-in-path result becomes has to say: the occurrences of one line are merged into a single row, and
 * every one of them the row shows has to be shown as a match.
 */
public class FindInPathPresentationTest extends DaemonAnalyzerTestCase {

  private static final String NEEDLE = "needle";

  public void testEveryOccurrenceOfAShortLineIsHighlighted() {
    UsageInfo2UsageAdapter row = mergedRow("<root>\n  <item data=\"needle a needle b needle\"/>\n</root>\n");

    assertEquals(List.of(NEEDLE, NEEDLE, NEEDLE), matchedTexts(row.getPresentation().getText()));
  }

  /**
   * A line too long to show whole: the row shows a fragment of it around the first occurrence, so the ones past the
   * fragment have nowhere to be shown -- but the ones inside it are more than just the first, and they all have to be
   * highlighted even though thousands of further occurrences are merged into the same row.
   */
  public void testOccurrencesInsideTheShownFragmentOfALongLineAreHighlighted() {
    StringBuilder sb = new StringBuilder("<root>\n  <item data=\"");
    for (int i = 0; i < 1000; i++) {
      sb.append(NEEDLE).append(' ').append(i).append(' ');
    }
    sb.append("\"/>\n</root>\n");

    TextChunk[] chunks = mergedRow(sb.toString()).getPresentation().getText();
    List<String> matched = matchedTexts(chunks);

    assertFalse("the fragment shown around the first occurrence holds several of them", matched.size() < 2);
    for (String text : matched) {
      assertEquals(NEEDLE, text);
    }
    // Everything the row shows is one run of the line: the chunks after the line number join back into it.
    StringBuilder shown = new StringBuilder();
    for (int i = 1; i < chunks.length; i++) {
      shown.append(chunks[i].getText());
    }
    assertTrue("<" + shown + "> is not a fragment of the line", sb.toString().contains(shown.toString()));
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
    configureByText(XmlFileType.INSTANCE, text);
    PsiFile psiFile = getFile();
    FindModel findModel = FindManagerTestUtils.configureFindModel(NEEDLE);
    FindManager findManager = FindManager.getInstance(myProject);

    UsageInfo2UsageAdapter row = null;
    int offset = 0;
    while (offset < text.length()) {
      FindResult found = findManager.findString(text, offset, findModel, psiFile.getVirtualFile());
      if (!found.isStringFound()) break;
      UsageInfo2UsageAdapter adapter = new UsageInfo2UsageAdapter(
        new UsageInfo(psiFile, found.getStartOffset(), found.getEndOffset()));
      if (row != null) assertTrue("occurrences of one line have to merge", adapter.merge(row));
      row = adapter;
      offset = found.getEndOffset() == offset ? offset + 1 : found.getEndOffset();
    }
    assertNotNull(row);
    row.updateCachedPresentation();
    return row;
  }
}
