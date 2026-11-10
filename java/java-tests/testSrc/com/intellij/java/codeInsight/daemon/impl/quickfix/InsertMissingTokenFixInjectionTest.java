// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class InsertMissingTokenFixInjectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/codeInsight/daemonCodeAnalyzer/quickFix/insertSemicolon");

  @Test
  public void testInjection() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.setCaresAboutInjection(false);
    String text = """
      import org.intellij.lang.annotations.Language;
      
      class Test {
        void foo() {
          @Language("JAVA")
          String javaFile = ""\"
            class X {
              void foo() {
                System.out.println(1)<caret>
              }
            }
            ""\";
        }
      }""";
    myFixture.configureByText("Test.java", text);
    IntentionAction intention = myFixture.findSingleIntention("Insert ';'");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.checkPreviewAndLaunchAction(intention);
      myFixture.checkResult(text.replace("<caret>", ";"));
    });
  }
  
  @Test
  public void testInjection2() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.setCaresAboutInjection(false);
    String text = """
      import org.intellij.lang.annotations.Language;
      
      class Test {
        void foo() {
          @Language("JAVA")
          String javaFile = ""\"
            class X {
              void foo() {
                <caret>System.out.println(1)<target>
              }
            }
            ""\";
        }
      }""";
    myFixture.configureByText("Test.java", text.replace("<target>", ""));
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction("Insert ';'");
      myFixture.checkResult(text.replace("<target>", ";"));
    });
  }
  
  @Test
  public void testInjectionFixAll() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.setCaresAboutInjection(false);
    String text = """
      import org.intellij.lang.annotations.Language;
      
      class Test {
          void foo() {
              System.out.println(1)<target>
              System.out.println(1)<target>
              @Language("JAVA")
              String javaFile = ""\"
                      class X {
                        void foo() {
                          System.out.println(1)<target>
                          <caret>System.out.println(1)<target>
                          System.out.println(1)<target>
                        }
                      }
                      ""\";
              @Language("JAVA")
              String javaFile2 = ""\"
                      class X {
                        void foo() {
                          System.out.println(1)<target>
                          System.out.println(1)<target>
                          System.out.println(1)<target>
                        }
                      }
                      ""\";
          }
      }""";
    myFixture.configureByText("Test.java", text.replace("<target>", ""));
    IntentionAction intention = myFixture.findSingleIntention("Apply all 'Insert ';'' fixes in file");
    myFixture.launchAction(intention);
    myFixture.checkResult(text.replace("<target>", ";"));
  }
}
