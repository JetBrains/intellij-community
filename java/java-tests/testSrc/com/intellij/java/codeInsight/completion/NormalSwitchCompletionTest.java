// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.NeedsIndex;
import org.jetbrains.annotations.NotNull;

public class NormalSwitchCompletionTest extends NormalCompletionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  public void testDefaultInRuleSwitch() { doTest(); }

  public void testLabelInRuleSwitch() { doTest(); }

  public void testSecondLabelInOldSwitch() { doTest(); }

  public void testSecondLabelInRuleSwitch() { doTest(); }

  public void testSwitchExpressionStart() { doTest(); }

  public void testYieldInSwitchExpression() { doTest(); }

  public void testInsideYieldInSwitchExpression() { doTest(); }

  public void testInsideRuleInSwitchExpression() { doTest(); }

  public void testBreakDeepInsideSwitchExpression() { doTest(); }

  @NeedsIndex.Full
  public void testCompletePatternVariableInSwitchExpr() { doTest(); }

  @NeedsIndex.Full
  public void testCompletePatternVariableInSwitchStmt() { doTest(); }

  public void testCompleteReturnInSwitch() { doTest(); }

  @NeedsIndex.Full
  public void testCompleteConstantInSwitchExpr() { doTest("\n"); }

  @NeedsIndex.Full
  public void testCompleteConstantInSwitchStmt() { doTest("\n"); }

  public void testCompleteNullInSwitchStmt() { doTest(); }

  public void testCompleteNullInSwitchExpr() { doTest(); }

  public void testCompletePatternVariableSwitchStmt() { doTest(); }

  public void testCompletePatternVariableSwitchExpr() { doTest(); }

  public void testCompleteSealedLabelSwitch() { doTest(); }

  public void testCompleteReturnInSwitchRule() { doTest(); }

  public void testCompleteReturnInSwitchRule2() { doTest(); }

  public void testCompleteThrowInSwitchRule() { doTest(); }

  @NeedsIndex.Full(reason = "Other options do not appear if there's no index. As a result, '\n' goes right into the editor")
  public void testCompleteIfInSwitchRule() { doTest("\n"); }

  public void testCompleteSwitchObjectSelectorPostfix() { doTestPostfixCompletion(); }

  public void testCompleteSwitchPrimitiveSelectorPostfix() {
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel(),
                           () -> doTestPostfixCompletion());
  }

  public void testCompleteSwitchPrimitiveSelectorPostfixLowerLanguageLevel() {
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_11,
                           () -> doTestPostfixCompletion());
  }

  public void testCompleteSwitchSealedSelectorPostfix() { doTestPostfixCompletion(); }

  @NeedsIndex.ForStandardLibrary
  public void testCompleteReferencesInExpressionSwitch() {
    doTest();
  }

  @NeedsIndex.ForStandardLibrary
  public void testCompleteFinalInsideSwitch() { doTest("\n"); }

  public void testCompleteYieldInsideSwitch() { doTest(); }
  public void testWhenInSwitchRule() { doTest(); }

  public void testQualifierEnumConstantInSwitch1() { doTest(); }
  public void testQualifierEnumConstantInSwitch2() { doTest(); }
  public void testQualifierEnumConstantInSwitch3() { doTest(); }
  public void testQualifierEnumConstantInSwitch4() { doTest(); }
  @NeedsIndex.ForStandardLibrary
  public void testQualifierEnumConstantInSwitchWithObject() { doTest(); }
  @NeedsIndex.ForStandardLibrary
  public void testQualifierEnumConstantInSwitchWithObjectInJava20() {
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_20, () -> doTest());
  }
  public void testQualifierEnumConstantInSwitchInJava20() {
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_20, () -> doTest());
  }
  public void testClassPatternInSwitch1() { doTest(); }
  public void testClassPatternInSwitch2() { doTest(); }
  public void testClassEnumInSwitch() { doTest(); }
  public void testDefaultAfterNull() { doTest(); }
  public void testDefaultAfterNullInJava20() {
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_20, () -> doTest());
  }
  public void testDefaultAfterNotNull() { doTest(); }
  public void testDefaultAfterDefault() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testNoExtraArrowMultiCaret() { doTest(); }
  public void testClassEnumInSwitchInJava20() {
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_20, () -> doTest());
  }
  private void doTestPostfixCompletion() {
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable());
    configure();
    myFixture.type('\t');
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    checkResult();
  }
}
