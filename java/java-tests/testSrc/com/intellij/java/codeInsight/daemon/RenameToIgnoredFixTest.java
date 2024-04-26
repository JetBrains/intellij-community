// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public final class RenameToIgnoredFixTest extends LightJavaCodeInsightFixtureTestCase {
  public void testRenameToIgnoredSwitchRule() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21, () -> {
      myFixture.configureByText("Test.java", """
      class Scratch {
        void test(Object obj) {
          switch (obj) {
          case String ignored -> System.out.println("String");
          case Integer <caret>integer -> System.out.println("Integer");
          default -> System.out.println("Other");
          }
        }
      }""");
      myFixture.enableInspections(new UnusedDeclarationInspection());
      IntentionAction intention = myFixture.findSingleIntention("Rename 'integer' to 'ignored'");
      myFixture.launchAction(intention);
      myFixture.checkResult("""
                              class Scratch {
                                void test(Object obj) {
                                  switch (obj) {
                                  case String ignored -> System.out.println("String");
                                  case Integer ignored -> System.out.println("Integer");
                                  default -> System.out.println("Other");
                                  }
                                }
                              }""");
    });
  }

  public void testRenameToIgnoredSwitch() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21, () -> {
      myFixture.configureByText("Test.java", """
       class Scratch {
         void test(Object obj) {
           switch (obj) {
             case String ignored: System.out.println("String");  break;
             case Integer <caret>a: System.out.println("Integer"); break;
             default: System.out.println("Other");  break;
           }
         }
       }""");
      myFixture.enableInspections(new UnusedDeclarationInspection());
      IntentionAction intention = myFixture.findSingleIntention("Rename 'a' to 'ignored'");
      myFixture.launchAction(intention);
      myFixture.checkResult("""
                              class Scratch {
                                void test(Object obj) {
                                  switch (obj) {
                                    case String ignored: System.out.println("String");  break;
                                    case Integer ignored: System.out.println("Integer"); break;
                                    default: System.out.println("Other");  break;
                                  }
                                }
                              }""");
    });
  }

  public void testRenameToIgnoredSwitch2() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21, () -> {
      myFixture.configureByText("Test.java", """
       class Scratch {
         void test(Object obj) {
           int a = 1;
           int ignored = 1;
           int b = 1;
       
           switch (obj) {
             case String ignored1: System.out.println("String"); break;
             case Integer a<caret> :System.out.println("Integer"); break;
             default: System.out.println("Other");break;
           }
         }
       }""");
      myFixture.enableInspections(new UnusedDeclarationInspection());
      IntentionAction intention = myFixture.findSingleIntention("Rename 'a' to 'ignored1'");
      myFixture.launchAction(intention);
      myFixture.checkResult("""
                              class Scratch {
                                void test(Object obj) {
                                  int a = 1;
                                  int ignored = 1;
                                  int b = 1;

                                  switch (obj) {
                                    case String ignored1: System.out.println("String"); break;
                                    case Integer ignored1:System.out.println("Integer"); break;
                                    default: System.out.println("Other");break;
                                  }
                                }
                              }""");
    });
  }
}
