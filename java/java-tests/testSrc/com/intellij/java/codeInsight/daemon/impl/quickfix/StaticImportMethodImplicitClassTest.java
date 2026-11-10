// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.java.JavaBundle;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;

@TestApplication
public class StaticImportMethodImplicitClassTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testStaticImportMethodImplicitClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
    assertThat(actions).isNullOrEmpty();
  }

  @Test
  public void testStaticImportMethodTheSameImplicitClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }
}
