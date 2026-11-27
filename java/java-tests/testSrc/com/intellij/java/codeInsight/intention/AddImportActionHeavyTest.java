// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention;

import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;

@TestApplication
public class AddImportActionHeavyTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture = javaModuleCodeInsightFixture(PathManager.getHomePath());

  @Test
  public void test_prefer_junit_in_tests() throws IOException {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package org.junit; public @interface Before {}");
    myFixture.addClass("package org.aspectj.lang.annotation; public @interface Before {}");
    PsiTestUtil.addSourceRoot(myFixture.getModule(), myFixture.getTempDirFixture().findOrCreateDir("tests"), true);
    myFixture.configureFromExistingVirtualFile(
      myFixture.addFileToProject("tests/a.java", "@Befor<caret>e class MyTest {}").getVirtualFile());
    myFixture.launchAction(myFixture.findSingleIntention("Import class"));
    myFixture.checkResult("""
                            import org.junit.Before;

                            @Before
                            class MyTest {}""");
  }
}
