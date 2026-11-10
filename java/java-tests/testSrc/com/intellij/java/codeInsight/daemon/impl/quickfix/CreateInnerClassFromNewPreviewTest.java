// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
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
public class CreateInnerClassFromNewPreviewTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testPreviewWhenTargetClassIsInAnotherFile() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("public class B { }");
    myFixture.configureByText("Test.java", """
      public class Test {
        void f() {
          new B.<caret>Builder();
        }
      }""");
    IntentionAction action = myFixture.findSingleIntention("Create inner class 'Builder'");
    String previewText = myFixture.getIntentionPreviewText(action);
    assertNotNull(previewText);
    assertEquals("""
                   public class B { 
                       public static class Builder {
                       }
                   }""", previewText);
  }

  @Test
  public void testPreviewWhenTargetClassIsInTheSameFile() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Test.java", """
      public class Test { 
        void f() {
          new B.<caret>Builder();
        }
      }
      class B { }""");
    IntentionAction action = myFixture.findSingleIntention("Create inner class 'Builder'");
    String previewText = myFixture.getIntentionPreviewText(action);
    assertEquals("""
                   public class Test {
                     void f() {
                       new B.Builder();
                     }
                   }
                   class B {
                       public static class Builder {
                       }
                   }""", previewText);
  }
}
