// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.highlighting;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.platform.bazel.runfiles.BazelLabel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.common.BazelTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.RunInEdt;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
@RunInEdt(writeIntent = true)
public class BraceHighlightingHandlerTest {
  @SuppressWarnings("deprecation")
  private final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  @BeforeEach
  void setUp() {
    codeInsightFixture.get().setTestDataPath(getTestDataPath());
  }

  private static @NotNull String getTestDataPath() {
    if (BazelTestUtil.isUnderBazelTest()) {
      var label = BazelLabel.Companion.fromString("@community//platform/lang-impl:testData");
      return BazelTestUtil.getFileFromBazelRuntime(label).toAbsolutePath().resolve("editor/braceHighlighter").toString().concat("/");
    }
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/platform/lang-impl/testData/editor/braceHighlighter/";
  }

  static Stream<String> dataFiles() {
    return Stream.of(new File(getTestDataPath()).list()).filter(name -> !name.endsWith(".txt")).sorted();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("dataFiles")
  public void testAction(@NotNull String fileName) {
    codeInsightFixture.get().configureByFile(fileName);
    String result = getEditorTextWithHighlightedBraces(codeInsightFixture.get().getEditor(), codeInsightFixture.get().getFile());
    PlatformTestUtil.assertSameLinesWithFile(getTestDataPath() + fileName + ".txt", result);
  }

  /**
   * @return a text from passed editor with highlighted braces wrapped in {@code <brace></brace>} tags. And {@link <caret>} marker
   */
  public static String getEditorTextWithHighlightedBraces(@NotNull Editor editor, @NotNull PsiFile psiFile) {
    Editor hostEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor);
    List<Pair<Integer, String>> markers = new ArrayList<>();
    Alarm alarm = psiFile.getProject().getService(BackgroundHighlighter.class).alarm;
    new BraceHighlightingHandler(psiFile.getProject(), editor, alarm, psiFile).updateBraces(true);
    RangeHighlighter[] highlighters = editor.getMarkupModel().getAllHighlighters();
    for (RangeHighlighter highlighter : highlighters) {
      if (highlighter.getLayer() == BraceHighlightingHandler.LAYER) {
        markers.add(Pair.create(highlighter.getStartOffset(), "<brace>"));
        markers.add(Pair.create(highlighter.getEndOffset(), "</brace>"));
      }
    }

    hostEditor.getCaretModel().getAllCarets().forEach(it -> markers.add(Pair.create(it.getOffset(), "<caret>")));

    StringBuilder result = new StringBuilder(hostEditor.getDocument().getCharsSequence());
    markers.stream()
      .sorted(Comparator.<Pair<Integer, String>>comparingInt(it -> it.first).reversed()
                .thenComparingInt(it -> markerOrder(it.second)))
      .forEach(it -> result.insert(it.first, it.second));
    return result.toString();
  }

  private static int markerOrder(@NotNull String marker) {
    return switch (marker) {
      case "<brace>" -> 0;
      case "</brace>" -> 1;
      case "<caret>" -> 2;
      default -> throw new IllegalArgumentException("Unknown marker: " + marker);
    };
  }
}
