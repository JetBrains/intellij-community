/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.fileTemplates;

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

  public void testCreateFromTemplateAction() throws Exception {

    myFixture.configureByText(PlainTextFileType.INSTANCE, "");
    CustomFileTemplate template = new CustomFileTemplate("foo", "txt");
    template.setText("Put caret here: #[[$END$]]# end of template");
    template.setHasEmbeddedLiveTemplate(true);
    myFixture.testAction(new TestAction(template));
    VirtualFile[] files = FileEditorManager.getInstance(getProject()).getSelectedFiles();
    myFixture.openFileInEditor(files[0]);
    myFixture.checkResult("Put caret here: <caret> end of template");
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
