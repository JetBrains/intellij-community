// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.formatter.performance;

import com.intellij.JavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.formatting.FormatterEx;
import com.intellij.formatting.FormatterImpl;
import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.FormattingModel;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.psi.formatter.java.JavaFormatterTestCase;
import com.intellij.lang.LanguageFormatting;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.FormatterTestUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static com.intellij.psi.SyntaxTraverser.astTraverser;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Maxim.Mossienko
 */
@PerformanceUnitTest
@StressTestApplication
public class JavaFormatterPerformanceTest extends JavaFormatterTestCase {
  private static final String BASE_PATH = "psi/formatter/java";

  private PsiFile myFile;

  @Override
  protected String getBasePath() {
    return BASE_PATH;
  }

  @Test
  public void testPerformance1() throws Exception {
    File testFile = new File(JavaTestUtil.getJavaTestDataPath(), BASE_PATH + "/performance.java");
    String text = StringUtil.convertLineSeparators(FileUtil.loadFile(testFile, StandardCharsets.UTF_8));
    PsiFile file = FormatterTestUtil.createFile(getProject(), testFile.getName(), text);

    CodeStyleSettings settings = CodeStyle.createTestSettings();
    FormatterImpl formatter = (FormatterImpl)FormatterEx.getInstanceEx();
    CommonCodeStyleSettings.IndentOptions options = settings.getIndentOptions(JavaFileType.INSTANCE);

    ReadAction.run(() -> astTraverser(SourceTreeToPsiMap.psiElementToTree(file)).forEach(node -> {}));
    Benchmark.newBenchmark("Java Formatting [1]", () -> ReadAction.run(() -> {
      FormattingModel model =
        LanguageFormatting.INSTANCE.forContext(file).createModel(FormattingContext.create(file, settings));
      formatter.formatWithoutModifications(model.getDocumentModel(), model.getRootBlock(), settings, options, file.getTextRange());
    }))
      .warmupIterations(50)
      .attempts(200)
      .start();
    // attempt.min.ms varies ~3% (from experiments)
  }

  @Test
  public void testPerformance2() {
    getSettings().setDefaultRightMargin(120);
    Benchmark.newBenchmark("Java Formatting [2]", () -> doTest())
      .warmupIterations(5)
      .attempts(20)
      .start();
    // attempt.min.ms varies ~50% (from experiments)
  }

  @Test
  public void testPerformance3() {
    CommonCodeStyleSettings settings = getSettings(JavaLanguage.INSTANCE);
    settings.RIGHT_MARGIN = 80;
    settings.METHOD_PARAMETERS_WRAP = 1;
    settings.SPACE_BEFORE_METHOD_PARENTHESES = true;
    settings.SPACE_BEFORE_METHOD_CALL_PARENTHESES = true;
    settings.ALIGN_MULTILINE_PARAMETERS = false;
    CommonCodeStyleSettings.IndentOptions indentOptions = settings.getRootSettings().getIndentOptions(JavaFileType.INSTANCE);
    indentOptions.USE_TAB_CHARACTER = true;
    indentOptions.TAB_SIZE = 4;

    Benchmark.newBenchmark("Java Formatting [3]", () -> doTest())
      .warmupIterations(100)
      .attempts(300)
      .start();
    // attempt.min.ms varies ~5% (from experiments)
  }

  /**
   * Formats the test data file with an open editor, as {@code FormatterTestCase.doCheckDocumentUpdate} did.
   */
  @Override
  protected void doTextTest(String text, String textAfter) {
    PsiFile file = FormatterTestUtil.createFile(getProject(), "before." + getFileExtension(), text);
    Editor editor = EdtTestUtil.runInEdtAndGet(() -> {
      FileEditorManager fileEditorManager = FileEditorManager.getInstance(getProject());
      Editor openedEditor = fileEditorManager.openTextEditor(new OpenFileDescriptor(getProject(), file.getVirtualFile(), 0), false);
      if (myFile != null) {
        fileEditorManager.closeFile(myFile.getVirtualFile());
      }
      return openedEditor;
    });
    myFile = file;
    assertNotNull(editor);
    FormatterTestUtil.checkDocument(getProject(), file, text, textAfter);
  }

  @AfterEach
  void tearDown() {
    if (myFile != null) {
      EdtTestUtil.runInEdtAndWait(() -> {
        ((UndoManagerImpl)UndoManager.getInstance(getProject())).clearUndoRedoQueueInTests(myFile.getVirtualFile());
        FileEditorManager.getInstance(getProject()).closeFile(myFile.getVirtualFile());
      });
      myFile = null;
    }
  }
}