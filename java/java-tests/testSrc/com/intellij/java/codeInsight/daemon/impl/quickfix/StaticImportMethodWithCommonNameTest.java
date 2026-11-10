// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
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
public class StaticImportMethodWithCommonNameTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    int idx = 0;
    while (idx++ < 450) {
      myFixture.addClass("package a; class ATest" + idx + "{ public void format(String s, String s1) {}}");
    }
  }

  @Test
  public void testFindStaticMember() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", "class A { {String s = for<caret>mat(\"\",\"\");}}");
    final IntentionAction intention = myFixture.findSingleIntention(QuickFixBundle.message("static.import.method.text"));
    assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResult("""
                            import static java.lang.String.format;

                            class A { {String s = format("","");}}""", false);
  }
}
