// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInspection.actions.CleanupInspectionIntention;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.sillyAssignment.SillyAssignmentInspection;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.classlayout.UtilityClassWithoutPrivateConstructorInspection;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@TestApplication
public class CleanupInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testInjected() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new SillyAssignmentInspection());
    myFixture.configureByText("J.java", """
       import org.intellij.lang.annotations.Language;
      
       class X {
         @Language("JAVA")
         String s = ""\"
           class EmptyArray {
             public void run() {
               int i = 0;
               i = <caret>i;
             }
           }
           ""\";
           @Language("JAVA")
           String t = ""\"
             class EmptyArray {
               public void run() {
                 int i = 0;
                 i = i;
               }
             }
           ""\";
       }
      """);
    myFixture.launchAction(myFixture.findSingleIntention("Fix all 'Variable is assigned to itself' problems in file"));
    myFixture.checkResult("""
                             import org.intellij.lang.annotations.Language;
                            
                             class X {
                               @Language("JAVA")
                               String s = ""\"
                                 class EmptyArray {
                                   public void run() {
                                     int i = 0;
                                   }
                                 }
                                 ""\";
                                 @Language("JAVA")
                                 String t = ""\"
                                   class EmptyArray {
                                     public void run() {
                                       int i = 0;
                                     }
                                   }
                                 ""\";
                             }
                            """);
  }

  @Test
  public void testPartiallyApplied() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
    EdtTestUtil.runInEdtAndWait(() -> {
      IntentionAction fixAll = IntentionManager.getInstance().createFixAllIntention(new LocalInspectionToolWrapper(inspection), intention);
      assertInstanceOf(CleanupInspectionIntention.class, fixAll);
      String message = ((CleanupInspectionIntention)fixAll).findAndFix(myFixture.getProject(), myFixture.getFile());
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
    });
  }
}
