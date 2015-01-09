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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReformatCodeActionInEditorTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/actions/reformatFileInEditor/";
  }

  public void doTest(@NotNull ReformatFilesOptions options) {
    setOptions(options);

    String before = null;
    if (options.isProcessOnlyChangedText()) {
      myFixture.configureByFile(getTestName(true) + "_revision.java");
      PsiFile file = myFixture.getFile();
      Document document = myFixture.getDocument(file);
      before = document.getText();
    }

    myFixture.configureByFile(getTestName(true) + "_before.java");

    if (before != null) {
      myFixture.getFile().putUserData(FormatChangedTextUtil.TEST_REVISION_CONTENT, before);
    }

    final String actionId = IdeActions.ACTION_EDITOR_REFORMAT;
    AnAction action = ActionManager.getInstance().getAction(actionId);

    AnActionEvent event = createEventFor(action, getProject(), myFixture.getEditor());

    action.actionPerformed(event);
    myFixture.checkResultByFile(getTestName(true) + "_after.java");
  }

  @Override
  public void tearDown() throws Exception {
    myFixture.getFile().putUserData(FormatChangedTextUtil.TEST_REVISION_CONTENT, null);
    super.tearDown();
  }

  protected AnActionEvent createEventFor(@NotNull AnAction action, @NotNull final Project project, @NotNull final Editor editor) {
    return new AnActionEvent(null, new DataContext() {
      @Nullable
      @Override
      public Object getData(@NonNls String dataId) {
        if (CommonDataKeys.PROJECT.is(dataId)) return project;
        if (CommonDataKeys.EDITOR.is(dataId)) return editor;
        return null;
      }
    }, "", action.getTemplatePresentation(), ActionManager.getInstance(), 0);
  }

  protected void setOptions(ReformatFilesOptions options) {
    ReformatCodeAction.setTestOptions(options);
  }

  public void testFormatWholeFile() {
    doTest(new MockReformatFileSettings().setProcessWholeFile(true));
  }

  public void testFormatOptimizeWholeFile() {
    doTest(new MockReformatFileSettings().setProcessWholeFile(true).setOptimizeImports(true));
  }

  public void testFormatOptimizeRearrangeWholeFile() {
    doTest(new MockReformatFileSettings().setProcessWholeFile(true).setOptimizeImports(true).setRearrange(true));
  }

  public void testFormatSelection() {
    doTest(new MockReformatFileSettings().setProcessWholeFile(false));
  }

  public void testFormatRearrangeSelection() {
    doTest(new MockReformatFileSettings().setProcessWholeFile(false).setRearrange(true));
  }

  public void testFormatVcsChanges() {
    doTest(new MockReformatFileSettings().setProcessOnlyChangedText(true));
  }

  public void testFormatOptimizeVcsChanges() {
    doTest(new MockReformatFileSettings().setProcessOnlyChangedText(true).setOptimizeImports(true));
  }

  public void testFormatOptimizeRearrangeVcsChanges() {
    doTest(new MockReformatFileSettings().setProcessOnlyChangedText(true).setOptimizeImports(true).setRearrange(true));
  }

}
