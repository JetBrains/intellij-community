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
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.ProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class RawTypeCanBeGenericFixTest {
  private static final ProjectDescriptor JDK_8_WITH_LEVEL_6 = new ProjectDescriptor(LanguageLevel.JDK_1_6) {
    @Override
    public Sdk getSdk() {
      return IdeaTestUtil.getMockJdk18();
    }
  };
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(JDK_8_WITH_LEVEL_6);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInspection/makeTypeGeneric");
    myFixture.enableInspections(new RawUseOfParameterizedTypeInspection());
  }

  @Test
  public void testField() {
    doTest(getMessage("TT", "Comparator<String>"));
  }

  @Test
  public void testLocalVariable() {
    doTest(getMessage("list", "List<String>"));
  }

  @Test
  public void testAvoidUnrelatedWarnings() {
    doTest(getMessage("iterator", "Iterator<String>"));
  }

  @Test
  public void testAtEquals() {
    doTest(getMessage("list", "List<String>"));
  }

  @Test
  public void testConflict() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    DefaultLogger.disableStderrDumping(myFixture.getTestRootDisposable());
    try {
      doTest(getMessage("list", "List<T>"));
      fail("No conflict detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Cannot convert type of expression <b>&quot;&quot;</b> from <b>java.lang.String</b> to <b>T</b>",
                   e.getMessage());
    }
  }

  @Test
  public void testAtTypeCast() { doTest("Change cast type to List<?>"); }
  @Test
  public void testAtTypeCast2() { doTest("Change cast type to List<?>"); }
  @Test
  public void testAtTypeCast3() { assertIntentionNotAvailable("Change cast type to List<?>"); }
  @Test
  public void testAtTypeCast4() { doTest("Change cast type to X<?>"); }
  @Test
  public void testAtTypeCast5() { assertIntentionNotAvailable("Change cast type to X<?>"); }
  @Test
  public void testAtTypeCast6() { assertIntentionNotAvailable("Change cast type to Callable<><?>"); }

  @Test
  public void testAtConstructor1() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_8, () -> assertIntentionNotAvailable("Insert '<>'"));
  }
  @Test
  public void testAtConstructor2() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_8, () -> doTest("Insert '<>'"));
  }
  @Test
  public void testAtConstructor3() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_8, () -> doTest("Insert '<>'"));
  }
  @Test
  public void testAtConstructor4() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_8, () -> assertIntentionNotAvailable("Insert '<>'"));
  }

  @Test
  public void testAtInitializer() {
    assertIntentionNotAvailable(getMessagePrefix());
  }

  @Test
  public void testImplementedRaw() {
    assertIntentionNotAvailable(getMessagePrefix());
  }

  @Test
  public void testBoundedTypeParameter() {
    doTest(getMessage("list", "List<? extends Integer>"));
  }

  private void doTest(String intentionName) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles(myFixture.getTestName(false) + ".java");
    final IntentionAction singleIntention = myFixture.findSingleIntention(intentionName);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction(singleIntention);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      myFixture.checkResultByFile(myFixture.getTestName(false) + ".java", myFixture.getTestName(false) + "_after.java", true);
    });
  }

  private void assertIntentionNotAvailable(String intentionName) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles(myFixture.getTestName(false) + ".java");
    final List<IntentionAction> intentionActions = myFixture.filterAvailableIntentions(intentionName);
    assertThat(intentionActions).as(intentionName + " is not expected").isEmpty();
  }

  private static String getMessage(String variable, String type) {
    return JavaBundle.message("raw.variable.type.can.be.generic.quickfix", variable, type);
  }

  private static String getMessagePrefix() {
    String message = JavaBundle.message("raw.variable.type.can.be.generic.quickfix", "@", "@");
    return message.substring(0, message.indexOf("@"));
  }
}
