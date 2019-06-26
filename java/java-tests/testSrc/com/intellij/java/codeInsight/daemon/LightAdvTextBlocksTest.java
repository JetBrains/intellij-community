// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class LightAdvTextBlocksTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advTextBlock";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_13;
  }

  
  public void testSplitTextBlockOnWhitespace() { doTestSplitIntention(); }
  public void testSplitTextBlock() { doTestSplitIntention(); }
  
  private void doTestSplitIntention() {
    myFixture.configureByFile(getTestName(false) + ".java");
    List<IntentionAction> actions = myFixture.filterAvailableIntentions("Split text block");
    assertNotEmpty(actions);
    myFixture.launchAction(actions.get(0));
    myFixture.checkResultByFile(getTestName(false) + ".after.java");
  }

  private void performPaste() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE);
  }

  private void performCopy() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_COPY);
  }
}