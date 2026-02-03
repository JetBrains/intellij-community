// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.style;

import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.PsiTestUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.style.LambdaBodyCanBeCodeBlockInspection;

public class LambdaBodyCanBeCodeBlockFixTest extends IGQuickFixesTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new LambdaBodyCanBeCodeBlockInspection());
    myDefaultHint = InspectionGadgetsBundle.message("lambda.body.can.be.code.block.quickfix");
    myRelativePath = "style/expr2block";
  }

  public void testSimple() {
    doTest();
  }

  public void testVoidCompatibleInExpr() {
    doTest();
  }

  public void testLambdaWithInvalidCodeInside() {
    myFixture.configureByFile(myRelativePath + "/" + getTestName(false) + ".java");
    myFixture.launchAction(myFixture.findSingleIntention(myDefaultHint));
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    PsiTestUtil.checkFileStructure(myFixture.getFile());
  }

}
