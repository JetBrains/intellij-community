// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static com.intellij.java.codeInsight.completion.NormalCompletionFixtureKt.normalCompletionFixture;

@TestApplication
@IndexingModeTemplate
public class NormalSwitchCompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<NormalCompletionFixture> completion = normalCompletionFixture(projectFixture);

  @Test
  public void testDefaultInRuleSwitch() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testLabelInRuleSwitch() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testSecondLabelInOldSwitch() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testSecondLabelInRuleSwitch() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testSwitchExpressionStart() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testYieldInSwitchExpression() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testInsideYieldInSwitchExpression() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testInsideRuleInSwitchExpression() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testBreakDeepInsideSwitchExpression() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.Full
  public void testCompletePatternVariableInSwitchExpr() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.Full
  public void testCompletePatternVariableInSwitchStmt() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testCompleteReturnInSwitch() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.Full
  public void testCompleteConstantInSwitchExpr() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  @NeedsIndex.Full
  public void testCompleteConstantInSwitchStmt() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  public void testCompleteNullInSwitchStmt() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testCompleteNullInSwitchExpr() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testCompletePatternVariableSwitchStmt() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testCompletePatternVariableSwitchExpr() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testCompleteSealedLabelSwitch() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testCompleteReturnInSwitchRule() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testCompleteReturnInSwitchRule2() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testCompleteThrowInSwitchRule() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.Full(reason = "Other options do not appear if there's no index. As a result, '\n' goes right into the editor")
  public void testCompleteIfInSwitchRule() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  public void testCompleteSwitchObjectSelectorPostfix() { EdtTestUtil.runInEdtAndWait(() -> doTestPostfixCompletion()); }

  @Test
  public void testCompleteSwitchPrimitiveSelectorPostfix() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel(),
                             () -> doTestPostfixCompletion());
    });
  }

  @Test
  public void testCompleteSwitchPrimitiveSelectorPostfixLowerLanguageLevel() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_11,
                             () -> doTestPostfixCompletion());
    });
  }

  @Test
  public void testCompleteSwitchSealedSelectorPostfix() { EdtTestUtil.runInEdtAndWait(() -> doTestPostfixCompletion()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCompleteReferencesInExpressionSwitch() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest());
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCompleteFinalInsideSwitch() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  public void testCompleteYieldInsideSwitch() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }
  @Test
  public void testWhenInSwitchRule() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testQualifierEnumConstantInSwitch1() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }
  @Test
  public void testQualifierEnumConstantInSwitch2() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }
  @Test
  public void testQualifierEnumConstantInSwitch3() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }
  @Test
  public void testQualifierEnumConstantInSwitch4() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testQualifierEnumConstantInSwitchWithObject() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testQualifierEnumConstantInSwitchWithObjectInJava20() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_20, () -> completion.get().doTest()));
  }
  @Test
  public void testQualifierEnumConstantInSwitchInJava20() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_20, () -> completion.get().doTest()));
  }
  @Test
  public void testClassPatternInSwitch1() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }
  @Test
  public void testClassPatternInSwitch2() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }
  @Test
  public void testClassEnumInSwitch() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }
  @Test
  public void testDefaultAfterNull() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }
  @Test
  public void testDefaultAfterNullInJava20() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_20, () -> completion.get().doTest()));
  }
  @Test
  public void testDefaultAfterNotNull() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }
  @Test
  public void testDefaultAfterDefault() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testClassEnumInSwitchInJava20() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_20, () -> completion.get().doTest()));
  }
  private void doTestPostfixCompletion() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable());
    completion.get().configure();
    myFixture.type('\t');
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    completion.get().checkResult();
  }
}
