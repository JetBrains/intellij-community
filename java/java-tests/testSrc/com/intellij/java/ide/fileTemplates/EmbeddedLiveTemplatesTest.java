// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

/**
 * @author Dmitry Avdeev
 */
@TestApplication
public class EmbeddedLiveTemplatesTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testCreateFromTemplateAction() {

    doTest("Put caret here: #[[$END$]]# end of template",
           "Put caret here: <caret> end of template");
  }

  @Test
  public void testSupportVariables() {

    doTest("First: #[[$Var$]]# Second: #[[$Var$]]#",
           "First: Var Second: Var");
  }

  @Test
  public void testVariablesOrder_IDEA_325386() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      setUpTest("""
                # #[[$NAME$]]#
                # #[[$EMAIL$]]#
                # #[[$AGE$]]#
                # #[[$FAVOURITE_COLOUR$]]#
                """);

      myFixture.type("_EXTENDED");
      myFixture.type("\t");
      myFixture.type("An email");
      myFixture.type("\t");
      myFixture.type("An age");
      myFixture.type("\t");
      myFixture.type("Orange");
      myFixture.type("\t");

      myFixture.checkResult("""
                            # NAME_EXTENDED
                            # An email
                            # An age
                            # Orange
                            """);
    });
  }

  private void setUpTest(@NotNull String text) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    CustomFileTemplate template = new CustomFileTemplate("foo", "txt");
    template.setText(text);
    template.setLiveTemplateEnabled(true);
    myFixture.testAction(new TestAction(template));
    VirtualFile[] files = FileEditorManager.getInstance(myFixture.getProject()).getSelectedFiles();
    myFixture.openFileInEditor(files[0]);
  }

  private void doTest(String text, String result) {
    EdtTestUtil.runInEdtAndWait(() -> {
      setUpTest(text);
      codeInsightFixture.get().checkResult(result);
    });
  }

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(PlainTextFileType.INSTANCE, ""); // enable editor action context
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
  }

  private static class TestAction extends CreateFromTemplateActionBase {

    private final FileTemplate myTemplate;

    TestAction(FileTemplate template) {
      super("", "", null);
      myTemplate = template;
    }

    @Override
    protected FileTemplate getTemplate(Project project, PsiDirectory dir) {
      return myTemplate;
    }
  }
}
