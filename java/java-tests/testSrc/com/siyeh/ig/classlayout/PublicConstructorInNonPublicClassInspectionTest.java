// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.classlayout;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class PublicConstructorInNonPublicClassInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/ig/com/siyeh/igtest/classlayout/public_constructor_in_non_public_class");
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new PublicConstructorInNonPublicClassInspection());
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testPublicConstructorInNonPublicClass() {
    doTest();
  }
  @Test
  public void testRecordsJava15() {
    doTest();
  }

  @Test
  public void testQuickfix() {
    checkQuickFix();
  }

  @Test
  public void testQuickfixWithParameters() {
    checkQuickFix();
  }

  private void checkQuickFix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    doTest();
    final IntentionAction intention = myFixture.getAvailableIntention("Remove 'public' modifier");
    assertNotNull(intention);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction(intention);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      myFixture.checkResultByFile(myFixture.getTestName(false) + ".after.java");
    });
  }
}