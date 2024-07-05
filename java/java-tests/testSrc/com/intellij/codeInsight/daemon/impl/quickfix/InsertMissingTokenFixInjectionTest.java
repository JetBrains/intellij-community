// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class InsertMissingTokenFixInjectionTest extends LightJavaCodeInsightFixtureTestCase {

  public void testInjection() {
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
    myFixture.checkPreviewAndLaunchAction(intention);
    myFixture.checkResult(text.replace("<caret>", ";"));
  }
  
  public void testInjection2() {
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
    myFixture.launchAction("Insert ';'");
    myFixture.checkResult(text.replace("<target>", ";"));
  }
  
  public void testInjectionFixAll() {
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
  
  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/insertSemicolon";
  }
}
