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
package com.intellij.java.codeInsight.actions;

import com.intellij.JavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.application.options.codeStyle.excludedFiles.NamedScopeDescriptor;
import com.intellij.codeInsight.actions.FileInEditorProcessor;
import com.intellij.codeInsight.actions.LayoutCodeOptions;
import com.intellij.codeInsight.actions.ReformatCodeRunOptions;
import com.intellij.codeInsight.actions.VcsFacade;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.actions.TextRangeType.SELECTED_TEXT;
import static com.intellij.codeInsight.actions.TextRangeType.VCS_CHANGED_TEXT;
import static com.intellij.codeInsight.actions.TextRangeType.WHOLE_FILE;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class ReformatCodeActionInEditorTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/actions/reformatFileInEditor/");
  }

  @AfterEach
  void tearDown() {
    PsiFile file = codeInsightFixture.get().getFile();
    if (file != null) {
      file.putUserData(VcsFacade.TEST_REVISION_CONTENT, null);
    }
  }

  public void doTest(LayoutCodeOptions options) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    CharSequence revisionContent = null;
    if (options.getTextRangeType() == VCS_CHANGED_TEXT) {
      myFixture.configureByFile(myFixture.getTestName(true) + "_revision.java");
      PsiFile file = myFixture.getFile();
      Document document = myFixture.getDocument(file);
      revisionContent = document.getCharsSequence();
    }

    myFixture.configureByFile(myFixture.getTestName(true) + "_before.java");
    if (revisionContent != null) {
      myFixture.getFile().putUserData(VcsFacade.TEST_REVISION_CONTENT, revisionContent);
    }

    FileInEditorProcessor processor = new FileInEditorProcessor(myFixture.getFile(), myFixture.getEditor(), options);

    EdtTestUtil.runInEdtAndWait(() -> processor.processCode());
    myFixture.checkResultByFile(myFixture.getTestName(true) + "_after.java");
  }

  @Test
  public void testSelectionReformat() {
    doTest(new ReformatCodeRunOptions(SELECTED_TEXT));
  }

  @Test
  public void testWholeFileReformat() {
    doTest(new ReformatCodeRunOptions(WHOLE_FILE));
  }

  @Test
  public void testWholeFileReformatAndOptimize() {
    doTest(new ReformatCodeRunOptions(WHOLE_FILE).setOptimizeImports(true));
  }

  @Test
  public void testSelectedTextAndOptimizeImports() {
    doTest(new ReformatCodeRunOptions(SELECTED_TEXT).setOptimizeImports(true));
  }

  @Test
  public void testFormatWholeFile() {
    doTest(new ReformatCodeRunOptions(WHOLE_FILE));
  }

  @Test
  public void testFormatOptimizeWholeFile() {
    doTest(new ReformatCodeRunOptions(WHOLE_FILE).setOptimizeImports(true));
  }

  @Test
  public void testFormatOptimizeRearrangeWholeFile() {
    doTest(new ReformatCodeRunOptions(WHOLE_FILE).setOptimizeImports(true).setRearrangeCode(true));
  }

  @Test
  public void testFormatSelection() {
    doTest(new ReformatCodeRunOptions(SELECTED_TEXT));
  }

  @Test
  public void testFormatRearrangeSelection() {
    doTest(new ReformatCodeRunOptions(SELECTED_TEXT).setRearrangeCode(true));
  }

  @Test
  public void testFormatVcsChanges() {
    doTest(new ReformatCodeRunOptions(VCS_CHANGED_TEXT));
  }

  @Test
  public void testFormatOptimizeVcsChanges() {
    doTest(new ReformatCodeRunOptions(VCS_CHANGED_TEXT).setOptimizeImports(true));
  }

  // Excluded because of IDEA-229587
  public void _testFormatOptimizeRearrangeVcsChanges() {
    doTest(new ReformatCodeRunOptions(VCS_CHANGED_TEXT).setOptimizeImports(true).setRearrangeCode(true));
  }
  
  @Test
  public void testReformatRearrange_NotBreaksCode_WhenCaretOnEmptyLine() {
    doTest(new ReformatCodeRunOptions(WHOLE_FILE).setRearrangeCode(true));
  }

  @Test
  public void testFormatSelection_DoNotTouchTrailingWhiteSpaces() {
    //todo actually test is not working, and working test is not working
    doTest(new ReformatCodeRunOptions(SELECTED_TEXT));
  }

  // Excluded because of IDEA-229587
  public void _testWrapParamList() {
    CodeStyleSettings temp = CodeStyle.createTestSettings();
    CommonCodeStyleSettings javaSettings = temp.getCommonSettings(JavaLanguage.INSTANCE);
    javaSettings.KEEP_LINE_BREAKS = false;
    javaSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    javaSettings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED | CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    CodeStyle.doWithTemporarySettings(codeInsightFixture.get().getProject(), temp, () -> doTest(new ReformatCodeRunOptions(VCS_CHANGED_TEXT)));
  }

  @Test
  public void testDisabledFormatting() {
    CodeStyleSettings temp = CodeStyle.createTestSettings();
    NamedScopeDescriptor descriptor = new NamedScopeDescriptor("Test");
    descriptor.setPattern("file:*.java");
    temp.getExcludedFiles().addDescriptor(descriptor);
    CodeStyle.doWithTemporarySettings(codeInsightFixture.get().getProject(), temp, () -> doTest(new ReformatCodeRunOptions(WHOLE_FILE).setOptimizeImports(true)));
  }

  @Test
  public void testReformatWithOptimizeImportMustNotBeCanceledUnexpectedly() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    CodeStyleSettings temp = CodeStyle.createTestSettings();
    CodeStyle.doWithTemporarySettings(codeInsightFixture.get().getProject(), temp, () -> {
      String text = """
        class X {
            void f(Runnable dddd) {
                f(()->{});vvdds<caret>
            }
        }""";
      myFixture.configureByText("x.java", text);
      myFixture.type("abc");

      LayoutCodeOptions options = new ReformatCodeRunOptions(WHOLE_FILE).setOptimizeImports(true);
      FileInEditorProcessor processor = new FileInEditorProcessor(myFixture.getFile(), myFixture.getEditor(), options);
      EdtTestUtil.runInEdtAndWait(() -> processor.processCode());
      myFixture.checkResult("""
                              class X {
                                  void f(Runnable dddd) {
                                      f(() -> {
                                      });
                                      vvddsabc
                                  }
                              }""");
    });
  }
}
