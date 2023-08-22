// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.SealClassAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class SealClassActionMultiFileTest extends LightJavaCodeInsightFixtureTestCase {
  public void testMultiFile() {
    PsiFile main = myFixture.configureByText("Main.java", "class Mai<caret>n {}");
    PsiClass direct1 = myFixture.addClass("class Direct1 extends Main {}");
    PsiClass direct2 = myFixture.addClass("class Direct2 extends Main {}");
    PsiClass indirect = myFixture.addClass("class Indirect extends Direct1 {}");

    IntentionAction action = new SealClassAction().asIntention();
    assertTrue(action.isAvailable(getProject(), getEditor(), getFile()));
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      action.invoke(getProject(), getEditor(), getFile());
    });
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    assertEquals("sealed class Main permits Direct1, Direct2 {}", main.getText());
    assertEquals("non-sealed class Direct1 extends Main {}", direct1.getText());
    assertEquals("non-sealed class Direct2 extends Main {}", direct2.getText());
    assertEquals("class Indirect extends Direct1 {}", indirect.getText());
  }
}
