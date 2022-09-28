// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class StaticImportMethodWithCommonNameTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    int idx = 0;
    while (idx++ < 450) {
      myFixture.addClass("package a; class ATest" + idx + "{ public void format(String s, String s1) {}}");
    }
  }

  public void testFindStaticMember() {
    myFixture.configureByText("a.java", "class A { {String s = for<caret>mat(\"\",\"\");}}");
    final IntentionAction intention = myFixture.findSingleIntention(QuickFixBundle.message("static.import.method.text"));
    assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResult("""
                            import static java.lang.String.format;

                            class A { {String s = format("","");}}""", false);
  }
}
