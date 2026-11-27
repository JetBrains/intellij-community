// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.java18StreamApi.StaticPseudoFunctionalStyleMethodInspection;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtilRt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Dmitry Batkovich
 */
@TestApplication
public class StaticPseudoFunctionalStyleMethodTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture =
    javaModuleCodeInsightFixture(JavaTestUtil.getJavaTestDataPath() + "/inspection/lambdaLibsStatic", true, moduleBuilder -> {
      moduleBuilder.setLanguageLevel(LanguageLevel.JDK_1_8);
      moduleBuilder.addLibrary("guava", ArrayUtilRt.toStringArray(IntelliJProjectConfiguration.getModuleLibrary("intellij.libraries.guava", "Guava").getClassesPaths()));
      moduleBuilder.addJdkVersion(LanguageLevel.JDK_1_8);
    });

  private String testName;

  @BeforeEach
  void setUpTestName(TestInfo testInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), true);
  }

  @Test
  public void testSimpleTransform() {
    doTest();
  }

  @Test
  public void testSimpleFilter() {
    doTest();
  }

  @Test
  public void testSimpleFind() {
    doTest();
  }

  @Test
  public void testSimpleAll() {
    doTest();
  }

  @Test
  public void testSimpleAny() {
    doTest();
  }

  @Test
  public void testLambdaIsntAnonymous() {
    doTest();
  }

  @Test
  public void testLambdaIsntAnonymous2() {
    doTest();
  }

  @Test
  public void testLambdaIsntAnonymous3() {
    doTest();
  }

  @Test
  public void testFindWithDefaultValue() {
    doTest();
  }

  @Test
  public void testFilterWithInstanceOf() {
    doTest();
  }

  @Test
  public void testDiamondResolve() {
    doTest();
  }

  @Test
  public void testTransformLambda() {
    doTest();
  }

  @Test
  public void testTransformMethodCalling() {
    doTest();
  }

  @Test
  public void testTransformMethodRef() {
    doTest();
  }

  @Test
  public void testLambdaIsVariable() {
    doTest();
  }

  @Test
  public void testListsTransform() {
    doTest();
  }

  public void _testReplaceWithMethodReference() {
    doTest();
  }

  private void doTest() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(testName + "/test.java");
    myFixture.enableInspections(new StaticPseudoFunctionalStyleMethodInspection());
    IntentionAction action = myFixture.getAvailableIntention("Replace with Java Stream API pipeline");
    assertNotNull(action, "Quick fix isn't found");
    EdtTestUtil.runInEdtAndWait(() -> myFixture.checkPreviewAndLaunchAction(action));
    myFixture.checkResultByFile(testName + "/test_after.java");
  }
}
