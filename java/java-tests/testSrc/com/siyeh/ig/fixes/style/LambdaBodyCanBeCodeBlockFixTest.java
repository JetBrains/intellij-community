// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.style;

import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.style.LambdaBodyCanBeCodeBlockInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class LambdaBodyCanBeCodeBlockFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new LambdaBodyCanBeCodeBlockInspection()), "style/expr2block",
                        InspectionGadgetsBundle.message("lambda.body.can.be.code.block.quickfix"));

  @Test
  public void testSimple() {
    ig.get().doTest();
  }

  @Test
  public void testVoidCompatibleInExpr() {
    ig.get().doTest();
  }

  @Test
  public void testLambdaWithInvalidCodeInside() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    myFixture.configureByFile(ig.get().getRelativePath() + "/" + ig.get().getTestName(false) + ".java");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction(myFixture.findSingleIntention(ig.get().getDefaultHint()));
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      PsiTestUtil.checkFileStructure(myFixture.getFile());
    });
  }

}
