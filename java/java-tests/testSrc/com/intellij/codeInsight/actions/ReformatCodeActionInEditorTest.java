/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.actions;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

import static com.intellij.codeInsight.actions.TextRangeType.*;

public class ReformatCodeActionInEditorTest extends LightPlatformCodeInsightFixtureTestCase {

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/actions/reformatFileInEditor/";
  }

  @Override
  public void tearDown() throws Exception {
    myFixture.getFile().putUserData(FormatChangedTextUtil.TEST_REVISION_CONTENT, null);
    super.tearDown();
  }

  public void doTest(LayoutCodeOptions options) {
    CharSequence revisionContent = null;
    if (options.getTextRangeType() == VCS_CHANGED_TEXT) {
      myFixture.configureByFile(getTestName(true) + "_revision.java");
      PsiFile file = myFixture.getFile();
      Document document = myFixture.getDocument(file);
      revisionContent = document.getCharsSequence();
    }

    myFixture.configureByFile(getTestName(true) + "_before.java");
    if (revisionContent != null) {
      myFixture.getFile().putUserData(FormatChangedTextUtil.TEST_REVISION_CONTENT, revisionContent);
    }

    FileInEditorProcessor processor = new FileInEditorProcessor(myFixture.getFile(), myFixture.getEditor(), options);

    processor.processCode();
    myFixture.checkResultByFile(getTestName(true) + "_after.java");
  }

  public void testSelectionReformat() {
    doTest(new ReformatCodeRunOptions(SELECTED_TEXT));
  }

  public void testWholeFileReformat() {
    doTest(new ReformatCodeRunOptions(WHOLE_FILE));
  }

  public void testWholeFileReformatAndOptimize() {
    doTest(new ReformatCodeRunOptions(WHOLE_FILE).setOptimizeImports(true));
  }

  public void testSelectedTextAndOptimizeImports() {
    doTest(new ReformatCodeRunOptions(SELECTED_TEXT).setOptimizeImports(true));
  }

  public void testFormatWholeFile() {
    doTest(new ReformatCodeRunOptions(WHOLE_FILE));
  }

  public void testFormatOptimizeWholeFile() {
    doTest(new ReformatCodeRunOptions(WHOLE_FILE).setOptimizeImports(true));
  }

  public void testFormatOptimizeRearrangeWholeFile() {
    doTest(new ReformatCodeRunOptions(WHOLE_FILE).setOptimizeImports(true).setRearrangeCode(true));
  }

  public void testFormatSelection() {
    doTest(new ReformatCodeRunOptions(SELECTED_TEXT));
  }

  public void testFormatRearrangeSelection() {
    doTest(new ReformatCodeRunOptions(SELECTED_TEXT).setRearrangeCode(true));
  }

  public void testFormatVcsChanges() {
    doTest(new ReformatCodeRunOptions(VCS_CHANGED_TEXT));
  }

  public void testFormatOptimizeVcsChanges() {
    doTest(new ReformatCodeRunOptions(VCS_CHANGED_TEXT).setOptimizeImports(true));
  }

  public void testFormatOptimizeRearrangeVcsChanges() {
    doTest(new ReformatCodeRunOptions(VCS_CHANGED_TEXT).setOptimizeImports(true).setRearrangeCode(true));
  }
  
  public void testReformatRearrange_NotBreaksCode_WhenCaretOnEmptyLine() {
    doTest(new ReformatCodeRunOptions(WHOLE_FILE).setRearrangeCode(true));
  }

  public void testFormatSelection_DoNotTouchTrailingWhiteSpaces() {
    //todo actually test is not working, and working test is not working
    doTest(new ReformatCodeRunOptions(SELECTED_TEXT));
  }
}
