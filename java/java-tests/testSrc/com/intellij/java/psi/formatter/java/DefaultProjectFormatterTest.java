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

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.formatter.FormatterTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class DefaultProjectFormatterTest extends JavaFormatterTestCase {
  @Override
  protected String getBasePath() {
    return "/psi/formatter/wrapping";
  }

  @Test
  public void testFormatting() {
    Project defaultProject = ProjectManager.getInstance().getDefaultProject();
    doFileTest(
      defaultProject,
      "class Foo{}",

      "class Foo {\n" +
      "}");
  }

  private void doFileTest(Project project, final String text, final String textAfter) {
    final String fileName = "before." + getFileExtension();
    final PsiFile file = FormatterTestUtil.createFile(project, fileName, text);
    FormatterTestUtil.reformat(project, file, null);
    String fileText = ReadAction.computeBlocking(() -> file.getText());
    assertEquals(textAfter, fileText);
  }
}
