package com.intellij.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.generation.actions.GenerateSuperMethodCallAction;
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

  public void testImplement() throws Exception { doTest(); }
  public void testOverride() throws Exception { doTest(); }


  private void doTest() throws Exception {
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
      handler.invoke(getProject(), getEditor(), getFile());

      checkResultByFile(after);
    }
  }
}
