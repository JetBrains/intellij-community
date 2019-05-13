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
package com.intellij.java.psi.formatter.java;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Rustam Vishnyakov
 */
public class DefaultProjectFormatterTest extends JavaFormatterTestCase {
  @Override
  protected String getBasePath() {
    return "/psi/formatter/wrapping";
  }

  public void testFormatting() {
    Project currProject = ourProject;
    ourProject = ProjectManager.getInstance().getDefaultProject();
    try {
      doFileTest(
        "class Foo{}",

        "class Foo {\n" +
        "}");
    }
    finally {
      ourProject = currProject;
    }
  }

  protected void doFileTest(final String text, final String textAfter)
    throws IncorrectOperationException {
    final String fileName = "before." + getFileExtension();
    final PsiFile file = createFileFromText(text, fileName, PsiFileFactory.getInstance(getProject()));
    CommandProcessor.getInstance().executeCommand(getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> performFormatting(file)), "", "");
    String fileText = file.getText();
    assertEquals(textAfter, fileText);
  }
}
