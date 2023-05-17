// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.miscGenerics.RawUseOfParameterizedTypeInspection;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RawTypeCanBeGenericFixTest extends LightJavaCodeInsightFixtureTestCase {
  private static final ProjectDescriptor JDK_8_WITH_LEVEL_6 = new ProjectDescriptor(LanguageLevel.JDK_1_6) {
    @Override
    public Sdk getSdk() {
      return IdeaTestUtil.getMockJdk18();
    }
  };

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInspection/makeTypeGeneric";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new RawUseOfParameterizedTypeInspection());
  }

  public void testField() {
    doTest(getMessage("TT", "Comparator<String>"));
  }

  public void testLocalVariable() {
    doTest(getMessage("list", "List<String>"));
  }

  public void testAvoidUnrelatedWarnings() {
    doTest(getMessage("iterator", "Iterator<String>"));
  }

  public void testAtEquals() {
    doTest(getMessage("list", "List<String>"));
  }

  public void testConflict() {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());
    try {
      doTest(getMessage("list", "List<T>"));
      fail("No conflict detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Cannot convert type of expression <b>&quot;&quot;</b> from <b>java.lang.String</b> to <b>T</b>",
                   e.getMessage());
    }
  }

  public void testAtTypeCast() { doTest("Change cast type to List<?>"); }
  public void testAtTypeCast2() { doTest("Change cast type to List<?>"); }
  public void testAtTypeCast3() { assertIntentionNotAvailable("Change cast type to List<?>"); }
  public void testAtTypeCast4() { doTest("Change cast type to X<?>"); }
  public void testAtTypeCast5() { assertIntentionNotAvailable("Change cast type to X<?>"); }
  public void testAtTypeCast6() { assertIntentionNotAvailable("Change cast type to Callable<><?>"); }

  public void testAtConstructor1() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_8, () -> assertIntentionNotAvailable("Insert '<>'")); }
  public void testAtConstructor2() { IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_8, () -> doTest("Insert '<>'")); }
  public void testAtConstructor3() { IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_8, () -> doTest("Insert '<>'")); }
  public void testAtConstructor4() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_8, () -> assertIntentionNotAvailable("Insert '<>'"));
  }

  public void testAtInitializer() {
    assertIntentionNotAvailable(getMessagePrefix());
  }

  public void testImplementedRaw() {
    assertIntentionNotAvailable(getMessagePrefix());
  }

  public void testBoundedTypeParameter() {
    doTest(getMessage("list", "List<? extends Integer>"));
  }

  private void doTest(String intentionName) {
    myFixture.configureByFiles(getTestName(false) + ".java");
    final IntentionAction singleIntention = myFixture.findSingleIntention(intentionName);
    myFixture.launchAction(singleIntention);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    myFixture.checkResultByFile(getTestName(false) + ".java", getTestName(false) + "_after.java", true);
  }

  private void assertIntentionNotAvailable(String intentionName) {
    myFixture.configureByFiles(getTestName(false) + ".java");
    final List<IntentionAction> intentionActions = myFixture.filterAvailableIntentions(intentionName);
    assertEmpty(intentionName + " is not expected", intentionActions);
  }

  private static String getMessage(String variable, String type) {
    return JavaBundle.message("raw.variable.type.can.be.generic.quickfix", variable, type);
  }

  private static String getMessagePrefix() {
    String message = JavaBundle.message("raw.variable.type.can.be.generic.quickfix", "@", "@");
    return message.substring(0, message.indexOf("@"));
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JDK_8_WITH_LEVEL_6;
  }
}
