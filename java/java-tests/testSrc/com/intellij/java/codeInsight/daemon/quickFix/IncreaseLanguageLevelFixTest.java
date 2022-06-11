// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.java.JavaBundle;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

public class IncreaseLanguageLevelFixTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/increaseLanguageLevel/";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.setLanguageLevel(LanguageLevel.JDK_1_6);
    moduleBuilder.addJdk(IdeaTestUtil.getMockJdk18Path().getPath());
  }

  public void testLambda() {
    myFixture.configureByFile("Lambda.java");
    assertEquals(LanguageLevel.JDK_1_6, PsiUtil.getLanguageLevel(myFixture.getFile()));
    IntentionAction fix = myFixture.findSingleIntention("Set language level");
    myFixture.launchAction(fix);
    assertEquals(LanguageLevel.JDK_1_8, PsiUtil.getLanguageLevel(myFixture.getFile()));
  }

  public void testUpgradeJdk() {
    myFixture.configureByFile("UpgradeJdk.java");
    assertEquals(LanguageLevel.JDK_1_6, PsiUtil.getLanguageLevel(myFixture.getFile()));
    assertNotNull(myFixture.findSingleIntention(JavaBundle.message("intention.name.upgrade.jdk.to", "10")));
  }
}
