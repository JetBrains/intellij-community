// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.formatter.performance;

import com.intellij.application.options.CodeStyle;
import com.intellij.formatting.FormatterEx;
import com.intellij.formatting.FormatterImpl;
import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.FormattingModel;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.psi.formatter.java.JavaFormatterTestCase;
import com.intellij.lang.LanguageFormatting;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.testFramework.PlatformTestUtil;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static com.intellij.psi.SyntaxTraverser.astTraverser;

/**
 * @author Maxim.Mossienko
 */
public class JavaFormatterPerformanceTest extends JavaFormatterTestCase {
  private static final String BASE_PATH = "psi/formatter/java";

  @Override
  protected String getBasePath() {
    return BASE_PATH;
  }

  public void testPerformance1() throws Exception {
    File testFile = new File(PathManagerEx.getTestDataPath(), BASE_PATH + "/performance.java");
    String text = StringUtil.convertLineSeparators(FileUtil.loadFile(testFile, StandardCharsets.UTF_8));
    PsiFile file = createFile(testFile.getName(), text);
    astTraverser(SourceTreeToPsiMap.psiElementToTree(file)).forEach(node -> {});

    CodeStyleSettings settings = CodeStyle.createTestSettings();
    FormatterImpl formatter = (FormatterImpl)FormatterEx.getInstanceEx();
    CommonCodeStyleSettings.IndentOptions options = settings.getIndentOptions(JavaFileType.INSTANCE);

    PlatformTestUtil.startPerformanceTest("Java Formatting [1]", 5000, () -> {
      FormattingModel model =
        LanguageFormatting.INSTANCE.forContext(file).createModel(FormattingContext.create(file, settings));
      formatter.formatWithoutModifications(model.getDocumentModel(), model.getRootBlock(), settings, options, file.getTextRange());
    })
      .warmupIterations(50)
      .attempts(200)
      .assertTiming();
    // attempt.min.ms varies ~3% (from experiments)
  }

  public void testPerformance2() {
    getSettings().setDefaultRightMargin(120);
    PlatformTestUtil.startPerformanceTest("Java Formatting [2]", 8000, () -> doTest())
      .warmupIterations(5)
      .attempts(20)
      .assertTiming();
    // attempt.min.ms varies ~50% (from experiments)
  }

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

    PlatformTestUtil.startPerformanceTest("Java Formatting [3]", 3000, () -> doTest())
      .warmupIterations(100)
      .attempts(300)
      .assertTiming();
    // attempt.min.ms varies ~5% (from experiments)
  }

  @Override
  protected boolean doCheckDocumentUpdate() {
    return true;
  }
}