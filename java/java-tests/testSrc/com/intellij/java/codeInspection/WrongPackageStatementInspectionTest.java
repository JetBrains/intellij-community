// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.wrongPackageStatement.WrongPackageStatementInspection;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public final class WrongPackageStatementInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    codeInsightFixture.get().enableInspections(new WrongPackageStatementInspection());
  }

  @Test
  public void testWrongPackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile sourceRoot = projectFixture.get().getSourceRoot();
    myFixture.configureByText("Test.java", """
        package <error descr="Package name 'com.foo.bar' does not correspond to the file path ''">com.f<caret>oo.bar</error>;
        
        class Test { }
        """);
    String path = VfsUtilCore.getRelativePath(myFixture.getFile().getVirtualFile(), sourceRoot);
    assertEquals("Test.java", path);
    myFixture.checkHighlighting();
    IntentionAction action = myFixture.findSingleIntention("Move to package 'com.foo.bar'");
    myFixture.checkIntentionPreviewHtml(action, """
      <p>Create directories:<br/>&bull; com<br/>&bull; com/foo<br/>&bull; com/foo/bar</p><br/><p>\
      <icon src="file"/>&nbsp;Test.java &rarr; <icon src="dir"/>&nbsp;%s$com$foo$bar</p>""".replace("$", File.separator).formatted(sourceRoot.getPresentableUrl()));
    myFixture.launchAction(action);
    path = VfsUtilCore.getRelativePath(myFixture.getFile().getVirtualFile(), sourceRoot);
    assertEquals("com/foo/bar/Test.java", path);
  }
}
