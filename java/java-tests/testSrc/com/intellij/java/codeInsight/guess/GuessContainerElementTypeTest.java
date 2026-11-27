// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.guess;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class GuessContainerElementTypeTest {

  private static final String BASE_PATH = "/codeInsight/guess/containerElement";

  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  @Test
  public void test1() {
    daemonFixture.get().configureByFile(BASE_PATH + "/Test1.java");
    ReadAction.runBlocking(() -> {
      PsiType[] result = guessContainerElementTypes();
      assertNotNull(result);
      assertEquals(1, result.length);
      assertTrue(result[0].equalsToText("java.lang.String"));
    });
  }

  @Test
  public void test2() {
    daemonFixture.get().configureByFile(BASE_PATH + "/Test2.java");
    ReadAction.runBlocking(() -> {
      PsiType[] result = guessContainerElementTypes();
      assertNotNull(result);
      assertEquals(1, result.length);
      assertTrue(result[0].equalsToText("java.lang.String"));
    });
  }

  private PsiType[] guessContainerElementTypes() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    int offset = myFixture.getEditor().getCaretModel().getOffset();
    PsiElement element = myFixture.getFile().findElementAt(offset);
    assertNotNull(element);
    PsiElement parent = element.getParent();
    assertTrue(parent instanceof PsiReferenceExpression);
    PsiExpression expr = (PsiExpression)parent;
    return GuessManager.getInstance(myFixture.getProject()).guessContainerElementType(expr, null);
  }
}
