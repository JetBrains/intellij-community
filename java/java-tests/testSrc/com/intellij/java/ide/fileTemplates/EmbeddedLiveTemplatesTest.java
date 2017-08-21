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
package com.intellij.java.ide.fileTemplates;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.actions.CreateFromTemplateActionBase;
import com.intellij.ide.fileTemplates.impl.CustomFileTemplate;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

/**
 * @author Dmitry Avdeev
 */
public class EmbeddedLiveTemplatesTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testCreateFromTemplateAction() {

    doTest("Put caret here: #[[$END$]]# end of template",
           "Put caret here: <caret> end of template");
  }

  public void testSupportVariables() {

    doTest("First: #[[$Var$]]# Second: #[[$Var$]]#",
           "First: Var Second: Var");
  }

  protected void doTest(String text, String result) {
    CustomFileTemplate template = new CustomFileTemplate("foo", "txt");
    template.setText(text);
    template.setLiveTemplateEnabled(true);
    myFixture.testAction(new TestAction(template));
    VirtualFile[] files = FileEditorManager.getInstance(getProject()).getSelectedFiles();
    myFixture.openFileInEditor(files[0]);
    myFixture.checkResult(result);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.configureByText(PlainTextFileType.INSTANCE, ""); // enable editor action context
    TemplateManagerImpl.setTemplateTesting(getProject(), myFixture.getTestRootDisposable());
  }

  private static class TestAction extends CreateFromTemplateActionBase {

    private final FileTemplate myTemplate;

    public TestAction(FileTemplate template) {
      super("", "", null);
      myTemplate = template;
    }

    @Override
    protected FileTemplate getTemplate(Project project, PsiDirectory dir) {
      return myTemplate;
    }
  }
}
