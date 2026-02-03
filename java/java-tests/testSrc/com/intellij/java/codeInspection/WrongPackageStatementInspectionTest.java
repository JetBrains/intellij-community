// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.wrongPackageStatement.WrongPackageStatementInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.io.File;

public final class WrongPackageStatementInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new WrongPackageStatementInspection());
  }

  public void testWrongPackage() {
    myFixture.configureByText("Test.java", """
        package <error descr="Package name 'com.foo.bar' does not correspond to the file path ''">com.f<caret>oo.bar</error>;
        
        class Test { }
        """);
    String url = myFixture.getFile().getVirtualFile().getUrl();
    assertEquals("temp:///src/Test.java", url);
    myFixture.checkHighlighting();
    IntentionAction action = myFixture.findSingleIntention("Move to package 'com.foo.bar'");
    myFixture.checkIntentionPreviewHtml(action, """
      <p>Create directories:<br/>&bull; com<br/>&bull; com/foo<br/>&bull; com/foo/bar</p><br/><p>\
      <icon src="file"/>&nbsp;Test.java &rarr; <icon src="dir"/>&nbsp;$src$com$foo$bar</p>""".replace("$", File.separator));
    myFixture.launchAction(action);
    url = myFixture.getFile().getVirtualFile().getUrl();
    assertEquals("temp:///src/com/foo/bar/Test.java", url);
  }
}
