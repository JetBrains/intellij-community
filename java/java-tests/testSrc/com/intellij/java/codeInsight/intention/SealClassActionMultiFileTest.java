// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.SealClassAction;
import com.intellij.openapi.command.CommandProcessor;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class SealClassActionMultiFileTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testMultiFile() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile main = myFixture.configureByText("Main.java", "class Mai<caret>n {}");
    PsiClass direct1 = myFixture.addClass("class Direct1 extends Main {}");
    PsiClass direct2 = myFixture.addClass("class Direct2 extends Main {}");
    PsiClass indirect = myFixture.addClass("class Indirect extends Direct1 {}");

    IntentionAction action = new SealClassAction().asIntention();
    EdtTestUtil.runInEdtAndWait(() -> {
      assertTrue(action.isAvailable(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));
      CommandProcessor.getInstance().executeCommand(myFixture.getProject(), () -> {
        action.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      }, null, null);
      assertEquals("sealed class Main permits Direct1, Direct2 {}", main.getText());
      assertEquals("non-sealed class Direct1 extends Main {}", direct1.getText());
      assertEquals("non-sealed class Direct2 extends Main {}", direct2.getText());
      assertEquals("class Indirect extends Direct1 {}", indirect.getText());
    });
  }
}
