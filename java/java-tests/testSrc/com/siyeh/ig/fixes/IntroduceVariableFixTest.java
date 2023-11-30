// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.style.ChainedMethodCallInspection;

public class IntroduceVariableFixTest extends LightJavaCodeInsightFixtureTestCase {

  public void testIntentionPreview() {
    myFixture.enableInspections(new ChainedMethodCallInspection());
    myFixture.configureByText("Test.java",
                              """
                                import java.io.File;

                                class X {
                                    int foo(File f) {
                                        return f.getName().lengt<caret>h();
                                    }
                                }
                                """);
    IntentionAction action = myFixture.findSingleIntention("Introduce variable");
    myFixture.checkPreviewAndLaunchAction(action);
    myFixture.checkResult("""
                            import java.io.File;

                            class X {
                                int foo(File f) {
                                    String name = f.getName();
                                    return name.length();
                                }
                            }
                            """);
  }
}
