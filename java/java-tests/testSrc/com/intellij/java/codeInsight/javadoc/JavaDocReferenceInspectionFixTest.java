// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.javadoc;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class JavaDocReferenceInspectionFixTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_20;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new JavaDocReferenceInspection());
  }
  
  public void testQualifyReference() {
    myFixture.configureByText("Test.java", """
      /**
       * Hi {@link <error descr="Cannot resolve symbol 'ArrayList'"><caret>ArrayList</error>}
       */
      class Test {}
      """);
    myFixture.checkHighlighting();
    IntentionAction action = myFixture.findSingleIntention("Add qualifier");
    myFixture.launchAction(action);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    myFixture.checkResult("""
                            import java.util.ArrayList;
                                                        
                            /**
                             * Hi {@link ArrayList}
                             */
                            class Test {}
                            """);
  }

  public void testRenameParameter() {
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

  public void testCreateSnippetClass() {
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
        inside directory <icon src="dir"/>&nbsp;#src""".replace('#', File.separatorChar));
    myFixture.launchAction(action);
    VirtualFile path = myFixture.getFile().getVirtualFile().findFileByRelativePath("../snippet-files/a/b/Hello.java");
    assertTrue(path.exists());
  }
}
