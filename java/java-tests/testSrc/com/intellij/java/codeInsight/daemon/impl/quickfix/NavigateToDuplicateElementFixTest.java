// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
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
public class NavigateToDuplicateElementFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testNavigateToSameLevelClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("A.java", "class Foo {} class Foo<caret> {}");
    doTest("Navigate to duplicate class", 6);
  }

  @Test
  public void testNavigateToSameLevelInterface() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("A.java", "interface Foo {} interface Foo<caret> {}");
    doTest("Navigate to duplicate interface", 10);
  }

  @Test
  public void testNavigateFromInterfaceToClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("A.java", "class Foo { interface Foo<caret> {} }");
    doTest("Navigate to duplicate class", 6);
  }

  @Test
  public void testNavigateFromClassToInterface() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("A.java", "interface Foo {} class Foo<caret> {}");
    doTest("Navigate to duplicate interface", 10);
  }

  private void doTest(String intentionName, int expectedOffset) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IntentionAction intention = myFixture.findSingleIntention(intentionName);
    assertNotNull(intention);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction(intention);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      assertEquals(expectedOffset, myFixture.getCaretOffset());
    });
  }
}
