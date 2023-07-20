// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.javadoc;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class JavadocCreateSnippetFileFromUsageFixTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_20;
  }

  public void testCreateSnippetClass() {
    myFixture.enableInspections(new JavaDocReferenceInspection());
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
