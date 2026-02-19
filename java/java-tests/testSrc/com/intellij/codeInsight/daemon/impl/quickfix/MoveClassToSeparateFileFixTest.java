// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class MoveClassToSeparateFileFixTest extends LightJavaCodeInsightFixtureTestCase {
  public void testSimple() {
    myFixture.configureByText("Test.java", """
      public class Test {}
      <error descr="Class 'Another' is public, should be declared in a file named 'Another.java'">public class <caret>Another</error> {}""");
    myFixture.checkHighlighting();
    IntentionAction action = myFixture.findSingleIntention("Move class 'Another' to 'Another.java'");
    myFixture.checkIntentionPreviewHtml(action, """
      <p><icon src="source_Another"/>&nbsp;Another &rarr; <icon src="target_src"/>&nbsp;src</p>""");
    myFixture.launchAction(action);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    myFixture.checkResult("public class Test {}\n");
    PsiClass another = myFixture.findClass("Another");
    assertNotNull(another);
    PsiFile psiFile = another.getContainingFile();
    assertEquals("Another.java", psiFile.getName());
    assertEquals("public class Another {}\n", psiFile.getText());
  }
}
