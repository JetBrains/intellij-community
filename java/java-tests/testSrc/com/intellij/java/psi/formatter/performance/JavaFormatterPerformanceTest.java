/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.psi.formatter.performance;

import com.intellij.formatting.FormatterEx;
import com.intellij.formatting.FormatterImpl;
import com.intellij.formatting.FormattingModel;
import com.intellij.java.psi.formatter.java.JavaFormatterTestCase;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageFormatting;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.FormatterTestCase;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;

import java.io.File;

/**
 * @author Maxim.Mossienko
 * @since Jan 26, 2007
 */
public class JavaFormatterPerformanceTest extends JavaFormatterTestCase {
  private static final String BASE_PATH= "psi/formatter/java";

  @Override
  protected String getBasePath() {
    return BASE_PATH;
  }

  public void testPaymentManager() throws Exception {
    final FileEditorManager editorManager = FileEditorManager.getInstance(LightPlatformTestCase.getProject());
    try {
      getSettings().getCommonSettings(JavaLanguage.INSTANCE).KEEP_LINE_BREAKS = false;
      doTest();
    } finally {
      getSettings().getCommonSettings(JavaLanguage.INSTANCE).KEEP_LINE_BREAKS = true;
      editorManager.closeFile(editorManager.getSelectedFiles()[0]);
    }
  }

  public void testPerformance() throws Exception {
    final String name = getTestName(true) + ".java";
    final String text = loadFile(name);
    final PsiFile file = LightPlatformTestCase.createFile(name, text);

    transformAllChildren(SourceTreeToPsiMap.psiElementToTree(file));
    final CodeStyleSettings settings = new CodeStyleSettings();
    PlatformTestUtil.startPerformanceTest("java formatting",1000, () -> {
      final FormattingModel model = LanguageFormatting.INSTANCE.forContext(file).createModel(file, settings);
      ((FormatterImpl)FormatterEx.getInstanceEx()).formatWithoutModifications(model.getDocumentModel(), model.getRootBlock(), settings,
                                                                              settings.getIndentOptions(StdFileTypes.JAVA),
                                                                              file.getTextRange());
    }).useLegacyScaling().assertTiming();
  }

  public void testPerformance2() {
    final CodeStyleSettings settings = getSettings();
    settings.setDefaultRightMargin(120);
    PlatformTestUtil.startPerformanceTest(getTestName(false), 4000, () -> {
      try {
        doTest();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }).useLegacyScaling().assertTiming();
  }

  public void testPerformance3() {
    PlatformTestUtil.startPerformanceTest(getTestName(false), 3900, () -> {
      final CommonCodeStyleSettings settings = FormatterTestCase.getSettings(JavaLanguage.INSTANCE);
      settings.RIGHT_MARGIN = 80;
      settings.METHOD_PARAMETERS_WRAP = 1;
      final CommonCodeStyleSettings.IndentOptions indentOptions = settings.getRootSettings().getIndentOptions(StdFileTypes.JAVA);
      indentOptions.USE_TAB_CHARACTER = true;
      indentOptions.TAB_SIZE = 4;
      settings.SPACE_BEFORE_METHOD_PARENTHESES = true;
      settings.SPACE_BEFORE_METHOD_CALL_PARENTHESES = true;
      settings.ALIGN_MULTILINE_PARAMETERS = false;
      try {
        doTest();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }).useLegacyScaling().assertTiming();
  }

  private static void transformAllChildren(final ASTNode file) {
    for (ASTNode child = file.getFirstChildNode(); child != null; child = child.getTreeNext()) {
      transformAllChildren(child);
    }
  }

  private static String loadFile(String name) throws Exception {
    String fullName = PathManagerEx.getTestDataPath() + "/" + BASE_PATH + File.separatorChar + name;
    String text = FileUtil.loadFile(new File(fullName));
    text = StringUtil.convertLineSeparators(text);
    return text;
  }

  @Override
  protected boolean doCheckDocumentUpdate() {
    return true;
  }
}
