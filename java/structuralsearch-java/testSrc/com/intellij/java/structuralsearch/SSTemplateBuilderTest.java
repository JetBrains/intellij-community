// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.structuralsearch;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.structuralsearch.plugin.ui.StructuralSearchTemplateBuilder;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class SSTemplateBuilderTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testClassTemplate() {
    doTest("class foo extends bar, next implements xxx",
           "class foo extends $Class2$, $Class3$ implements $Class4$");
  }

  @Test
  public void testStatement() {
    doTest("i=1", "$Var1$=1");
  }

  @Test
  public void testMethodCall() {
    Template template = doTest("foo()", "foo()");
    assertEquals(1, template.getSegmentsCount());
  }

  @Test
  public void testStartingWhitespace() {
    doTest("    List<Integer> list = null;", "    $Class1$<$Class2$> list = null;");
  }

  @Test
  public void testInnerWhiteSpace() {
    doTest("try {\n" +
           "    List<Integer> list = null;} finally {}",
           "try {\n" +
           "    $Class1$<$Class2$> list = null;} finally {}");
  }

  @Test
  public void testEmpty() {
    doTest("", "");
  }

  private Template doTest(String text, String expected) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile psiFile = myFixture.configureByText(JavaFileType.INSTANCE, text);
    TemplateBuilderImpl builder = ReadAction.computeBlocking(() -> (TemplateBuilderImpl)StructuralSearchTemplateBuilder.getInstance().buildTemplate(myFixture.getFile()));
    Template template = WriteCommandAction.writeCommandAction(myFixture.getProject()).compute(() -> {
      Template inlineTemplate = builder.buildInlineTemplate();
      TemplateManager.getInstance(myFixture.getProject()).startTemplate(myFixture.getEditor(), inlineTemplate);
      return inlineTemplate;
    });
    assertEquals(expected, psiFile.getText());
    return template;
  }
}
