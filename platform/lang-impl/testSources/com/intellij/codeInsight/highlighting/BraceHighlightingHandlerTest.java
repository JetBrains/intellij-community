// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.testFramework.FileBasedTestCaseHelper;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RunWith(com.intellij.testFramework.Parameterized.class)
@TestDataPath("/testData/../../../platform/lang-impl/testData/editor/braceHighlighter/")
public class BraceHighlightingHandlerTest extends LightPlatformCodeInsightTestCase implements FileBasedTestCaseHelper {
  @Test
  public void testAction() {
    configureByFile(myFileSuffix);
    String result = getEditorTextWithHighlightedBraces(getEditor(), getFile());
    UsefulTestCase.assertSameLinesWithFile(getAnswerFilePath(), result);
  }

  @Nullable
  @Override
  public String getFileSuffix(@NotNull String fileName) {
    return StringUtil.endsWith(fileName, ".txt") ? null : fileName;
  }

  @Override
  public @Nullable String getBaseName(@NotNull String fileAfterSuffix) {
    return StringUtil.endsWith(fileAfterSuffix, ".txt") ? fileAfterSuffix.substring(0, fileAfterSuffix.length() - 4) : null;
  }

  /**
   * @return a text from passed editor with highlighted braces wrapped in {@code <brace></brace>} tags. And {@link <caret>} marker
   */
  public static String getEditorTextWithHighlightedBraces(@NotNull Editor editor, @NotNull PsiFile psiFile) {
    Editor hostEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor);
    List<Pair<Integer, String>> markers = new ArrayList<>();
    Alarm alarm = new Alarm();
    try {
      new BraceHighlightingHandler(psiFile.getProject(), editor, alarm, psiFile).updateBraces();
      RangeHighlighter[] highlighters = editor.getMarkupModel().getAllHighlighters();
      for (RangeHighlighter highlighter : highlighters) {
        if (highlighter.getLayer() == BraceHighlightingHandler.LAYER) {
          markers.add(Pair.create(highlighter.getStartOffset(), "<brace>"));
          markers.add(Pair.create(highlighter.getEndOffset(), "</brace>"));
        }
      }
    }
    finally {
      Disposer.dispose(alarm);
    }

    hostEditor.getCaretModel().getAllCarets().forEach(it -> markers.add(Pair.create(it.getOffset(), "<caret>")));

    StringBuilder result = new StringBuilder(hostEditor.getDocument().getCharsSequence());
    markers.stream()
      .sorted(Comparator.comparingInt(it -> -it.first))
      .forEach(it -> result.insert(it.first, it.second));
    return result.toString();
  }
}
