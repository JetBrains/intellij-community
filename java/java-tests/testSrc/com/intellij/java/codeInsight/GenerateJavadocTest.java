// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestDataPath("$CONTENT_ROOT/testData/codeInsight/generateJavadoc/")
@TestApplication
public class GenerateJavadocTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  @Test
  public void test1() { doTest(); }
  @Test
  public void test2() { doTest(); }
  @Test
  public void test3() { doTest(); }
  @Test
  public void testIdeadev2328() { doTest(); }
  @Test
  public void testIdeadev2328_2() { doTest(); }
  @Test
  public void testBeforeCommentedJavadoc() { doTest(); }
  @Test
  public void testDoubleSlashInJavadoc() { doTest(); }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String name = myFixture.getTestName(false);
    myFixture.configureByFile("/codeInsight/generateJavadoc/before" + name + ".java");
    myFixture.runInCommand(() -> {
      performAction();
      myFixture.checkResultByFile("/codeInsight/generateJavadoc/after" + name + ".java");
    });
  }

  private void performAction() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EditorActionManager actionManager = EditorActionManager.getInstance();
    EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
    DataContext context = DataManager.getInstance().getDataContext(myFixture.getEditor().getComponent());
    actionHandler.execute(myFixture.getEditor(), myFixture.getEditor().getCaretModel().getCurrentCaret(), context);
  }
}
