// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java;

import com.intellij.JavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import org.junit.jupiter.api.Test;

import java.io.File;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class TabIndentingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(new DefaultLightProjectDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private static final String BASE_PATH = JavaTestUtil.getJavaTestDataPath() + "/psi/formatter/tabIndenting";

  @Test
  public void testSpace() throws Exception {
    doTest("Test.java", "Space_after.java");
  }

  @Test
  public void testSmartTab4() throws Exception {
    CodeStyleSettings settings = CodeStyle.getSettings(codeInsightFixture.get().getProject());
    settings.getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = true;
    settings.getIndentOptions(JavaFileType.INSTANCE).SMART_TABS = true;

    doTest("Test.java", "SmartTab4_after.java");
  }

  @Test
  public void testSmartTab2() throws Exception {
    CodeStyleSettings settings = CodeStyle.getSettings(codeInsightFixture.get().getProject());
    settings.getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = true;
    settings.getIndentOptions(JavaFileType.INSTANCE).SMART_TABS = true;
    settings.getIndentOptions(JavaFileType.INSTANCE).TAB_SIZE = 2;

    doTest("Test.java", "SmartTab2_after.java");
  }

  @Test
  public void testSmartTab8() throws Exception {
    CodeStyleSettings settings = CodeStyle.getSettings(codeInsightFixture.get().getProject());
    settings.getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = true;
    settings.getIndentOptions(JavaFileType.INSTANCE).SMART_TABS = true;
    settings.getIndentOptions(JavaFileType.INSTANCE).TAB_SIZE = 8;

    doTest("Test.java", "SmartTab8_after.java");
  }

  @Test
  public void testTab2() throws Exception {
    CodeStyleSettings settings = CodeStyle.getSettings(codeInsightFixture.get().getProject());
    settings.getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = true;
    settings.getIndentOptions(JavaFileType.INSTANCE).TAB_SIZE = 2;

    doTest("Test.java", "Tab2_after.java");
  }

  @Test
  public void testTab4() throws Exception {
    CodeStyleSettings settings = CodeStyle.getSettings(codeInsightFixture.get().getProject());
    settings.getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = true;

    doTest("Test.java", "Tab4_after.java");
  }

  @Test
  public void testTab8() throws Exception {
    CodeStyleSettings settings = CodeStyle.getSettings(codeInsightFixture.get().getProject());
    settings.getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = true;
    settings.getIndentOptions(JavaFileType.INSTANCE).TAB_SIZE = 8;

    doTest("Test.java", "Tab8_after.java");
  }

  @Test
  public void testSCR6197() throws Exception {
    doTest("SCR6197.java", "SCR6197_after.java");
  }

  @Test
  public void testMoreTabsInComments() throws Exception {
    doTest("moreTabsInComments.java", "moreTabsInComments_after.java");
  }

  @Test
  public void testAlignmentWithTabs() throws Exception {
    CodeStyleSettings settings = CodeStyle.getSettings(codeInsightFixture.get().getProject());
    settings.getIndentOptions(JavaFileType.INSTANCE).USE_TAB_CHARACTER = true;
    settings.getIndentOptions(JavaFileType.INSTANCE).TAB_SIZE = 4;
    settings.getCommonSettings(JavaLanguage.INSTANCE).METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    settings.getCommonSettings(JavaLanguage.INSTANCE).ALIGN_MULTILINE_PARAMETERS = true;
    doTest("methodAlignment.java", "methodAlignment_after.java");
  }

  private void doTest(String fileNameBefore, String fileNameAfter) throws Exception {
    String text = loadFile(fileNameBefore);
    Project project = codeInsightFixture.get().getProject();
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileNameBefore);
    final PsiFile file = ReadAction.computeBlocking(
      () -> PsiFileFactory.getInstance(project).createFileFromText(fileNameBefore, fileType, text, LocalTimeCounter.currentTime(), true, false));
    EdtTestUtil.runInEdtAndWait(() -> CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        CodeStyleManager.getInstance(project).reformat(file);
      }
      catch (IncorrectOperationException e) {
        throw new AssertionError(e);
      }
    }), null, null));


    String textAfter = loadFile(fileNameAfter);
    String fileText = ReadAction.computeBlocking(() -> file.getText());

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
