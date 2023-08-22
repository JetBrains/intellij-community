// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInspection.actions.CleanupInspectionIntention;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.classlayout.UtilityClassWithoutPrivateConstructorInspection;

public class CleanupInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  public void testPartiallyApplied() {
    UtilityClassWithoutPrivateConstructorInspection inspection = new UtilityClassWithoutPrivateConstructorInspection();
    myFixture.enableInspections(inspection);
    myFixture.configureByText("Utils.java", """
      final class Use {
      	public static void foo() {}
      }
      final class Use1 {
      	public static void foo() {}
      }
      final class U<caret>se2 {
      	public static void foo() {
      		new Use1();
      		new Use();
      	}
      }""");
    IntentionAction intention = myFixture.findSingleIntention("Generate empty 'private' constructor");
    IntentionAction fixAll = IntentionManager.getInstance().createFixAllIntention(new LocalInspectionToolWrapper(inspection), intention);
    assertInstanceOf(fixAll, CleanupInspectionIntention.class);
    String message = ((CleanupInspectionIntention)fixAll).findAndFix(getProject(), getFile());
    assertEquals("""
         Some quick-fixes haven't completed successfully:
         2 of 3: Utility class has instantiations, private constructor will not be created
         1 of 3: Action completed successfully""", message);
    myFixture.checkResult("""
                            final class Use {
                            	public static void foo() {}
                            }
                            final class Use1 {
                            	public static void foo() {}
                            }
                            final class Use2 {
                                private Use2() {
                                }
                                                        
                                public static void foo() {
                            		new Use1();
                            		new Use();
                            	}
                            }""");
  }
}
