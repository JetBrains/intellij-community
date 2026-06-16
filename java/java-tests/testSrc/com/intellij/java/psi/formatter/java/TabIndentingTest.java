// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.util.IncorrectOperationException;

import java.io.File;

public class TabIndentingTest extends LightIdeaTestCase {
  private static final String BASE_PATH = PathManagerEx.getTestDataPath() + "/psi/formatter/tabIndenting";

  public void testSpace() throws Exception {
    doTest("Test.java", "Space_after.java");
  }

  public void testSmartTab4() throws Exception {
    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    settings.getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = true;
    settings.getIndentOptions(JavaFileType.INSTANCE).SMART_TABS = true;

    doTest("Test.java", "SmartTab4_after.java");
  }

  public void testSmartTab2() throws Exception {
    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    settings.getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = true;
    settings.getIndentOptions(JavaFileType.INSTANCE).SMART_TABS = true;
    settings.getIndentOptions(JavaFileType.INSTANCE).TAB_SIZE = 2;

    doTest("Test.java", "SmartTab2_after.java");
  }

  public void testSmartTab8() throws Exception {
    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    settings.getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = true;
    settings.getIndentOptions(JavaFileType.INSTANCE).SMART_TABS = true;
    settings.getIndentOptions(JavaFileType.INSTANCE).TAB_SIZE = 8;

    doTest("Test.java", "SmartTab8_after.java");
  }

  public void testTab2() throws Exception {
    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    settings.getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = true;
    settings.getIndentOptions(JavaFileType.INSTANCE).TAB_SIZE = 2;

    doTest("Test.java", "Tab2_after.java");
  }

  public void testTab4() throws Exception {
    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    settings.getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = true;

    doTest("Test.java", "Tab4_after.java");
  }

  public void testTab8() throws Exception {
    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    settings.getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = true;
    settings.getIndentOptions(JavaFileType.INSTANCE).TAB_SIZE = 8;

    doTest("Test.java", "Tab8_after.java");
  }

  public void testSCR6197() throws Exception {
    doTest("SCR6197.java", "SCR6197_after.java");
  }

  public void testMoreTabsInComments() throws Exception {
    doTest("moreTabsInComments.java", "moreTabsInComments_after.java");
  }

  public void testAlignmentWithTabs() throws Exception {
    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    settings.getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = true;
    settings.getIndentOptions(JavaFileType.INSTANCE).TAB_SIZE = 4;
    settings.getCommonSettings(JavaLanguage.INSTANCE).METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    settings.getCommonSettings(JavaLanguage.INSTANCE).ALIGN_MULTILINE_PARAMETERS = true;
    doTest("methodAlignment.java", "methodAlignment_after.java");
  }

  private void doTest(String fileNameBefore, String fileNameAfter) throws Exception {
    String text = loadFile(fileNameBefore);
    final PsiFile file = createFile(fileNameBefore, text);
    CommandProcessor.getInstance().executeCommand(getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        CodeStyleManager.getInstance(getProject()).reformat(file);
      }
      catch (IncorrectOperationException e) {
        throw new AssertionError(e);
      }
    }), null, null);


    String textAfter = loadFile(fileNameAfter);
    String fileText = file.getText();

    textAfter = StringUtil.trimStart(textAfter, "\n");
    fileText = StringUtil.trimStart(fileText, "\n");

    if (!textAfter.equals(fileText)) {
      System.err.println("Expected:");
      System.err.println(textAfter);
      System.err.println();
      System.err.println("Was:");
      System.err.println(fileText);

      assertEquals(textAfter, fileText);
    }
  }

  private static String loadFile(String name) throws Exception {
    String fullName = BASE_PATH + File.separatorChar + name;
    String text = FileUtil.loadFile(new File(fullName));
    text = StringUtil.convertLineSeparators(text);
    return text;
  }
}
