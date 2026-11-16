// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.javadoc;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_20;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JavaDocReferenceInspectionFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(JAVA_20);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    codeInsightFixture.get().enableInspections(new JavaDocReferenceInspection());
  }

  @Test
  public void testQualifyReference() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Test.java", """
      /**
       * Hi {@link <error descr="Cannot resolve symbol 'ArrayList'"><caret>ArrayList</error>}
       */
      class Test {}
      """);
    myFixture.checkHighlighting();
    IntentionAction action = myFixture.findSingleIntention("Add qualifier");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction(action);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    });
    myFixture.checkResult("""
                            import java.util.ArrayList;
                                                        
                            /**
                             * Hi {@link ArrayList}
                             */
                            class Test {}
                            """);
  }

  @Test
  public void testRenameParameter() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    //noinspection JavadocDeclaration
    myFixture.configureByText("Test.java", """
      class Test {
        /**
         * @param <error descr="Cannot resolve symbol 'hello'"><caret>hello</error> hi
         */
        void method(String p1, String p2) {}
      }
      """);
    myFixture.checkHighlighting();
    IntentionAction action = myFixture.findSingleIntention("Change to …");
    myFixture.launchAction(action);
    myFixture.checkResult("""
                            class Test {
                              /**
                               * @param p1 hi
                               */
                              void method(String p1, String p2) {}
                            }
                            """);
  }

  @Test
  public void testCreateSnippetClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Test.java", """
      /**
       * {@snippet class=<error descr="Snippet file 'snippet-files/a/b/Hello.java' is not found">"a.<caret>b.Hello"</error>}
       */
      class Test{}
      """);
    myFixture.checkHighlighting();
    IntentionAction action = myFixture.findSingleIntention("Create file Hello.java");
    myFixture.checkIntentionPreviewHtml(action, """
        Create file <icon src="file"/>&nbsp;snippet-files#a#b#Hello.java<br/>\
        including intermediate directories<br/>\
        inside directory <icon src="dir"/>&nbsp;""".replace('#', File.separatorChar) + projectFixture.get().getSourceRoot().getPresentableUrl());
    myFixture.launchAction(action);
    VirtualFile path = myFixture.getFile().getVirtualFile().findFileByRelativePath("../snippet-files/a/b/Hello.java");
    assertTrue(path.exists());
  }
}
