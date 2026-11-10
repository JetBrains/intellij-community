// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.javaDoc.JavadocDeclarationInspection;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class JavadocSnippetHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new JavadocDeclarationInspection());
  }

  @Test
  public void testSnippetExternalMismatch() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("snippet-files/Test.java", """
      class Test {
          // @start region=reg
          // @replace region substring="one" replacement="two"
          void one() {}
          void anotherone() {} // @highlight substring="void"
          // @end
          // @end
      }
      """);
    myFixture.configureByText("Test.java", """
      /**
       * {<warning descr="External snippet differs from inline snippet">@<caret>snippet</warning> class="Test":
       * }
       */
      class X {}
      """);
    myFixture.checkHighlighting();
    IntentionAction deleteSnippetBody = myFixture.findSingleIntention("Remove snippet body");
    assertEquals("""
                   /**
                    * {@snippet class="Test"}
                    */
                   class X {}
                   """, myFixture.getIntentionPreviewText(deleteSnippetBody));
    IntentionAction action = myFixture.findSingleIntention("Synchronize inline snippet");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.checkPreviewAndLaunchAction(action);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      myFixture.checkResult("""
                              /**
                               * {@snippet class="Test":
                               * class Test {
                               *
                               *
                               *     void two() {}
                               *     void anothertwo() {}
                               *
                               *
                               * }}
                               */
                              class X {}
                              """);
    });
  }
  
  @Test
  public void testSnippetExternalMismatchMarkdown() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("snippet-files/Test.java", """
      class Test {
          // @start region=reg
          // @replace region substring="one" replacement="two"
          void one() {}
          void anotherone() {} // @highlight substring="void"
          // @end
          // @end
      }
      """);
    myFixture.configureByText("Test.java", """
      /// {<warning descr="External snippet differs from inline snippet">@<caret>snippet</warning> class="Test":
      /// }
      class X {}
      """);
    myFixture.checkHighlighting();
    IntentionAction deleteSnippetBody = myFixture.findSingleIntention("Remove snippet body");
    assertEquals("""
                   /// {@snippet class="Test"}
                   class X {}
                   """, myFixture.getIntentionPreviewText(deleteSnippetBody));
    IntentionAction action = myFixture.findSingleIntention("Synchronize inline snippet");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.checkPreviewAndLaunchAction(action);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      myFixture.checkResult("""
                              /// {@snippet class="Test":
                              /// class Test {
                              ///
                              ///
                              ///     void two() {}
                              ///     void anothertwo() {}
                              ///
                              ///
                              /// }}
                              class X {}
                              """);
    });
  }

}
