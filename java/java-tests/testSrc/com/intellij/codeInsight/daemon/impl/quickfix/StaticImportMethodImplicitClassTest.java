// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.java.JavaBundle;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.List;

public class StaticImportMethodImplicitClassTest extends LightJavaCodeInsightFixtureTestCase {
  public void testStaticImportMethodImplicitClass() {
    myFixture.addClass(
      """
        void main() {
            methodFromImplicitClass();
        }
        
        public static double methodFromImplicitClass() {
            return 1;
        }
        """);

    myFixture.configureByText("SomeClass.java", """
      public class SomeClass {
          public void foo() {
              methodFromImplicitClass<caret>();
          }
      }
      """);
    myFixture.doHighlighting();
    List<IntentionAction> actions = myFixture.filterAvailableIntentions(JavaBundle.message("qualify.static.call.fix.text"));
    assertEmpty(actions);
  }

  public void testStaticImportMethodTheSameImplicitClass() {
    myFixture.configureByText("Main.java", """
      void main() {
      }
      
      class A{
        public static double methodFromImplicitClass() {
          return 1;
        }
      }
      
      public class SomeClass {
        public void foo() {
          methodFromImplicitClass<caret>();
        }
      }""");
    myFixture.doHighlighting();
    myFixture.launchAction(JavaBundle.message("qualify.static.call.fix.text"));
    myFixture.checkResult(
      """
        void main() {
        }
        
        class A{
          public static double methodFromImplicitClass() {
            return 1;
          }
        }
        
        public class SomeClass {
          public void foo() {
            A.methodFromImplicitClass();
          }
        }""");
  }
}
