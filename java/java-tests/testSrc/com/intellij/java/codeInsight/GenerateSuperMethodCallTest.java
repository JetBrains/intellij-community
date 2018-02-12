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
package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.generation.actions.GenerateSuperMethodCallAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class GenerateSuperMethodCallTest extends LightCodeInsightTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testImplement() { doTest(); }
  public void testOverride() { doTest(); }
  public void testOverrideInNestedBlock() { doTest(); }


  private void doTest() {
    String name = getTestName(false);
    configureByFile("/codeInsight/generateSuperMethodCall/before" +
                    name +
                    ".java");
    String after = "/codeInsight/generateSuperMethodCall/after" + name + ".java";
    boolean mustBeAvailable = new File(getTestDataPath() + after).exists();
    boolean isValid = new GenerateSuperMethodCallAction() {
      @Override
      protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull final PsiFile file) {
        return super.isValidForFile(project, editor, file);
      }
    }.isValidForFile(getProject(), getEditor(), getFile());
    assertEquals(mustBeAvailable, isValid);
    if (mustBeAvailable) {
      CodeInsightActionHandler handler = new GenerateSuperMethodCallAction() {
        @NotNull
        @Override
        protected CodeInsightActionHandler getHandler() {
          return super.getHandler();
        }
      }.getHandler();
      ApplicationManager.getApplication().runWriteAction(() -> handler.invoke(getProject(), getEditor(), getFile()));


      checkResultByFile(after);
    }
  }
}
