// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.psi.PsiClass;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class RenameFieldMultiTest extends LightJavaCodeInsightFixtureTestCase {
   @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/renameField/multi/";
  }

  public void testNonCodeUsages() {
    PsiClass aClass = myFixture.addClass("""
                                           package p;
                                           public class A {
                                             private String myField;
                                           }""");
    doTest(aClass, "properties");
  }

  private void doTest(PsiClass aClass, String ext) {
    String suffix = getTestName(false);
    myFixture.configureByFile("before" + suffix + "." + ext);
    new RenameProcessor(getProject(), aClass.findFieldByName("myField", false), "myField1", true, true).run();
    myFixture.checkResultByFile("after" + suffix + "." + ext);
  }
}
