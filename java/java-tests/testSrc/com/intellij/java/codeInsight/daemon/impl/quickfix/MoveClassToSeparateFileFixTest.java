// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class MoveClassToSeparateFileFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testSimple() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Test.java", """
      public class Test {}
      <error descr="Class 'Another' is public, should be declared in a file named 'Another.java'">public class <caret>Another</error> {}""");
    myFixture.checkHighlighting();
    IntentionAction action = myFixture.findSingleIntention("Move class 'Another' to 'Another.java'");
    myFixture.checkIntentionPreviewHtml(action, """
      <p><icon src="source_Another"/>&nbsp;Another &rarr; <icon src="target_src"/>&nbsp;src</p>""");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction(action);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      myFixture.checkResult("public class Test {}\n");
      PsiClass another = myFixture.findClass("Another");
      assertNotNull(another);
      PsiFile psiFile = another.getContainingFile();
      assertEquals("Another.java", psiFile.getName());
      assertEquals("public class Another {}\n", psiFile.getText());
    });
  }
}
