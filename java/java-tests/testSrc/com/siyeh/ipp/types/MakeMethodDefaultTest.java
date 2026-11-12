// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.types;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ipp.IPPFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;

@TestApplication
public class MakeMethodDefaultTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp = ippFixture(projectFixture, "types/makeDefault", "Make 'foo()' default");

  @Test
  public void testInInterface() {
    ipp.get().doTest();
  }

  @Test
  public void testAlreadyDefault() {
    ipp.get().assertIntentionNotAvailable();
  }

  @Test
  public void testAlreadyHasBody() {
    LightJavaCodeInsightTestFixture myFixture = ipp.get().getFixture();
    @NotNull final String intentionName = ipp.get().getIntentionName();
    final String testName = myFixture.getTestName(false);
    myFixture.configureByFile(testName + ".java");
    final List<IntentionAction> actions = myFixture.filterAvailableIntentions(intentionName);
    for (IntentionAction action : actions) {
      // a quick fix with the same name is available if the method already has a body
      // so we can't check on the name
      assertFalse(action instanceof MakeMethodDefaultIntention);
    }
  }
  
  @Test
  public void testAnnotationType() {
    ipp.get().assertIntentionNotAvailable();
  }
}
