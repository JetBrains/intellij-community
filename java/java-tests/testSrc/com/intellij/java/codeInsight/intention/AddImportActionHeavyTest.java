// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention;

import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

import java.io.IOException;

public class AddImportActionHeavyTest extends JavaCodeInsightFixtureTestCase {
  public void test_prefer_junit_in_tests() throws IOException {
    myFixture.addClass("package org.junit; public @interface Before {}");
    myFixture.addClass("package org.aspectj.lang.annotation; public @interface Before {}");
    PsiTestUtil.addSourceRoot(getModule(), myFixture.getTempDirFixture().findOrCreateDir("tests"), true);
    myFixture.configureFromExistingVirtualFile(
      myFixture.addFileToProject("tests/a.java", "@Befor<caret>e class MyTest {}").getVirtualFile());
    myFixture.launchAction(myFixture.findSingleIntention("Import class"));
    myFixture.checkResult("""
                            import org.junit.Before;

                            @Before
                            class MyTest {}""");
  }
}
