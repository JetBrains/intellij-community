// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.java.JavaBundle;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class IncreaseLanguageLevelFixTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture =
    javaModuleCodeInsightFixture(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/increaseLanguageLevel/", true, moduleBuilder -> {
      moduleBuilder.setLanguageLevel(LanguageLevel.JDK_1_6);
      moduleBuilder.addJdkVersion(LanguageLevel.JDK_1_8);
    });

  @Test
  public void testLambda() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("Lambda.java");
    assertEquals(LanguageLevel.JDK_1_6, PsiUtil.getLanguageLevel(myFixture.getFile()));
    IntentionAction fix = myFixture.findSingleIntention("Set language level");
    myFixture.launchAction(fix);
    // new language level will be pushed during scanning
    IndexingTestUtil.waitUntilIndexesAreReady(myFixture.getProject());
    assertEquals(LanguageLevel.JDK_1_8, PsiUtil.getLanguageLevel(myFixture.getFile()));
  }

  @Test
  public void testUpgradeJdk() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("UpgradeJdk.java");
    assertEquals(LanguageLevel.JDK_1_6, PsiUtil.getLanguageLevel(myFixture.getFile()));
    assertNotNull(myFixture.findSingleIntention(JavaBundle.message("intention.name.upgrade.jdk.to", "10")));
  }
}
